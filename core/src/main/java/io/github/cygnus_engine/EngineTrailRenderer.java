package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * Soft additive engine plumes driven by {@link SpaceShip} engine mount positions.
 */
public class EngineTrailRenderer implements Disposable {

    private static final int POINTS_PER_ENGINE = 10;
    private static final int MAX_ENGINES_PER_SHIP = 4;
    private static final int MAX_SHIPS = 32;
    /** x, y, alpha per vertex; two triangles (six verts) per trail segment. */
    private static final int MAX_VERTICES = MAX_SHIPS * MAX_ENGINES_PER_SHIP * (POINTS_PER_ENGINE - 1) * 6;
    private static final int FLOATS_PER_VERTEX = 3;

    private static final float POINT_INTERVAL = 0.01f;
    private static final float POINT_LIFETIME = 3f;
    private static final float BASE_WIDTH = 2.4f;
    private static final float TAIL_WIDTH = 0.3f;
    /** Minimum streak length in world units so low-speed plumes remain visible. */
    private static final float MIN_SEGMENT_LENGTH = 14f;

    private final ObjectMap<SpaceShip, ShipTrails> trailsByShip = new ObjectMap<>();
    private final Vector2 scratch = new Vector2();
    private final Vector2 scratch2 = new Vector2();
    private final float[] vertexData = new float[MAX_VERTICES * FLOATS_PER_VERTEX];

    private ShaderProgram shader;
    private Mesh mesh;
    private int vertexCount;

    public EngineTrailRenderer() {
        ShaderProgram.pedantic = false;
        shader = new ShaderProgram(
            Gdx.files.internal("shaders/engine_trail.vert"),
            Gdx.files.internal("shaders/engine_trail.frag")
        );
        if (!shader.isCompiled()) {
            throw new IllegalStateException("Engine trail shader failed: " + shader.getLog());
        }

        mesh = new Mesh(
            true,
            MAX_VERTICES,
            0,
            new VertexAttribute(VertexAttributes.Usage.Position, 2, "a_position"),
            new VertexAttribute(VertexAttributes.Usage.Generic, 1, "a_alpha")
        );
    }

    public void update(float deltaTime, Array<SpaceShip> ships) {
        for (SpaceShip ship : ships) {
            if (!ship.isVisible() || ship.getEngineCount() == 0) {
                trailsByShip.remove(ship);
                continue;
            }

            ShipTrails shipTrails = trailsByShip.get(ship);
            if (shipTrails == null) {
                shipTrails = new ShipTrails(ship.getEngineCount());
                trailsByShip.put(ship, shipTrails);
            } else if (shipTrails.engineTrails.length != ship.getEngineCount()) {
                shipTrails.resize(ship.getEngineCount());
            }

            boolean active = ship.isEngineTrailActive();
            float intensity = ship.getEngineTrailIntensity();
            shipTrails.update(deltaTime, ship, active, intensity, scratch, scratch2);
        }
    }

    public void render(OrthographicCamera camera) {
        vertexCount = 0;
        float globalIntensity = 1f;

        for (ObjectMap.Entry<SpaceShip, ShipTrails> entry : trailsByShip.entries()) {
            appendTrailGeometry(entry.value);
        }

        if (vertexCount <= 0) {
            return;
        }

        mesh.setVertices(vertexData, 0, vertexCount * FLOATS_PER_VERTEX);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);

        shader.bind();
        shader.setUniformMatrix("u_projTrans", camera.combined);
        shader.setUniformf("u_color", 1f, 0.68f, 0.38f);
        shader.setUniformf("u_intensity", globalIntensity);

        mesh.render(shader, GL20.GL_TRIANGLES);

        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void appendTrailGeometry(ShipTrails shipTrails) {
        for (EngineTrail trail : shipTrails.engineTrails) {
            appendEngineTrail(trail);
        }
    }

    private void appendEngineTrail(EngineTrail trail) {
        if (trail.count < 1) {
            return;
        }

        for (int segment = 0; segment < trail.count; segment++) {
            int idx0 = (trail.head - 1 - segment + POINTS_PER_ENGINE) % POINTS_PER_ENGINE;
            TrailPoint p0 = trail.points[idx0];
            if (p0.age > POINT_LIFETIME) {
                continue;
            }

            float life0 = 1f - (p0.age / POINT_LIFETIME);
            float alpha0 = life0 * p0.intensity;
            float alpha1 = alpha0 * 0.35f;
            float width0 = MathUtils.lerp(TAIL_WIDTH, BASE_WIDTH, life0);
            float width1 = MathUtils.lerp(TAIL_WIDTH, BASE_WIDTH, life0 * 0.45f);

            float ex = p0.exhaustX;
            float ey = p0.exhaustY;
            if (ex * ex + ey * ey < 0.0001f) {
                continue;
            }

            float segLen = Math.max(p0.segmentLength, MIN_SEGMENT_LENGTH);
            float px = -ey;
            float py = ex;

            float nozzleX = p0.x;
            float nozzleY = p0.y;
            float tailX = nozzleX + ex * segLen;
            float tailY = nozzleY + ey * segLen;

            appendQuad(
                nozzleX + px * width0, nozzleY + py * width0, alpha0,
                nozzleX - px * width0, nozzleY - py * width0, alpha0,
                tailX - px * width1, tailY - py * width1, alpha1,
                tailX + px * width1, tailY + py * width1, alpha1
            );
        }
    }

