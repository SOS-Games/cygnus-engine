package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ShipEditor {

    private static final float WORLD_WIDTH = 800f;
    private static final float WORLD_HEIGHT = 600f;
    private static final float HANDLE_RADIUS = 8f;
    private static final float GRID_SIZE = 1f;
    private static final float ZOOM_SPEED = 0.1f;
    private static final float MIN_ZOOM = 0.2f;
    private static final float MAX_ZOOM = 3.0f;

    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;

    private SpriteBatch spriteBatch; // For future use with textures and fonts
    private Texture shipTexture;
    private Sprite shipSprite;

    private ShipData shipData;
    private FileHandle shipTextureFile;

    // Ship is always drawn centered; editor data is relative to center (0,0) in world space.
    private final Vector2 shipCenterWorld = new Vector2(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f);

    // Drag state
    private Vector2 selectedPoint = null; // COM / weapon / engine / collider vertex
    private PointOwner selectedPointOwner = PointOwner.NONE;
    private CornerHandle selectedCorner = CornerHandle.NONE;
    private final Vector2 tmp2 = new Vector2();
    private boolean symmetryEnabled = true;
    private boolean insertModeEnabled = true;
    private final Vector2 hoveredMouseRel = new Vector2();
    private Vector2 hoveredPoint = null;
    private int hoveredColliderEdgeIndex = -1;
    private final Vector2 hoveredInsertPoint = new Vector2();
    private final Map<Vector2, Vector2> colliderMirrorPairs = new IdentityHashMap<>();

    private enum CornerHandle { NONE, BL, BR, TL, TR }
    private enum PointOwner { NONE, COM, WEAPON, ENGINE, COLLIDER }

    private final InputAdapter input = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (shipData == null) return false;

            Vector2 mouseRel = screenToShipRelative(screenX, screenY);

            System.out.println("touchDown at " + mouseRel.x + ", " + mouseRel.y);

            // 1) Corner handles for bounds
            CornerHandle corner = pickBoundsCorner(mouseRel);
            if (corner != CornerHandle.NONE) {
                selectedCorner = corner;
                return true;
            }

            // 2) Points: weapon slots
            for (Vector2 slot : shipData.weaponSlots) {
                if (mouseRel.dst(slot) <= HANDLE_RADIUS) {
                    selectedPoint = slot;
                    selectedPointOwner = PointOwner.WEAPON;
                    return true;
                }
            }

            // 3) Points: engines
            for (Vector2 pos : shipData.enginePositions) {
                if (mouseRel.dst(pos) <= HANDLE_RADIUS) {
                    selectedPoint = pos;
                    selectedPointOwner = PointOwner.ENGINE;
                    return true;
                }
            }

            // 4) Points: collider vertices
            for (Vector2 v : shipData.colliderVertices) {
                if (mouseRel.dst(v) <= HANDLE_RADIUS) {
                    selectedPoint = v;
                    selectedPointOwner = PointOwner.COLLIDER;
                    return true;
                }
            }

            // 5) Center of mass
            if (mouseRel.dst(shipData.centerOfMass) <= HANDLE_RADIUS) {
                selectedPoint = shipData.centerOfMass;
                selectedPointOwner = PointOwner.COM;
                return true;
            }

            // 6) Insert into collider edge in insert mode.
            if (insertModeEnabled && tryInsertColliderVertex(mouseRel)) {
                return true;
            }

            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (shipData == null) return false;

            if (selectedPoint == null && selectedCorner == CornerHandle.NONE) return false;

            Vector2 mouseRel = screenToShipRelative(screenX, screenY);
            snap(mouseRel);

            if (selectedPoint != null) {
                selectedPoint.set(mouseRel);
                if (symmetryEnabled && selectedPointOwner != PointOwner.COM) {
                    List<Vector2> ownerList = switch (selectedPointOwner) {
                        case WEAPON -> shipData.weaponSlots;
                        case ENGINE -> shipData.enginePositions;
                        case COLLIDER -> shipData.colliderVertices;
                        default -> null;
                    };
                    Vector2 mirror = selectedPointOwner == PointOwner.COLLIDER
                        ? colliderMirrorPairs.get(selectedPoint)
                        : findMirrorPoint(ownerList, selectedPoint);
                    if (mirror != null) {
                        mirror.set(-selectedPoint.x, selectedPoint.y);
                    }
                }
                return true;
            }

            if (selectedCorner != CornerHandle.NONE) {
                resizeBoundsFromCorner(mouseRel, selectedCorner);
                return true;
            }

            return false;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (shipData == null) return false;

            selectedPoint = null;
            selectedPointOwner = PointOwner.NONE;
            selectedCorner = CornerHandle.NONE;
            updateHoverState(screenX, screenY);
            return false;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            if (shipData == null) return false;

            updateHoverState(screenX, screenY);
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (shipData == null) return false;

            float nextZoom = camera.zoom + amountY * ZOOM_SPEED;
            camera.zoom = MathUtils.clamp(nextZoom, MIN_ZOOM, MAX_ZOOM);
            camera.update();
            return true;
        }
    };
    
    public ShipEditor() {
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(WORLD_WIDTH, WORLD_HEIGHT);
        camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();
        
        spriteBatch = new SpriteBatch();
        spriteBatch.setProjectionMatrix(camera.combined);

        // Start with no ship loaded; ModdingScreen will call loadShip(...).
    };

    public void update(float deltaTime) {
        
    }

    public void render() {
        if (shipData == null || shipTexture == null) return;

        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        // Draw ship centered in the world.
        float w = shipTexture.getWidth();
        float h = shipTexture.getHeight();
        shipSprite.setSize(w, h);
        shipSprite.setOriginCenter();
        shipSprite.setRotation(0f);
        shipSprite.setPosition(shipCenterWorld.x - w / 2f, shipCenterWorld.y - h / 2f);
        shipSprite.draw(spriteBatch);

        spriteBatch.end();

        drawOverlays();
    }

    public void resize(int width, int height) {
        // Maintain aspect ratio and fixed world size
        float aspectRatio = (float) width / (float) height;
        float worldAspectRatio = WORLD_WIDTH / WORLD_HEIGHT;
        
        // using width/height keeps shapes the same size instead of stretching like it would with WORLD_WIDTH / WORLD_HEIGHT
        if (aspectRatio > worldAspectRatio) {
            // Window is wider - fit to height
            camera.viewportHeight = height;
            camera.viewportWidth = height * aspectRatio;
        } else {
            // Window is taller - fit to width
            camera.viewportWidth = width;
            camera.viewportHeight = width / aspectRatio;
        }
        
        // this keeps the same position on the screen, and won't jump the camera up and right like it would if we used screen width/height
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);

        camera.update();
        spriteBatch.setProjectionMatrix(camera.combined);
    }

    public void dispose() {
        if (shipTexture != null) shipTexture.dispose();
        if (spriteBatch != null) spriteBatch.dispose();
        if (shapeRenderer != null) shapeRenderer.dispose();
    }

    public InputAdapter getInputProcessor() {
        return input;
    }

    public ShipData getShipData() {
        return shipData;
    }

    public FileHandle getShipTextureFile() {
        return shipTextureFile;
    }

    public void loadShip(FileHandle textureFile) {
        if (textureFile == null || !textureFile.exists()) {
            throw new IllegalArgumentException("Texture file must exist");
        }

        this.shipTextureFile = textureFile;
        this.shipData = ShipDataIO.loadOrCreateDefault(textureFile);

        if (shipTexture != null) shipTexture.dispose();
        shipTexture = new Texture(textureFile);
        shipTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        shipSprite = new Sprite(shipTexture);
        ensureDefaultCollider();
        rebuildColliderMirrorPairs();

        Gdx.app.log("ShipEditor", "Loaded ship texture: " + textureFile.path());
    }

    public void addWeaponSlot() {
        if (shipData == null) return;
        addPointWithOptionalMirror(shipData.weaponSlots, new Vector2(0, 0));
    }

    public void addEnginePosition() {
        if (shipData == null) return;
        addPointWithOptionalMirror(shipData.enginePositions, new Vector2(0, 0));
    }

    public void addColliderVertex() {
        if (shipData == null) return;
        ensureDefaultCollider();
        if (insertModeEnabled && hoveredColliderEdgeIndex >= 0) {
            Vector2 insert = new Vector2(hoveredInsertPoint);
            snap(insert);
            insertColliderVertexAt(hoveredColliderEdgeIndex, insert);
            return;
        }
        // Fallback button behavior when not hovering an edge: add on right side midpoint.
        Rectangle b = shipData.bounds;
        Vector2 insert = new Vector2(Math.abs(b.x + b.width), 0f);
        snap(insert);
        shipData.colliderVertices.add(insert);
        if (symmetryEnabled && !MathUtils.isEqual(insert.x, 0f, 0.001f)) {
            Vector2 mirror = new Vector2(-insert.x, insert.y);
            shipData.colliderVertices.add(mirror);
            colliderMirrorPairs.put(insert, mirror);
            colliderMirrorPairs.put(mirror, insert);
        }
    }

    public void setInsertModeEnabled(boolean enabled) {
        this.insertModeEnabled = enabled;
    }

    public boolean isInsertModeEnabled() {
        return insertModeEnabled;
    }

    public void setSymmetryEnabled(boolean enabled) {
        this.symmetryEnabled = enabled;
    }

    public boolean isSymmetryEnabled() {
        return symmetryEnabled;
    }

    private Vector2 screenToShipRelative(int screenX, int screenY) {
        float clientX = Gdx.graphics.getWidth();
        float clientY = Gdx.graphics.getHeight();

        Vector3 clickedPos = new Vector3(screenX, screenY, 0);
        Vector3 tmp3 = camera.unproject(clickedPos, 0.0f, 0.0f, clientX, clientY);

        // don't use viewport.unproject because it returns NaN for tmp3.x and tmp3.y
        //viewport.unproject(tmp3.set(screenX, screenY, 0));

        float worldX = tmp3.x;
        float worldY = tmp3.y;
        tmp2.set(worldX - shipCenterWorld.x, worldY - shipCenterWorld.y);
        return tmp2;
    }

    private void snap(Vector2 v) {
        v.x = Math.round(v.x / GRID_SIZE) * GRID_SIZE;
        v.y = Math.round(v.y / GRID_SIZE) * GRID_SIZE;
    }

    private CornerHandle pickBoundsCorner(Vector2 mouseRel) {
        Rectangle b = shipData.bounds;
        Vector2 bl = new Vector2(b.x, b.y);
        Vector2 br = new Vector2(b.x + b.width, b.y);
        Vector2 tl = new Vector2(b.x, b.y + b.height);
        Vector2 tr = new Vector2(b.x + b.width, b.y + b.height);

        if (mouseRel.dst(bl) <= HANDLE_RADIUS) return CornerHandle.BL;
        if (mouseRel.dst(br) <= HANDLE_RADIUS) return CornerHandle.BR;
        if (mouseRel.dst(tl) <= HANDLE_RADIUS) return CornerHandle.TL;
        if (mouseRel.dst(tr) <= HANDLE_RADIUS) return CornerHandle.TR;
        return CornerHandle.NONE;
    }

    private void resizeBoundsFromCorner(Vector2 mouseRel, CornerHandle corner) {
        Rectangle b = shipData.bounds;
        float left = b.x;
        float right = b.x + b.width;
        float bottom = b.y;
        float top = b.y + b.height;

        switch (corner) {
            case BL -> { left = mouseRel.x; bottom = mouseRel.y; }
            case BR -> { right = mouseRel.x; bottom = mouseRel.y; }
            case TL -> { left = mouseRel.x; top = mouseRel.y; }
            case TR -> { right = mouseRel.x; top = mouseRel.y; }
            default -> {}
        }

        // Normalize so width/height stay positive.
        float newLeft = Math.min(left, right);
        float newRight = Math.max(left, right);
        float newBottom = Math.min(bottom, top);
        float newTop = Math.max(bottom, top);

        b.x = newLeft;
        b.y = newBottom;
        b.width = Math.max(1f, newRight - newLeft);
        b.height = Math.max(1f, newTop - newBottom);
    }

    private void addPointWithOptionalMirror(List<Vector2> points, Vector2 point) {
        points.add(point);
        if (!symmetryEnabled || MathUtils.isEqual(point.x, 0f, 0.001f)) return;
        points.add(new Vector2(-point.x, point.y));
    }

    private void ensureDefaultCollider() {
        if (shipData == null || shipTexture == null) return;
        if (shipData.colliderVertices != null && shipData.colliderVertices.size() >= 3) return;
        float halfW = shipTexture.getWidth() / 2f;
        float halfH = shipTexture.getHeight() / 2f;
        shipData.colliderVertices.clear();
        shipData.colliderVertices.add(new Vector2(halfW, halfH));
        shipData.colliderVertices.add(new Vector2(halfW, -halfH));
        shipData.colliderVertices.add(new Vector2(-halfW, -halfH));
        shipData.colliderVertices.add(new Vector2(-halfW, halfH));
    }

    private void rebuildColliderMirrorPairs() {
        colliderMirrorPairs.clear();
        if (shipData == null) return;
        List<Vector2> vertices = shipData.colliderVertices;
        for (int i = 0; i < vertices.size(); i++) {
            Vector2 a = vertices.get(i);
            if (colliderMirrorPairs.containsKey(a)) continue;
            for (int j = i + 1; j < vertices.size(); j++) {
                Vector2 b = vertices.get(j);
                if (MathUtils.isEqual(a.x, -b.x, 0.1f) && MathUtils.isEqual(a.y, b.y, 0.1f)) {
                    colliderMirrorPairs.put(a, b);
                    colliderMirrorPairs.put(b, a);
                    break;
                }
            }
        }
    }

    private boolean tryInsertColliderVertex(Vector2 mouseRel) {
        if (shipData.colliderVertices.size() < 2) return false;
        int edgeIndex = findHoveredColliderEdgeIndex(mouseRel);
        if (edgeIndex < 0) return false;
        Vector2 insert = new Vector2(hoveredInsertPoint);
        snap(insert);
        insertColliderVertexAt(edgeIndex, insert);
        return true;
    }

    private void insertColliderVertexAt(int edgeIndex, Vector2 insert) {
        List<Vector2> vertices = shipData.colliderVertices;
        Vector2 primary = new Vector2(insert);
        int insertAt = edgeIndex + 1;
        vertices.add(insertAt, primary);
        selectedPoint = primary;
        selectedPointOwner = PointOwner.COLLIDER;

        if (symmetryEnabled && !MathUtils.isEqual(primary.x, 0f, 0.001f)) {
            int mirroredEdge = mirrorEdgeIndex(edgeIndex, vertices.size() - 1);
            int mirrorInsertAt = mirroredEdge + 1;
            if (mirrorInsertAt == insertAt) mirrorInsertAt++;
            Vector2 mirror = new Vector2(-primary.x, primary.y);
            vertices.add(Math.min(mirrorInsertAt, vertices.size()), mirror);
            colliderMirrorPairs.put(primary, mirror);
            colliderMirrorPairs.put(mirror, primary);
        }
    }

    private int mirrorEdgeIndex(int edgeIndex, int originalSize) {
        return (originalSize - 1 - edgeIndex + originalSize) % originalSize;
    }

    private Vector2 findMirrorPoint(List<Vector2> points, Vector2 original) {
        if (points == null || original == null) return null;
        for (Vector2 p : points) {
            if (p == original) continue;
            if (MathUtils.isEqual(p.x, -original.x, 0.75f) && MathUtils.isEqual(p.y, original.y, 0.75f)) {
                return p;
            }
        }
        return null;
    }

    private void updateHoverState(int screenX, int screenY) {
        Vector2 mouseRel = screenToShipRelative(screenX, screenY);
        hoveredMouseRel.set(mouseRel);
        hoveredPoint = null;
        hoveredColliderEdgeIndex = -1;

        float pointSearchRadius = HANDLE_RADIUS * Math.max(1f, camera.zoom);

        for (Vector2 slot : shipData.weaponSlots) {
            if (mouseRel.dst(slot) <= pointSearchRadius) {
                hoveredPoint = slot;
                return;
            }
        }
        for (Vector2 pos : shipData.enginePositions) {
            if (mouseRel.dst(pos) <= pointSearchRadius) {
                hoveredPoint = pos;
                return;
            }
        }
        for (Vector2 v : shipData.colliderVertices) {
            if (mouseRel.dst(v) <= pointSearchRadius) {
                hoveredPoint = v;
                return;
            }
        }
        if (mouseRel.dst(shipData.centerOfMass) <= pointSearchRadius) {
            hoveredPoint = shipData.centerOfMass;
            return;
        }

        if (insertModeEnabled) {
            hoveredColliderEdgeIndex = findHoveredColliderEdgeIndex(mouseRel);
        }
    }

    private int findHoveredColliderEdgeIndex(Vector2 mouseRel) {
        List<Vector2> vertices = shipData.colliderVertices;
        if (vertices.size() < 2) return -1;
        float threshold = HANDLE_RADIUS * Math.max(1f, camera.zoom);
        float bestDistance = Float.MAX_VALUE;
        int bestIndex = -1;
        Vector2 bestPoint = null;
        for (int i = 0; i < vertices.size(); i++) {
            Vector2 p1 = vertices.get(i);
            Vector2 p2 = vertices.get((i + 1) % vertices.size());
            float d = Intersector.distanceSegmentPoint(p1, p2, mouseRel);
            if (d <= threshold && d < bestDistance) {
                bestDistance = d;
                bestIndex = i;
                if (bestPoint == null) bestPoint = new Vector2();
                Intersector.nearestSegmentPoint(p1, p2, mouseRel, bestPoint);
            }
        }
        if (bestIndex >= 0 && bestPoint != null) {
            hoveredInsertPoint.set(bestPoint);
        }
        return bestIndex;
    }

    private void drawOverlays() {
        shapeRenderer.setProjectionMatrix(camera.combined);

        // Convert relative -> world by adding shipCenterWorld
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.GREEN);
        Rectangle b = shipData.bounds;
        shapeRenderer.rect(shipCenterWorld.x + b.x, shipCenterWorld.y + b.y, b.width, b.height);

        // Collider polygon
        shapeRenderer.setColor(Color.CYAN);
        if (shipData.colliderVertices.size() >= 2) {
            for (int i = 0; i < shipData.colliderVertices.size(); i++) {
                Vector2 current = shipData.colliderVertices.get(i);
                Vector2 next = shipData.colliderVertices.get((i + 1) % shipData.colliderVertices.size());
                if (i == hoveredColliderEdgeIndex && insertModeEnabled) {
                    shapeRenderer.setColor(Color.WHITE);
                } else {
                    shapeRenderer.setColor(Color.CYAN);
                }
                shapeRenderer.line(
                    shipCenterWorld.x + current.x,
                    shipCenterWorld.y + current.y,
                    shipCenterWorld.x + next.x,
                    shipCenterWorld.y + next.y
                );
            }
        }
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // set circle radius based on zoom level
        float circleRadius = 4f * camera.zoom;

        // Weapon slots
        shapeRenderer.setColor(Color.RED);
        for (Vector2 slot : shipData.weaponSlots) {
            shapeRenderer.circle(shipCenterWorld.x + slot.x, shipCenterWorld.y + slot.y, circleRadius);
        }

        // Engines
        shapeRenderer.setColor(Color.ORANGE);
        for (Vector2 pos : shipData.enginePositions) {
            shapeRenderer.circle(shipCenterWorld.x + pos.x, shipCenterWorld.y + pos.y, circleRadius);
        }

        // Collider handles
        shapeRenderer.setColor(Color.CYAN);
        for (Vector2 v : shipData.colliderVertices) {
            shapeRenderer.circle(shipCenterWorld.x + v.x, shipCenterWorld.y + v.y, circleRadius);
        }

        // Center of mass
        shapeRenderer.setColor(Color.BLUE);
        shapeRenderer.circle(shipCenterWorld.x + shipData.centerOfMass.x, shipCenterWorld.y + shipData.centerOfMass.y, circleRadius);

        // Bounds corners as handles
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.circle(shipCenterWorld.x + b.x, shipCenterWorld.y + b.y, circleRadius);
        shapeRenderer.circle(shipCenterWorld.x + b.x + b.width, shipCenterWorld.y + b.y, circleRadius);
        shapeRenderer.circle(shipCenterWorld.x + b.x, shipCenterWorld.y + b.y + b.height, circleRadius);
        shapeRenderer.circle(shipCenterWorld.x + b.x + b.width, shipCenterWorld.y + b.y + b.height, circleRadius);

        // Hover state + insert ghost indicator.
        if (hoveredPoint != null) {
            shapeRenderer.setColor(Color.YELLOW);
            float hoverRadius = circleRadius * 1.6f;
            shapeRenderer.circle(shipCenterWorld.x + hoveredPoint.x, shipCenterWorld.y + hoveredPoint.y, hoverRadius);
        } else if (insertModeEnabled && hoveredColliderEdgeIndex >= 0) {
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.circle(
                shipCenterWorld.x + hoveredInsertPoint.x,
                shipCenterWorld.y + hoveredInsertPoint.y,
                circleRadius * 1.35f
            );
        }

        shapeRenderer.end();
    }
}