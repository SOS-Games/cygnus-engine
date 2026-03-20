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
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import java.util.List;

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
    private Viewport viewport;

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
                    Vector2 mirror = findMirrorPoint(ownerList, selectedPoint);
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
            selectedPoint = null;
            selectedPointOwner = PointOwner.NONE;
            selectedCorner = CornerHandle.NONE;
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
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
        viewport = new FitViewport(WORLD_WIDTH, WORLD_HEIGHT, camera);
        
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
        viewport.update(width, height, true);
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
        addPointWithOptionalMirror(shipData.colliderVertices, new Vector2(0, 0));
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

        shapeRenderer.end();
    }
}