    private void appendQuad(
        float x0, float y0, float a0,
        float x1, float y1, float a1,
        float x2, float y2, float a2,
        float x3, float y3, float a3
    ) {
        if (vertexCount + 6 > MAX_VERTICES) {
            return;
        }

        putVertex(x0, y0, a0);
        putVertex(x1, y1, a1);
        putVertex(x2, y2, a2);

        putVertex(x0, y0, a0);
        putVertex(x2, y2, a2);
        putVertex(x3, y3, a3);
    }

    private void putVertex(float x, float y, float alpha) {
        int offset = vertexCount * FLOATS_PER_VERTEX;
        vertexData[offset] = x;
        vertexData[offset + 1] = y;
        vertexData[offset + 2] = MathUtils.clamp(alpha, 0f, 1f);
        vertexCount++;
    }

    @Override
    public void dispose() {
        if (mesh != null) {
            mesh.dispose();
            mesh = null;
        }
        if (shader != null) {
            shader.dispose();
            shader = null;
        }
        trailsByShip.clear();
    }

    private static final class ShipTrails {
        EngineTrail[] engineTrails;

        ShipTrails(int engineCount) {
            resize(engineCount);
        }

        void resize(int engineCount) {
            engineTrails = new EngineTrail[engineCount];
            for (int i = 0; i < engineCount; i++) {
                engineTrails[i] = new EngineTrail();
            }
        }

        void update(
            float deltaTime,
            SpaceShip ship,
            boolean active,
            float intensity,
            Vector2 engineWorldPos,
            Vector2 exhaustDir
        ) {
            for (int i = 0; i < engineTrails.length; i++) {
                ship.writeEngineWorldPosition(i, engineWorldPos);
                ship.writeEngineExhaustDirection(i, exhaustDir);
                engineTrails[i].update(
                    deltaTime,
                    engineWorldPos.x,
                    engineWorldPos.y,
                    exhaustDir.x,
                    exhaustDir.y,
                    ship.getWorldSpeed(),
                    active,
                    intensity
                );
            }
        }
    }

    private static final class EngineTrail {
        final TrailPoint[] points = new TrailPoint[POINTS_PER_ENGINE];
        int head;
        int count;
        float emitTimer;

        EngineTrail() {
            for (int i = 0; i < points.length; i++) {
                points[i] = new TrailPoint();
            }
        }

        void update(
            float deltaTime,
            float x,
            float y,
            float exhaustX,
            float exhaustY,
            float worldSpeed,
            boolean active,
            float intensity
        ) {
            for (int i = 0; i < count; i++) {
                int idx = (head - 1 - i + POINTS_PER_ENGINE) % POINTS_PER_ENGINE;
                points[idx].age += deltaTime;
            }

            while (count > 0) {
                int oldest = (head - count + POINTS_PER_ENGINE) % POINTS_PER_ENGINE;
                if (points[oldest].age <= POINT_LIFETIME) {
                    break;
                }
                count--;
            }

            if (!active) {
                emitTimer = 0f;
                return;
            }

            emitTimer += deltaTime;
            if (emitTimer < POINT_INTERVAL) {
                return;
            }
            emitTimer = 0f;

            TrailPoint point = points[head];
            point.x = x;
            point.y = y;
            float exhaustLen = (float) Math.sqrt(exhaustX * exhaustX + exhaustY * exhaustY);
            if (exhaustLen > 0.0001f) {
                point.exhaustX = exhaustX / exhaustLen;
                point.exhaustY = exhaustY / exhaustLen;
            } else {
                point.exhaustX = 0f;
                point.exhaustY = -1f;
            }
            point.segmentLength = Math.max(MIN_SEGMENT_LENGTH, worldSpeed * POINT_INTERVAL * 6f);
            point.age = 0f;
            point.intensity = intensity;
            head = (head + 1) % POINTS_PER_ENGINE;
            count = Math.min(count + 1, POINTS_PER_ENGINE);
        }
    }

    private static final class TrailPoint {
        float x;
        float y;
        float exhaustX;
        float exhaustY;
        float segmentLength;
        float age;
        float intensity = 1f;
    }
}
