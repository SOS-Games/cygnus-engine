package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ObjectMap;

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

    private SpriteBatch spriteBatch;
    private Texture shipTexture;
    private Sprite shipSprite;

    private ShipData shipData;
    private FileHandle shipTextureFile;

    private final Vector2 shipCenterWorld = new Vector2(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f);

    private Vector2 selectedPoint = null; // COM / weapon / engine
    private PointOwner selectedPointOwner = PointOwner.NONE;
    private CornerHandle selectedCorner = CornerHandle.NONE;
    private final Vector2 tmp2 = new Vector2();

    /** Editor overlay visibility — keeps the hull view uncluttered. */
    private boolean layerBounds = true;
    private boolean layerWeapons = true;
    private boolean layerEngines = true;
    private boolean layerCom = true;
    private boolean layerColliders = true;

    private boolean symmetryEnabled = true;
    private final Vector2 hoveredMouseRel = new Vector2();
    private Vector2 hoveredPoint = null;
    private WeaponSlot hoveredWeaponSlot = null;

    /** Multi-circle collider authoring */
    private ColliderPick hoveredColliderPick = ColliderPick.NONE;
    private ShipColliderCircle hoveredColliderCircle = null;
    private ShipColliderCircle selectedCollider = null;
    private ColliderDragMode colliderDrag = ColliderDragMode.NONE;

    private final Map<ShipColliderCircle, ShipColliderCircle> colliderMirrorPairs = new IdentityHashMap<>();

    private final Map<WeaponSlot, WeaponSlot> weaponMirrorPairs = new IdentityHashMap<>();
    private WeaponSlot selectedWeaponSlot;
    private final ObjectMap<String, Sprite> weaponPreviewSprites = new ObjectMap<>();
    private final Vector2 hullPixelScale = new Vector2(1f, 1f);

    private enum CornerHandle { NONE, BL, BR, TL, TR }
    private enum PointOwner { NONE, COM, WEAPON, ENGINE }
    private enum ColliderPick { NONE, CENTER, EDGE }
    private enum ColliderDragMode { NONE, CENTER, RADIUS_EDGE }

    /** Persistent editor selection (survives mouse release). */
    public enum SelectionKind { NONE, WEAPON, ENGINE, COM, COLLIDER }

    private SelectionKind selectionKind = SelectionKind.NONE;

    private final InputAdapter input = new InputAdapter() {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (shipData == null) return false;

            Vector2 mouseRel = screenToShipRelative(screenX, screenY);

            if (layerBounds) {
                CornerHandle corner = pickBoundsCorner(mouseRel);
                if (corner != CornerHandle.NONE) {
                    clearSelection();
                    selectedCollider = null;
                    colliderDrag = ColliderDragMode.NONE;
                    selectedCorner = corner;
                    return true;
                }
            }

            selectedCorner = CornerHandle.NONE;

            if (layerWeapons) {
                for (WeaponSlot slot : shipData.weaponSlots) {
                    if (mouseRel.dst(slot.x, slot.y) <= HANDLE_RADIUS * zoomFactor()) {
                        selectWeaponSlot(slot);
                        selectedCollider = null;
                        colliderDrag = ColliderDragMode.NONE;
                        return true;
                    }
                }
            }

            if (layerEngines) {
                for (Vector2 pos : shipData.enginePositions) {
                    if (mouseRel.dst(pos) <= HANDLE_RADIUS * zoomFactor()) {
                        selectEngine(pos);
                        selectedCollider = null;
                        colliderDrag = ColliderDragMode.NONE;
                        return true;
                    }
                }
            }

            if (layerColliders) {
                ShipColliderCircle centerHit = pickColliderCenter(mouseRel);
                if (centerHit != null) {
                    selectCollider(centerHit);
                    colliderDrag = ColliderDragMode.CENTER;
                    return true;
                }

                ShipColliderCircle edgeHit = pickColliderEdge(mouseRel);
                if (edgeHit != null) {
                    selectCollider(edgeHit);
                    colliderDrag = ColliderDragMode.RADIUS_EDGE;
                    return true;
                }
            }

            selectedCollider = null;
            colliderDrag = ColliderDragMode.NONE;

            if (layerCom) {
                if (mouseRel.dst(shipData.centerOfMass) <= HANDLE_RADIUS * zoomFactor()) {
                    selectCenterOfMass();
                    return true;
                }
            }

            clearSelection();
            return false;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (shipData == null) return false;

            boolean draggingWeapon = selectedWeaponSlot != null && selectedPointOwner == PointOwner.WEAPON;
            boolean draggingCollider = selectedCollider != null && colliderDrag != ColliderDragMode.NONE;

            if (selectedCorner == CornerHandle.NONE && selectedPoint == null && !draggingWeapon && !draggingCollider) {
                return false;
            }

            Vector2 mouseRel = screenToShipRelative(screenX, screenY);
            snap(mouseRel);

            if (selectedPointOwner == PointOwner.WEAPON && selectedWeaponSlot != null) {
                selectedWeaponSlot.x = mouseRel.x;
                selectedWeaponSlot.y = mouseRel.y;
                if (symmetryEnabled) {
                    WeaponSlot mirror = weaponMirrorPairs.get(selectedWeaponSlot);
                    if (mirror != null) {
                        mirror.x = -selectedWeaponSlot.x;
                        mirror.y = selectedWeaponSlot.y;
                    }
                }
                return true;
            }

            if (draggingCollider) {
                ShipColliderCircle c = selectedCollider;
                if (colliderDrag == ColliderDragMode.CENTER) {
                    c.x = mouseRel.x;
                    c.y = mouseRel.y;
                    if (symmetryEnabled) {
                        ShipColliderCircle mirror = colliderMirrorPairs.get(c);
                        if (mirror != null) {
                            mirror.x = -c.x;
                            mirror.y = c.y;
                        }
                    }
                } else if (colliderDrag == ColliderDragMode.RADIUS_EDGE) {
                    float newR = Math.max(GRID_SIZE, Math.round(mouseRel.dst(c.x, c.y)));
                    c.radius = newR;
                    if (symmetryEnabled) {
                        ShipColliderCircle mirror = colliderMirrorPairs.get(c);
                        if (mirror != null) {
                            mirror.radius = newR;
                        }
                    }
                }
                return true;
            }

            if (selectedPoint != null) {
                selectedPoint.set(mouseRel);
                if (symmetryEnabled && selectedPointOwner != PointOwner.COM) {
                    List<Vector2> ownerList = switch (selectedPointOwner) {
                        case ENGINE -> shipData.enginePositions;
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
            if (shipData == null) return false;

            selectedCorner = CornerHandle.NONE;
            colliderDrag = ColliderDragMode.NONE;
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
    }

    public void update(float deltaTime) {}

    private float zoomFactor() {
        return Math.max(1f, camera.zoom);
    }

    public void setLayerBoundsVisible(boolean v) {
        layerBounds = v;
    }

    public void setLayerWeaponsVisible(boolean v) {
        layerWeapons = v;
    }

    public void setLayerEnginesVisible(boolean v) {
        layerEngines = v;
    }

    public void setLayerCenterOfMassVisible(boolean v) {
        layerCom = v;
    }

    public void setLayerCollidersVisible(boolean v) {
        layerColliders = v;
    }

    /** Restore default layer visibility when opening a different ship. */
    public void resetLayerVisibility() {
        layerBounds = true;
        layerWeapons = true;
        layerEngines = true;
        layerCom = true;
        layerColliders = true;
    }

    public void clearSelection() {
        selectionKind = SelectionKind.NONE;
        selectedWeaponSlot = null;
        selectedPoint = null;
        selectedPointOwner = PointOwner.NONE;
        selectedCollider = null;
    }

    public SelectionKind getSelectionKind() {
        return selectionKind;
    }

    public String getSelectionSummary() {
        if (shipData == null || selectionKind == SelectionKind.NONE) {
            return "Nothing selected — click a mount, engine, COM, or collider handle.";
        }
        return switch (selectionKind) {
            case WEAPON -> {
                WeaponSlot s = selectedWeaponSlot;
                if (s == null) yield "Weapon slot (invalid selection).";
                String eq = s.equippedWeaponId == null || s.equippedWeaponId.isBlank() ? "(empty)" : s.equippedWeaponId;
                yield "Weapon slot \"" + s.id + "\" at (" + (int) s.x + ", " + (int) s.y + ") — " + s.type + " — " + eq;
            }
            case ENGINE -> {
                if (selectedPoint == null) yield "Engine (invalid selection).";
                yield "Engine at (" + (int) selectedPoint.x + ", " + (int) selectedPoint.y + ")";
            }
            case COM -> "Center of mass at (" + (int) shipData.centerOfMass.x + ", " + (int) shipData.centerOfMass.y + ")";
            case COLLIDER -> {
                if (selectedCollider == null) yield "Collider (invalid selection).";
                yield "Collider at (" + (int) selectedCollider.x + ", " + (int) selectedCollider.y + ") r=" + (int) selectedCollider.radius;
            }
            default -> "Nothing selected.";
        };
    }

    public boolean deleteSelected() {
        if (shipData == null || selectionKind == SelectionKind.NONE || selectionKind == SelectionKind.COM) {
            return false;
        }
        switch (selectionKind) {
            case WEAPON: {
                if (selectedWeaponSlot == null) return false;
                WeaponSlot mirror = weaponMirrorPairs.remove(selectedWeaponSlot);
                if (mirror != null) {
                    weaponMirrorPairs.remove(mirror);
                    shipData.weaponSlots.remove(mirror);
                }
                shipData.weaponSlots.remove(selectedWeaponSlot);
                rebuildWeaponMirrorPairs();
                break;
            }
            case ENGINE: {
                if (selectedPoint == null) return false;
                shipData.enginePositions.remove(selectedPoint);
                break;
            }
            case COLLIDER: {
                if (selectedCollider == null) return false;
                ShipColliderCircle mirror = colliderMirrorPairs.remove(selectedCollider);
                if (mirror != null) {
                    colliderMirrorPairs.remove(mirror);
                    shipData.colliders.remove(mirror);
                }
                shipData.colliders.remove(selectedCollider);
                rebuildColliderMirrorPairs();
                break;
            }
            default:
                return false;
        }
        clearSelection();
        return true;
    }

    private void selectWeaponSlot(WeaponSlot slot) {
        selectionKind = SelectionKind.WEAPON;
        selectedWeaponSlot = slot;
        selectedPoint = null;
        selectedPointOwner = PointOwner.WEAPON;
    }

    private void selectEngine(Vector2 pos) {
        selectionKind = SelectionKind.ENGINE;
        selectedPoint = pos;
        selectedWeaponSlot = null;
        selectedPointOwner = PointOwner.ENGINE;
    }

    private void selectCenterOfMass() {
        selectionKind = SelectionKind.COM;
        selectedPoint = shipData.centerOfMass;
        selectedWeaponSlot = null;
        selectedPointOwner = PointOwner.COM;
    }

    private void selectCollider(ShipColliderCircle c) {
        selectionKind = SelectionKind.COLLIDER;
        selectedCollider = c;
        selectedPoint = null;
        selectedWeaponSlot = null;
        selectedPointOwner = PointOwner.NONE;
    }

    public void render() {
        if (shipData == null || shipTexture == null || shipSprite == null) return;

        spriteBatch.setProjectionMatrix(camera.combined);
        spriteBatch.begin();

        configureHullSpriteForDraw();
        shipSprite.setRotation(ShipSpriteOrientation.editorSpriteRotation());
        shipSprite.setPosition(
            shipCenterWorld.x - shipSprite.getOriginX(),
            shipCenterWorld.y - shipSprite.getOriginY()
        );
        shipSprite.draw(spriteBatch);

        if (layerWeapons) {
            drawWeaponSlotPreviews(spriteBatch);
        }

        spriteBatch.end();

        drawOverlays();
    }

    public void resize(int width, int height) {
        float aspectRatio = (float) width / (float) height;
        float worldAspectRatio = WORLD_WIDTH / WORLD_HEIGHT;

        if (aspectRatio > worldAspectRatio) {
            camera.viewportHeight = height;
            camera.viewportWidth = height * aspectRatio;
        } else {
            camera.viewportWidth = width;
            camera.viewportHeight = width / aspectRatio;
        }

        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);

        camera.update();
        spriteBatch.setProjectionMatrix(camera.combined);
    }

    public void dispose() {
        disposeHullTexture();
        clearWeaponPreviewTextures();
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

    /**
     * Load hull definition from a ship JSON file (e.g. {@code mods/core/fighter.json}) and the texture
     * referenced by {@link ShipData#texturePath}, or a placeholder when the file is missing.
     */
    public void loadShipFromDefinition(FileHandle jsonFile) {
        if (jsonFile == null || !jsonFile.exists()) {
            throw new IllegalArgumentException("Ship JSON must exist");
        }
        clearWeaponPreviewTextures();
        clearSelection();
        resetLayerVisibility();
        shipData = ShipDataIO.loadFromJson(jsonFile);
        if (shipData == null) {
            throw new IllegalStateException("Failed to parse ship JSON: " + jsonFile.path());
        }
        reloadHullTexture();
        ensureDefaultColliders();
        rebuildColliderMirrorPairs();
        rebuildWeaponMirrorPairs();
        Gdx.app.log("ShipEditor", "Loaded ship JSON: " + jsonFile.path());
    }

    /** Reload hull image from current {@link ShipData#texturePath} (after user picks a new PNG). */
    public void reloadHullTextureFromShipData() {
        if (shipData == null) return;
        reloadHullTexture();
        ensureDefaultColliders();
        rebuildColliderMirrorPairs();
    }

    private void reloadHullTexture() {
        disposeHullTexture();
        FileHandle resolved = resolveHullTextureFile();
        if (resolved != null && resolved.exists()) {
            shipTexture = new Texture(resolved);
            shipTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            shipTextureFile = resolved;
        } else {
            shipTexture = createPlaceholderHullTexture();
            shipTextureFile = null;
        }
        shipSprite = new Sprite(shipTexture);
    }

    /** Match in-game hull sizing (bounds in ship-local space, nose = +Y). */
    private void configureHullSpriteForDraw() {
        if (shipSprite == null || shipData == null) return;
        ShipSpriteOrientation.applyHullLayout(
            shipSprite,
            shipData.bounds,
            shipData.centerOfMass.x,
            shipData.centerOfMass.y,
            shipTexture.getWidth(),
            shipTexture.getHeight()
        );
        ShipSpriteOrientation.computeHullPixelScale(
            shipData.bounds,
            shipTexture.getWidth(),
            shipTexture.getHeight(),
            hullPixelScale
        );
    }

    private FileHandle resolveHullTextureFile() {
        if (shipData == null || shipData.texturePath == null || shipData.texturePath.isBlank()) {
            return null;
        }
        FileHandle local = Gdx.files.local(shipData.texturePath);
        if (local.exists()) {
            return local;
        }
        FileHandle internal = Gdx.files.internal(shipData.texturePath);
        if (internal.exists()) {
            return internal;
        }
        return local;
    }

    private void disposeHullTexture() {
        if (shipTexture != null) {
            shipTexture.dispose();
            shipTexture = null;
        }
        shipSprite = null;
        shipTextureFile = null;
    }

    private static Texture createPlaceholderHullTexture() {
        Pixmap pm = new Pixmap(64, 64, Pixmap.Format.RGB888);
        pm.setColor(0.42f, 0.42f, 0.46f, 1f);
        pm.fill();
        pm.setColor(0.28f, 0.28f, 0.32f, 1f);
        for (int x = 0; x < 64; x += 8) {
            for (int y = 0; y < 64; y += 8) {
                if (((x / 8) + (y / 8)) % 2 == 0) {
                    pm.fillRectangle(x, y, 8, 8);
                }
            }
        }
        Texture t = new Texture(pm);
        pm.dispose();
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return t;
    }

    public WeaponSlot getSelectedWeaponSlot() {
        return selectedWeaponSlot;
    }

    public void setSelectedWeaponSlotType(WeaponSlot.SlotType type) {
        if (selectedWeaponSlot != null && type != null) {
            selectedWeaponSlot.type = type;
        }
    }

    public void setSelectedWeaponEquippedId(String weaponId) {
        if (selectedWeaponSlot != null) {
            selectedWeaponSlot.equippedWeaponId = weaponId == null || weaponId.isBlank() ? null : weaponId;
        }
    }

    public void addWeaponSlot() {
        if (shipData == null) return;
        int n = shipData.weaponSlots.size();
        WeaponSlot primary = new WeaponSlot("slot_" + n, 0f, 0f, WeaponSlot.SlotType.TURRET);
        shipData.weaponSlots.add(primary);
        if (symmetryEnabled) {
            WeaponSlot mirror = new WeaponSlot("slot_" + (n + 1), 0f, 0f, WeaponSlot.SlotType.TURRET);
            shipData.weaponSlots.add(mirror);
            weaponMirrorPairs.put(primary, mirror);
            weaponMirrorPairs.put(mirror, primary);
        }
        shipData.normalizeWeaponSlots();
    }

    public void addEnginePosition() {
        if (shipData == null) return;
        addPointWithOptionalMirror(shipData.enginePositions, new Vector2(0, 0));
    }

    public void addColliderCircle() {
        if (shipData == null) return;
        ensureCollidersNonNull();

        Rectangle b = shipData.bounds;
        float rr = Math.max(6f, 0.2f * Math.min(b.width, b.height));
        float cx = rr + GRID_SIZE;
        float cy = b.y + b.height * 0.5f;

        ShipColliderCircle primary = new ShipColliderCircle(cx, cy, rr);
        shipData.colliders.add(primary);

        if (symmetryEnabled && !MathUtils.isEqual(primary.x, 0f, 0.5f)) {
            ShipColliderCircle mirror = new ShipColliderCircle(-primary.x, primary.y, primary.radius);
            shipData.colliders.add(mirror);
            colliderMirrorPairs.put(primary, mirror);
            colliderMirrorPairs.put(mirror, primary);
        }
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

        float z = HANDLE_RADIUS * zoomFactor();
        if (mouseRel.dst(bl) <= z) return CornerHandle.BL;
        if (mouseRel.dst(br) <= z) return CornerHandle.BR;
        if (mouseRel.dst(tl) <= z) return CornerHandle.TL;
        if (mouseRel.dst(tr) <= z) return CornerHandle.TR;
        return CornerHandle.NONE;
    }

    private ShipColliderCircle pickColliderCenter(Vector2 mouseRel) {
        float z = HANDLE_RADIUS * zoomFactor();
        for (ShipColliderCircle c : shipData.colliders) {
            if (mouseRel.dst(c.x, c.y) <= z) return c;
        }
        return null;
    }

    private ShipColliderCircle pickColliderEdge(Vector2 mouseRel) {
        float z = HANDLE_RADIUS * zoomFactor();
        float edgeTol = 10f * zoomFactor();
        float bestErr = Float.MAX_VALUE;
        ShipColliderCircle best = null;
        for (ShipColliderCircle c : shipData.colliders) {
            float d = mouseRel.dst(c.x, c.y);
            if (d <= z + 2f) continue;
            float err = Math.abs(d - c.radius);
            if (err <= edgeTol && err < bestErr) {
                bestErr = err;
                best = c;
            }
        }
        return best;
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

    private void ensureCollidersNonNull() {
        if (shipData.colliders == null) shipData.colliders = new java.util.ArrayList<>();
    }

    private void ensureDefaultColliders() {
        if (shipData == null || shipTexture == null) return;
        ensureCollidersNonNull();
        shipData.normalizeColliders();
        if (!shipData.colliders.isEmpty()) return;

        float halfW = shipTexture.getWidth() / 2f;
        float halfH = shipTexture.getHeight() / 2f;
        float r = Math.max(8f, 0.45f * Math.min(halfW, halfH));
        shipData.colliders.add(new ShipColliderCircle(0f, 0f, r));
    }

    private void rebuildColliderMirrorPairs() {
        colliderMirrorPairs.clear();
        if (shipData == null || shipData.colliders == null) return;
        List<ShipColliderCircle> list = shipData.colliders;
        for (int i = 0; i < list.size(); i++) {
            ShipColliderCircle a = list.get(i);
            if (colliderMirrorPairs.containsKey(a)) continue;
            for (int j = i + 1; j < list.size(); j++) {
                ShipColliderCircle b = list.get(j);
                if (MathUtils.isEqual(a.x, -b.x, 0.75f)
                    && MathUtils.isEqual(a.y, b.y, 0.75f)
                    && MathUtils.isEqual(a.radius, b.radius, 0.75f)) {
                    colliderMirrorPairs.put(a, b);
                    colliderMirrorPairs.put(b, a);
                    break;
                }
            }
        }
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
        hoveredMouseRel.set(screenToShipRelative(screenX, screenY));

        hoveredPoint = null;
        hoveredColliderPick = ColliderPick.NONE;
        hoveredColliderCircle = null;
        hoveredWeaponSlot = null;

        float pointSearchRadius = HANDLE_RADIUS * zoomFactor();

        if (layerWeapons) {
            for (WeaponSlot slot : shipData.weaponSlots) {
                if (hoveredMouseRel.dst(slot.x, slot.y) <= pointSearchRadius) {
                    hoveredWeaponSlot = slot;
                    return;
                }
            }
        }
        if (layerEngines) {
            for (Vector2 pos : shipData.enginePositions) {
                if (hoveredMouseRel.dst(pos) <= pointSearchRadius) {
                    hoveredPoint = pos;
                    return;
                }
            }
        }

        if (layerColliders) {
            ShipColliderCircle cCenter = pickColliderCenter(hoveredMouseRel);
            if (cCenter != null) {
                hoveredColliderPick = ColliderPick.CENTER;
                hoveredColliderCircle = cCenter;
                return;
            }
            ShipColliderCircle cEdge = pickColliderEdge(hoveredMouseRel);
            if (cEdge != null) {
                hoveredColliderPick = ColliderPick.EDGE;
                hoveredColliderCircle = cEdge;
                return;
            }
        }

        if (layerCom) {
            if (hoveredMouseRel.dst(shipData.centerOfMass) <= pointSearchRadius) {
                hoveredPoint = shipData.centerOfMass;
            }
        }
    }

    private void drawOverlays() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        Rectangle b = shipData.bounds;

        float circleRadius = 4f * camera.zoom;

        if (layerBounds) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.GREEN);
            shapeRenderer.rect(shipCenterWorld.x + b.x, shipCenterWorld.y + b.y, b.width, b.height);
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.GREEN);
            shapeRenderer.circle(shipCenterWorld.x + b.x, shipCenterWorld.y + b.y, circleRadius);
            shapeRenderer.circle(shipCenterWorld.x + b.x + b.width, shipCenterWorld.y + b.y, circleRadius);
            shapeRenderer.circle(shipCenterWorld.x + b.x, shipCenterWorld.y + b.y + b.height, circleRadius);
            shapeRenderer.circle(shipCenterWorld.x + b.x + b.width, shipCenterWorld.y + b.y + b.height, circleRadius);
            shapeRenderer.end();
        }

        if (layerColliders) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.CYAN);
            for (ShipColliderCircle c : shipData.colliders) {
                boolean hl = hoveredColliderCircle == c
                    || hoveredColliderPick != ColliderPick.NONE && hoveredColliderCircle == c;
                shapeRenderer.setColor(hl ? Color.WHITE : Color.CYAN);
                shapeRenderer.circle(shipCenterWorld.x + c.x, shipCenterWorld.y + c.y, c.radius);
            }
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.CYAN);
            for (ShipColliderCircle c : shipData.colliders) {
                boolean selected = selectionKind == SelectionKind.COLLIDER && c == selectedCollider;
                boolean hoveredCenter = hoveredColliderPick == ColliderPick.CENTER && hoveredColliderCircle == c;
                float pr = selected ? circleRadius * 1.8f : (hoveredCenter ? circleRadius * 1.4f : circleRadius);
                shapeRenderer.circle(shipCenterWorld.x + c.x, shipCenterWorld.y + c.y, pr);
            }
            shapeRenderer.end();
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        if (layerWeapons) {
            for (WeaponSlot slot : shipData.weaponSlots) {
                boolean selected = selectionKind == SelectionKind.WEAPON && slot == selectedWeaponSlot;
                boolean hovered = slot == hoveredWeaponSlot;
                if (slot.type == WeaponSlot.SlotType.HARDPOINT) {
                    shapeRenderer.setColor(selected ? Color.WHITE : (hovered ? Color.YELLOW : Color.MAGENTA));
                } else {
                    shapeRenderer.setColor(selected ? Color.WHITE : (hovered ? Color.YELLOW : Color.RED));
                }
                float r = selected ? circleRadius * 1.8f : (hovered ? circleRadius * 1.4f : circleRadius);
                shapeRenderer.circle(shipCenterWorld.x + slot.x, shipCenterWorld.y + slot.y, r);
            }
        }

        if (layerEngines) {
            for (Vector2 pos : shipData.enginePositions) {
                boolean selected = selectionKind == SelectionKind.ENGINE && pos == selectedPoint;
                boolean hovered = pos == hoveredPoint;
                shapeRenderer.setColor(selected ? Color.WHITE : (hovered ? Color.YELLOW : Color.ORANGE));
                float r = selected ? circleRadius * 1.8f : (hovered ? circleRadius * 1.4f : circleRadius);
                shapeRenderer.circle(shipCenterWorld.x + pos.x, shipCenterWorld.y + pos.y, r);
            }
        }

        if (layerCom) {
            boolean selected = selectionKind == SelectionKind.COM;
            boolean hovered = shipData.centerOfMass == hoveredPoint;
            shapeRenderer.setColor(selected ? Color.WHITE : (hovered ? Color.YELLOW : Color.BLUE));
            float r = selected ? circleRadius * 1.8f : (hovered ? circleRadius * 1.4f : circleRadius);
            shapeRenderer.circle(shipCenterWorld.x + shipData.centerOfMass.x, shipCenterWorld.y + shipData.centerOfMass.y, r);
        }

        if (layerColliders) {
            for (ShipColliderCircle c : shipData.colliders) {
                boolean selected = selectionKind == SelectionKind.COLLIDER && c == selectedCollider;
                if (selected) {
                    shapeRenderer.setColor(Color.WHITE);
                    shapeRenderer.circle(shipCenterWorld.x + c.x, shipCenterWorld.y + c.y, circleRadius * 1.5f);
                }
            }
        }

        // Collider edge hover handle (when not selected)
        if (hoveredColliderCircle != null && layerColliders && hoveredColliderPick == ColliderPick.EDGE
            && !(selectionKind == SelectionKind.COLLIDER && hoveredColliderCircle == selectedCollider)) {
            shapeRenderer.setColor(Color.YELLOW);
            tmp2.set(hoveredMouseRel.x - hoveredColliderCircle.x, hoveredMouseRel.y - hoveredColliderCircle.y);
            float angle = tmp2.angleDeg();
            float rx = hoveredColliderCircle.x + MathUtils.cosDeg(angle) * hoveredColliderCircle.radius;
            float ry = hoveredColliderCircle.y + MathUtils.sinDeg(angle) * hoveredColliderCircle.radius;
            shapeRenderer.circle(shipCenterWorld.x + rx, shipCenterWorld.y + ry, circleRadius * 1.2f);
        }

        shapeRenderer.end();
    }

    private void rebuildWeaponMirrorPairs() {
        weaponMirrorPairs.clear();
        if (shipData == null) return;
        List<WeaponSlot> slots = shipData.weaponSlots;
        for (int i = 0; i < slots.size(); i++) {
            WeaponSlot a = slots.get(i);
            if (weaponMirrorPairs.containsKey(a)) continue;
            for (int j = i + 1; j < slots.size(); j++) {
                WeaponSlot b = slots.get(j);
                if (MathUtils.isEqual(a.x, -b.x, 0.75f) && MathUtils.isEqual(a.y, b.y, 0.75f)) {
                    weaponMirrorPairs.put(a, b);
                    weaponMirrorPairs.put(b, a);
                    break;
                }
            }
        }
    }

    private void drawWeaponSlotPreviews(SpriteBatch batch) {
        if (shipData == null) return;
        for (WeaponSlot slot : shipData.weaponSlots) {
            if (slot.equippedWeaponId == null || slot.equippedWeaponId.isBlank()) continue;
            WeaponData wd = WeaponDataIO.loadById(slot.equippedWeaponId);
            if (wd == null || wd.turretSprite == null || wd.turretSprite.isBlank()) continue;
            Sprite weaponSprite = obtainPreviewSprite(wd.turretSprite);
            if (weaponSprite == null) continue;
            float wx = shipCenterWorld.x + slot.x;
            float wy = shipCenterWorld.y + slot.y;
            ShipSpriteOrientation.applyWeaponPixelScale(weaponSprite, hullPixelScale.x, hullPixelScale.y);
            weaponSprite.setCenter(wx, wy);
            weaponSprite.setRotation(ShipSpriteOrientation.editorSpriteRotation());
            weaponSprite.draw(batch);
        }
    }

    private Sprite obtainPreviewSprite(String path) {
        Sprite cached = weaponPreviewSprites.get(path);
        if (cached != null) return cached;
        Texture tex = obtainPreviewTexture(path);
        if (tex == null) return null;
        Sprite sprite = new Sprite(tex);
        sprite.setOriginCenter();
        weaponPreviewSprites.put(path, sprite);
        return sprite;
    }

    private Texture obtainPreviewTexture(String path) {
        Sprite existing = weaponPreviewSprites.get(path);
        if (existing != null) return existing.getTexture();
        FileHandle fh = WeaponDataIO.resolveTextureFile(path);
        if (fh == null || !fh.exists()) return null;
        Texture tex = new Texture(fh);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return tex;
    }

    private void clearWeaponPreviewTextures() {
        for (Sprite s : weaponPreviewSprites.values()) {
            if (s != null) s.getTexture().dispose();
        }
        weaponPreviewSprites.clear();
    }
}
