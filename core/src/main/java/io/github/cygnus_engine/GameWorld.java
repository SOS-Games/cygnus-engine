package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.Gdx;

public class GameWorld {
    private static final float WORLD_WIDTH = 800f;
    private static final float WORLD_HEIGHT = 600f;
    private static final float DEFAULT_SHIP_RADIUS = 18f;
    private static final float MIN_CAMERA_ZOOM = 0.35f;
    private static final float MAX_CAMERA_ZOOM = 3.5f;
    private static final float ZOOM_SCROLL_STEP = 0.12f;
    private static final float SELECTION_RING_PADDING = 6f;
    private static final float ORBIT_TARGET_DEBUG_LINE_LENGTH = 100f;
    private static final float DEFAULT_CAMERA_X = WORLD_WIDTH / 2f;
    private static final float DEFAULT_CAMERA_Y = WORLD_HEIGHT / 2f;
    /** Higher values snap the camera to the follow target faster. */
    private static final float CAMERA_FOLLOW_SPEED = 5f;
    /** Quantize rendered hull/turret angles to reduce nearest-neighbor crawl while turning. */
    private static final float RENDER_ANGLE_SNAP_DEG = 0.5f;
    /** Ships spawn between (anchor size + ship radius + this) and + max extra offset from anchor. */
    private static final float SHIP_SPAWN_MIN_CLEARANCE = 120f;
    private static final float SHIP_SPAWN_MAX_EXTRA_RADIUS = 220f;
    /** Fraction of spawned ships that run trade routes between orbit bodies. */
    private static final float TRADER_SHIP_FRACTION = 0.45f;

    private enum NpcShipRole {
        TRADER,
        MILITIA,
        CIVILIAN
    }
    private static final float PIRATE_WARP_IN_MIN_SECONDS = 5f;
    private static final float PIRATE_WARP_IN_MAX_SECONDS = 15f;
    private static final int MAX_ACTIVE_PIRATES = 6;
    
    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;
    private Array<GameObject> gameObjects;
    /** Space stations ships may patrol, trade at, and spawn near (from star-system JSON). */
    private final Array<GameObject> orbitTargets = new Array<>();
    private Array<SpaceShip> spaceShips;
    private float warpTimer;
    private float warpInterval;
    private float pirateSpawnTimer;
    private float pirateSpawnInterval;
    private int pirateSpawnCounter;
    private GameObject selectedObject;
    private SpaceShip cameraFollowTarget;
    private SpaceShip playerShip;
    private float cameraZoom = 1f;
    private float worldPerPixelX = 1f;
    private float worldPerPixelY = 1f;
    private final Vector2 renderSnapScratch = new Vector2();

    private SpriteBatch spriteBatch;
    private final Array<ShipData> shipTemplates = new Array<>();
    private ProjectileManager projectileManager;
    private EngineTrailRenderer engineTrailRenderer;
    private final ObjectMap<String, ShipData> shipDataById = new ObjectMap<>();
    private final ObjectMap<String, Texture> shipTextureCache = new ObjectMap<>();
    private final ObjectMap<String, Texture> weaponTextureCache = new ObjectMap<>();
    
    public GameWorld() {
        GameUtils.clearSpaceShips();
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(WORLD_WIDTH, WORLD_HEIGHT);
        camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
        camera.position.set(DEFAULT_CAMERA_X, DEFAULT_CAMERA_Y, 0);
        camera.update();
        
        gameObjects = new Array<>();
        spaceShips = new Array<>();
        projectileManager = new ProjectileManager();
        engineTrailRenderer = new EngineTrailRenderer();
        warpTimer = 0f;
        warpInterval = 10f; // Warp every 10 seconds on average
        pirateSpawnTimer = 0f;
        pirateSpawnInterval = MathUtils.random(PIRATE_WARP_IN_MIN_SECONDS, PIRATE_WARP_IN_MAX_SECONDS);
        pirateSpawnCounter = 0;

        spriteBatch = new SpriteBatch();
        spriteBatch.setProjectionMatrix(camera.combined);
        loadAllShipSprites();
        initialize();
    }

    private void loadAllShipSprites() {
        shipDataById.clear();
        shipTemplates.clear();
        FileHandle modsDir = ModPaths.modsRoot();
        if (!modsDir.exists()) {
            return;
        }

        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) continue;
            for (FileHandle file : modFolder.list()) {
                if (!"json".equalsIgnoreCase(file.extension())) continue;
                ShipData data = ShipDataIO.loadFromJson(file);
                if (data == null || data.id == null || data.id.isBlank()) continue;
                if (data.texturePath == null || data.texturePath.isBlank()) continue;
                shipDataById.put(data.id, data);
                shipTemplates.add(data);
                obtainShipTexture(data);
            }
        }
    }

    private float shipRadiusFromShipData(ShipData data) {
        if (data == null) {
            return DEFAULT_SHIP_RADIUS;
        }
        data.normalizeOuterBounds();
        if (data.outerBounds != null && data.outerBounds.radius > 0f) {
            float reach = (float) Math.sqrt(data.outerBounds.x * data.outerBounds.x + data.outerBounds.y * data.outerBounds.y)
                + data.outerBounds.radius;
            return reach;
        }
        if (data.colliders != null) {
            float maxReach = 0f;
            for (ShipColliderCircle c : data.colliders) {
                float reach = (float) Math.sqrt(c.x * c.x + c.y * c.y) + c.radius;
                maxReach = Math.max(maxReach, reach);
            }
            if (maxReach > 0f) {
                return maxReach;
            }
        }
        Texture tex = obtainShipTexture(data);
        if (tex != null) {
            return ShipSpriteOrientation.hullRadiusFromTexture(tex.getWidth(), tex.getHeight());
        }
        return DEFAULT_SHIP_RADIUS;
    }
    
    private void initialize() {
        StarSystemData systemLayout = StarSystemDataIO.loadPreferredForGameplay();
        if (systemLayout != null && systemLayout.bodies != null && !systemLayout.bodies.isEmpty()) {
            loadBodiesFromStarSystem(systemLayout);
        } else {
            addBackgroundBody(new GameObject(
                GameObject.Type.PLANET,
                -WORLD_WIDTH * 0.25f,
                WORLD_HEIGHT * 0.5f,
                40f,
                "Planet"
            ));
            addOrbitTarget(new GameObject(
                GameObject.Type.SPACE_STATION,
                WORLD_WIDTH * 1.25f,
                WORLD_HEIGHT * 0.5f,
                50f,
                "Space Station",
                StationKind.TRADER
            ));
        }

        ensureOrbitTargetsNonEmpty();

        if (shipTemplates.isEmpty()) {
            throw new IllegalStateException("No ship templates found under mods/. Add ship JSON files before playing.");
        }
        
        for (int i = 0; i < 10; i++) {
            NpcShipRole role = pickNpcShipRole();
            GameObject spawnAnchor = pickSpawnAnchorForRole(role);
            ShipData template = shipTemplates.get(MathUtils.random(shipTemplates.size - 1));
            float shipRadius = shipRadiusFromShipData(template);
            spawnShipNearOrbitTarget(i, spawnAnchor, template, shipRadius, role);
        }

        spawnPlayerShip();
    }

    private void addOrbitTarget(GameObject obj) {
        gameObjects.add(obj);
        orbitTargets.add(obj);
    }

    /** Scenery only: drawn and updated, but not an orbit anchor or mouse target. */
    private void addBackgroundBody(GameObject obj) {
        gameObjects.add(obj);
    }

    private GameObject pickRandomOrbitTarget() {
        return orbitTargets.get(MathUtils.random(orbitTargets.size - 1));
    }

    private Array<GameObject> collectStationsOfKind(StationKind kind) {
        Array<GameObject> matches = new Array<>();
        for (GameObject obj : orbitTargets) {
            if (obj.isStationOfKind(kind)) {
                matches.add(obj);
            }
        }
        return matches;
    }

    private int countStationsOfKind(StationKind kind) {
        int count = 0;
        for (GameObject obj : orbitTargets) {
            if (obj.isStationOfKind(kind)) {
                count++;
            }
        }
        return count;
    }

    private GameObject pickRandomStationOfKind(StationKind kind) {
        Array<GameObject> matches = collectStationsOfKind(kind);
        if (matches.isEmpty()) {
            return pickRandomOrbitTarget();
        }
        return matches.get(MathUtils.random(matches.size - 1));
    }

    private Array<GameObject> stationsForRole(NpcShipRole role) {
        StationKind kind = stationKindForRole(role);
        Array<GameObject> matches = collectStationsOfKind(kind);
        if (matches.isEmpty()) {
            return new Array<>(orbitTargets);
        }
        return matches;
    }

    private static StationKind stationKindForRole(NpcShipRole role) {
        return switch (role) {
            case TRADER -> StationKind.TRADER;
            case MILITIA -> StationKind.MILITIA;
            case CIVILIAN -> StationKind.CIVILIAN;
        };
    }

    private GameObject pickSpawnAnchorForRole(NpcShipRole role) {
        return pickRandomStationOfKind(stationKindForRole(role));
    }

    private NpcShipRole pickNpcShipRole() {
        if (orbitTargets.size >= 2 && MathUtils.random() < TRADER_SHIP_FRACTION) {
            return NpcShipRole.TRADER;
        }
        return MathUtils.randomBoolean() ? NpcShipRole.MILITIA : NpcShipRole.CIVILIAN;
    }

    private void spawnShipNearOrbitTarget(
        int index,
        GameObject orbitTarget,
        ShipData template,
        float shipRadius,
        NpcShipRole role
    ) {
        float angleDeg = MathUtils.random(0f, 360f);
        float minDist = orbitTarget.getSize() + shipRadius + SHIP_SPAWN_MIN_CLEARANCE;
        float maxDist = minDist + SHIP_SPAWN_MAX_EXTRA_RADIUS;
        float dist = MathUtils.random(minDist, maxDist);
        float x = orbitTarget.getX() + MathUtils.cosDeg(angleDeg) * dist;
        float y = orbitTarget.getY() + MathUtils.sinDeg(angleDeg) * dist;

        String rolePrefix = switch (role) {
            case TRADER -> "Trader";
            case MILITIA -> "Militia";
            case CIVILIAN -> "Civilian";
        };
        SpaceShip ship = new SpaceShip(
            x,
            y,
            shipRadius,
            rolePrefix + " " + (index + 1) + " (" + template.id + ")",
            orbitTarget,
            template.speed
        );
        ship.configureFromShipData(template);
        ship.setHullSprite(createHullSprite(template));
        ship.configureWeaponInstances(buildWeaponInstances(template));
        switch (role) {
            case TRADER -> ship.configureAsTrader(orbitTargets);
            case MILITIA -> ship.configureAsMilitiaPatrol(stationsForRole(NpcShipRole.MILITIA));
            case CIVILIAN -> ship.configureAsCivilian(orbitTarget);
        }

        spaceShips.add(ship);
        gameObjects.add(ship);
        GameUtils.addSpaceShip(ship);
    }

    private ShipData pickPlayerShipTemplate() {
        ShipData fighter = shipDataById.get("fighter_2");
        if (fighter != null) {
            return fighter;
        }
        return shipTemplates.first();
    }

    private void spawnPlayerShip() {
        ShipData template = pickPlayerShipTemplate();
        GameObject anchor = pickRandomOrbitTarget();
        float shipRadius = shipRadiusFromShipData(template);

        float angleDeg = MathUtils.random(0f, 360f);
        float minDist = anchor.getSize() + shipRadius + SHIP_SPAWN_MIN_CLEARANCE;
        float maxDist = minDist + SHIP_SPAWN_MAX_EXTRA_RADIUS;
        float dist = MathUtils.random(minDist, maxDist);
        float x = anchor.getX() + MathUtils.cosDeg(angleDeg) * dist;
        float y = anchor.getY() + MathUtils.sinDeg(angleDeg) * dist;

        playerShip = new SpaceShip(
            x,
            y,
            shipRadius,
            "Player (" + template.id + ")",
            anchor,
            template.speed
        );
        playerShip.configureFromShipData(template);
        playerShip.setHullSprite(createHullSprite(template));
        playerShip.configureWeaponInstances(buildWeaponInstances(template));
        playerShip.configureAsPlayer(anchor);

        spaceShips.add(playerShip);
        gameObjects.add(playerShip);
        GameUtils.addSpaceShip(playerShip);
    }

    public SpaceShip getPlayerShip() {
        return playerShip;
    }

    private void trySpawnPirateWarpIn() {
        if (shipTemplates.isEmpty() || orbitTargets.isEmpty()) {
            return;
        }
        if (countActivePirates() >= MAX_ACTIVE_PIRATES) {
            return;
        }

        GameObject anchor = pickRandomOrbitTarget();
        ShipData template = shipTemplates.get(MathUtils.random(shipTemplates.size - 1));
        float shipRadius = shipRadiusFromShipData(template);

        pirateSpawnCounter++;
        SpaceShip ship = new SpaceShip(
            0f,
            0f,
            shipRadius,
            "Pirate " + pirateSpawnCounter + " (" + template.id + ")",
            anchor,
            template.speed
        );
        ship.configureFromShipData(template);
        ship.setHullSprite(createHullSprite(template));
        ship.configureWeaponInstances(buildWeaponInstances(template));
        ship.configureAsPirate(anchor);
        ship.beginWarpInNear(anchor);

        spaceShips.add(ship);
        gameObjects.add(ship);
        GameUtils.addSpaceShip(ship);
    }

    private int countActivePirates() {
        int count = 0;
        for (SpaceShip ship : spaceShips) {
            if (ship.isPirate()) {
                count++;
            }
        }
        return count;
    }

    private SpaceShip pickRandomNonPirateShip() {
        if (spaceShips.isEmpty()) {
            return null;
        }
        for (int attempt = 0; attempt < spaceShips.size; attempt++) {
            SpaceShip ship = spaceShips.get(MathUtils.random(spaceShips.size - 1));
            if (!ship.isPirate() && !ship.isPlayerControlled()) {
                return ship;
            }
        }
        return null;
    }

    private void ensureOrbitTargetsNonEmpty() {
        if (!orbitTargets.isEmpty()) {
            return;
        }
        addOrbitTarget(new GameObject(
            GameObject.Type.SPACE_STATION,
            WORLD_WIDTH * 1.25f,
            WORLD_HEIGHT * 0.5f,
            50f,
            "Space Station",
            StationKind.TRADER
        ));
    }

    private void loadBodiesFromStarSystem(StarSystemData systemLayout) {
        orbitTargets.clear();

        for (StarSystemBody body : systemLayout.bodies) {
            body.normalize();
            GameObject.Type type = body.type == StarSystemBody.Kind.SPACE_STATION
                ? GameObject.Type.SPACE_STATION
                : GameObject.Type.PLANET;
            GameObject obj = type == GameObject.Type.SPACE_STATION
                ? new GameObject(type, body.x, body.y, body.size, body.name, body.stationKind)
                : new GameObject(type, body.x, body.y, body.size, body.name);
            if (type == GameObject.Type.PLANET) {
                addBackgroundBody(obj);
            } else {
                addOrbitTarget(obj);
            }
        }

        ensureOrbitTargetsNonEmpty();
        loadAsteroidBelts(systemLayout);
    }

    private void loadAsteroidBelts(StarSystemData systemLayout) {
        if (systemLayout.asteroidBelts == null) {
            return;
        }
        for (StarSystemAsteroidBelt belt : systemLayout.asteroidBelts) {
            belt.normalize();
            Array<GameObject> spawned = new Array<>();
            AsteroidBeltGenerator.populate(belt, spawned);
            for (GameObject asteroid : spawned) {
                addBackgroundBody(asteroid);
            }
        }
    }

    private GameObject firstBodyOfType(GameObject.Type type) {
        for (GameObject obj : gameObjects) {
            if (obj.getType() == type) {
                return obj;
            }
        }
        return null;
    }

    private Array<ShipWeaponInstance> buildWeaponInstances(ShipData data) {
        Array<ShipWeaponInstance> out = new Array<>();
        if (data == null || data.weaponSlots == null) return out;
        for (WeaponSlot slot : data.weaponSlots) {
            if (slot.equippedWeaponId == null || slot.equippedWeaponId.isBlank()) continue;
            WeaponData wd = WeaponDataIO.loadById(slot.equippedWeaponId);
            if (wd == null || !wd.canEquipOn(slot.type)) continue;
            ShipWeaponInstance inst = new ShipWeaponInstance(slot, wd);
            if (wd.turretSprite != null && !wd.turretSprite.isBlank()) {
                Texture tex = obtainWeaponTexture(wd.turretSprite);
                if (tex != null) {
                    inst.textureRef = tex;
                    Sprite turretSprite = new Sprite(tex);
                    ShipSpriteOrientation.applyWeaponLayout(turretSprite);
                    inst.sprite = turretSprite;
                }
            }
            inst.aimAngleDeg = 0f;
            out.add(inst);
        }
        return out;
    }

    private Texture obtainShipTexture(ShipData shipData) {
        if (shipData == null || shipData.id == null || shipData.id.isBlank()) return null;
        Texture cached = shipTextureCache.get(shipData.id);
        if (cached != null) return cached;

        FileHandle resolvedTexture = ModPaths.resolveLocal(shipData.texturePath);
        if (!resolvedTexture.exists()) {
            resolvedTexture = com.badlogic.gdx.Gdx.files.internal("images/" + shipData.id + ".png");
        }
        if (!resolvedTexture.exists()) {
            return null;
        }

        Texture texture = new Texture(resolvedTexture);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        shipTextureCache.put(shipData.id, texture);
        return texture;
    }

    private Sprite createHullSprite(ShipData shipData) {
        Texture texture = obtainShipTexture(shipData);
        if (texture == null) {
            return null;
        }

        Sprite sprite = new Sprite(texture);
        ShipSpriteOrientation.applyHullLayout(
            sprite,
            shipData.centerOfMass.x,
            shipData.centerOfMass.y,
            texture.getWidth(),
            texture.getHeight()
        );
        return sprite;
    }

    private Texture obtainWeaponTexture(String path) {
        Texture cached = weaponTextureCache.get(path);
        if (cached != null) return cached;
        FileHandle fh = WeaponDataIO.resolveTextureFile(path);
        if (fh == null || !fh.exists()) return null;
        Texture tex = new Texture(fh);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        weaponTextureCache.put(path, tex);
        return tex;
    }
    
    public void update(float deltaTime) {
        // Update space ships (they handle their own behavior)
        for (SpaceShip ship : spaceShips) {
            ship.update(deltaTime, projectileManager);
        }
        projectileManager.update(deltaTime, spaceShips);
        engineTrailRenderer.update(deltaTime, spaceShips);
        
        // Update other game objects
        for (GameObject obj : gameObjects) {
            if (!(obj instanceof SpaceShip)) {
                obj.update(deltaTime);
            }
        }
        
        // Occasionally trigger warp out for non-pirate ships (test / ambient traffic)
        warpTimer += deltaTime;
        if (warpTimer >= warpInterval) {
            warpTimer = 0f;

            if (MathUtils.randomBoolean(1.0f)) {
                SpaceShip ship = pickRandomNonPirateShip();
                if (ship != null) {
                    ship.triggerWarpOut();
                }
            }
        }

        pirateSpawnTimer += deltaTime;
        if (pirateSpawnTimer >= pirateSpawnInterval) {
            pirateSpawnTimer = 0f;
            pirateSpawnInterval = MathUtils.random(PIRATE_WARP_IN_MIN_SECONDS, PIRATE_WARP_IN_MAX_SECONDS);
            trySpawnPirateWarpIn();
        }

        updateCamera(deltaTime);
    }

    private void updateCamera(float deltaTime) {
        float targetX = DEFAULT_CAMERA_X;
        float targetY = DEFAULT_CAMERA_Y;
        SpaceShip followTarget = getCameraFollowShip();
        if (followTarget != null) {
            targetX = followTarget.getX();
            targetY = followTarget.getY();
            camera.position.x = targetX;
            camera.position.y = targetY;
        } else {
            float t = MathUtils.clamp(CAMERA_FOLLOW_SPEED * deltaTime, 0f, 1f);
            camera.position.x = MathUtils.lerp(camera.position.x, targetX, t);
            camera.position.y = MathUtils.lerp(camera.position.y, targetY, t);
            snapCameraToPixelGrid();
        }

        refreshCameraProjection();
    }

    private SpaceShip getCameraFollowShip() {
        if (playerShip != null) {
            return playerShip;
        }
        if (cameraFollowTarget != null && selectedObject == cameraFollowTarget) {
            return cameraFollowTarget;
        }
        return null;
    }

    private boolean isFollowingShip() {
        return getCameraFollowShip() != null;
    }

    private void updateWorldPerPixelScales() {
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        if (screenW <= 0 || screenH <= 0) {
            worldPerPixelX = 1f;
            worldPerPixelY = 1f;
            return;
        }
        worldPerPixelX = camera.viewportWidth * camera.zoom / screenW;
        worldPerPixelY = camera.viewportHeight * camera.zoom / screenH;
    }

    private float snapWorldX(float worldX) {
        if (worldPerPixelX <= 0f) {
            return worldX;
        }
        return Math.round(worldX / worldPerPixelX) * worldPerPixelX;
    }

    private float snapWorldY(float worldY) {
        if (worldPerPixelY <= 0f) {
            return worldY;
        }
        return Math.round(worldY / worldPerPixelY) * worldPerPixelY;
    }

    private void snapWorldPoint(float worldX, float worldY, Vector2 out) {
        out.x = snapWorldX(worldX);
        out.y = snapWorldY(worldY);
    }

    private static float snapRenderAngle(float gameAngleDeg) {
        if (RENDER_ANGLE_SNAP_DEG <= 0f) {
            return gameAngleDeg;
        }
        return Math.round(gameAngleDeg / RENDER_ANGLE_SNAP_DEG) * RENDER_ANGLE_SNAP_DEG;
    }

    /** Align the camera to whole screen pixels so nearest-filtered sprites do not shimmer. */
    private void snapCameraToPixelGrid() {
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        if (screenW <= 0 || screenH <= 0) {
            return;
        }
        float worldPerPixelX = camera.viewportWidth * camera.zoom / screenW;
        float worldPerPixelY = camera.viewportHeight * camera.zoom / screenH;
        if (worldPerPixelX > 0f) {
            camera.position.x = Math.round(camera.position.x / worldPerPixelX) * worldPerPixelX;
        }
        if (worldPerPixelY > 0f) {
            camera.position.y = Math.round(camera.position.y / worldPerPixelY) * worldPerPixelY;
        }
    }

    private void refreshCameraProjection() {
        updateWorldPerPixelScales();
        camera.update();
        spriteBatch.setProjectionMatrix(camera.combined);
    }
    
    public void adjustZoom(float scrollAmount) {
        if (scrollAmount == 0f) {
            return;
        }
        cameraZoom = MathUtils.clamp(cameraZoom + scrollAmount * ZOOM_SCROLL_STEP, MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM);
        camera.zoom = cameraZoom;
        if (!isFollowingShip()) {
            snapCameraToPixelGrid();
        }
        refreshCameraProjection();
    }
    
    public void render() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        spriteBatch.setProjectionMatrix(camera.combined);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        for (GameObject obj : gameObjects) {
            if (obj instanceof SpaceShip && !((SpaceShip) obj).isVisible()) {
                continue;
            }
            if (obj.getType() != GameObject.Type.PLANET) {
                continue;
            }
            shapeRenderer.setColor(Color.ORANGE);
            shapeRenderer.circle(obj.getX(), obj.getY(), obj.getSize());
        }
        for (GameObject obj : gameObjects) {
            if (obj instanceof SpaceShip && !((SpaceShip) obj).isVisible()) {
                continue;
            }
            if (obj.getType() != GameObject.Type.ASTEROID) {
                continue;
            }
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.circle(obj.getX(), obj.getY(), obj.getSize());
        }
        for (GameObject obj : gameObjects) {
            if (obj instanceof SpaceShip && !((SpaceShip) obj).isVisible()) {
                continue;
            }
            if (obj.getType() != GameObject.Type.SPACE_STATION) {
                continue;
            }
            shapeRenderer.setColor(obj.getStationKind().displayColor);
            float halfSize = obj.getSize() / 2f;
            shapeRenderer.rect(obj.getX() - halfSize, obj.getY() - halfSize,
                obj.getSize(), obj.getSize());
        }
        projectileManager.render(shapeRenderer);
        shapeRenderer.end();

        spriteBatch.begin();
        SpaceShip pinnedShip = getCameraFollowShip();
        for (GameObject obj : gameObjects) {
            if (!(obj instanceof SpaceShip ship) || !ship.isVisible()) {
                continue;
            }

            boolean pinned = ship == pinnedShip;
            Sprite hullSprite = ship.getHullSprite();
            if (hullSprite != null) {
                if (pinned) {
                    renderSnapScratch.set(obj.getX(), obj.getY());
                } else {
                    snapWorldPoint(obj.getX(), obj.getY(), renderSnapScratch);
                }
                hullSprite.setPosition(
                    renderSnapScratch.x - hullSprite.getOriginX(),
                    renderSnapScratch.y - hullSprite.getOriginY()
                );
                hullSprite.setRotation(
                    ShipSpriteOrientation.gameAngleToSpriteRotation(snapRenderAngle(obj.getRotation()))
                );
                hullSprite.draw(spriteBatch);
            }

            for (ShipWeaponInstance w : ship.getWeaponInstances()) {
                if (w.sprite == null) continue;

                if (pinned) {
                    renderSnapScratch.set(w.worldPosCache.x, w.worldPosCache.y);
                } else {
                    snapWorldPoint(w.worldPosCache.x, w.worldPosCache.y, renderSnapScratch);
                }
                w.sprite.setCenter(renderSnapScratch.x, renderSnapScratch.y);
                w.sprite.setRotation(
                    ShipSpriteOrientation.gameAngleToSpriteRotation(snapRenderAngle(w.aimAngleDeg))
                );
                w.sprite.draw(spriteBatch);
            }
        }
        spriteBatch.end();

        engineTrailRenderer.render(camera);

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        drawSelectionIndicators();
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        drawShipClickBoundsOutlines(pinnedShip);
        drawPlayerTargetIndicator(pinnedShip);
        drawPinnedOrbitTargetIndicator();
        shapeRenderer.end();
    }

    private void drawPlayerTargetIndicator(SpaceShip pinnedShip) {
        if (playerShip == null) {
            return;
        }
        GameObject target = playerShip.getPlayerTarget();
        if (target == null || !isIndicatorVisible(target)) {
            return;
        }
        drawSelectionSquare(target, pinnedShip);
    }

    private void drawSelectionSquare(GameObject obj, SpaceShip pinnedShip) {
        shapeRenderer.setColor(1f, 0.85f, 0.2f, 1f);

        if (obj instanceof SpaceShip ship) {
            float half = ship.getClickBoundsRadius() + SELECTION_RING_PADDING;
            ship.writeClickBoundsWorldCenter(renderSnapScratch);
            float cx = renderSnapScratch.x;
            float cy = renderSnapScratch.y;
            if (!(ship == pinnedShip && isFollowingShip())) {
                snapWorldPoint(cx, cy, renderSnapScratch);
                cx = renderSnapScratch.x;
                cy = renderSnapScratch.y;
            }
            shapeRenderer.rect(cx - half, cy - half, half * 2f, half * 2f);
            return;
        }

        float half = obj.getSize() * 0.5f + SELECTION_RING_PADDING;
        snapWorldPoint(obj.getX(), obj.getY(), renderSnapScratch);
        float cx = renderSnapScratch.x;
        float cy = renderSnapScratch.y;
        shapeRenderer.rect(cx - half, cy - half, half * 2f, half * 2f);
    }

    private void drawShipClickBoundsOutlines(SpaceShip pinnedShip) {
        boolean following = isFollowingShip();
        for (GameObject obj : gameObjects) {
            if (!(obj instanceof SpaceShip ship) || !ship.isVisible()) {
                continue;
            }
            if (ship.isTrader()) {
                shapeRenderer.setColor(1f, 1f, 1f, 0.9f);
            } else if (ship.isMilitiaPatrol()) {
                shapeRenderer.setColor(0.35f, 0.55f, 1f, 0.9f);
            } else if (ship.isCivilian()) {
                shapeRenderer.setColor(0.55f, 0.9f, 0.45f, 0.9f);
            } else if (ship.isPirate()) {
                shapeRenderer.setColor(1f, 0.3f, 0.3f, 0.9f);
            } else {
                continue;
            }

            float radius = ship.getClickBoundsRadius();
            ship.writeClickBoundsWorldCenter(renderSnapScratch);
            float cx = renderSnapScratch.x;
            float cy = renderSnapScratch.y;

            boolean pinned = ship == pinnedShip && following;
            if (!pinned) {
                snapWorldPoint(cx, cy, renderSnapScratch);
                cx = renderSnapScratch.x;
                cy = renderSnapScratch.y;
            }

            shapeRenderer.circle(cx, cy, radius);
        }
    }

    private void drawSelectionIndicators() {
        if (selectedObject != null) {
            drawSelectionRing(selectedObject, Color.GREEN);
        }

        if (selectedObject instanceof SpaceShip selectedShip) {
            GameObject combatTarget = selectedShip.getCombatTarget();
            if (combatTarget != null && isIndicatorVisible(combatTarget)) {
                drawSelectionRing(combatTarget, Color.RED);
            }
        }
    }

    /** Short line from the camera-pinned ship toward its patrol orbit target (station). */
    private void drawPinnedOrbitTargetIndicator() {
        SpaceShip followShip = getCameraFollowShip();
        if (followShip == null || followShip.isPlayerControlled()) {
            return;
        }

        GameObject orbitTarget = followShip.getOrbitTarget();
        if (orbitTarget == null) {
            return;
        }

        float sx = followShip.getX();
        float sy = followShip.getY();
        float dx = orbitTarget.getX() - sx;
        float dy = orbitTarget.getY() - sy;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        if (distance < 0.001f) {
            return;
        }

        float lineLength = Math.min(ORBIT_TARGET_DEBUG_LINE_LENGTH, distance * 0.35f);
        float ex = sx + (dx / distance) * lineLength;
        float ey = sy + (dy / distance) * lineLength;

        shapeRenderer.setColor(0.45f, 0.9f, 1f, 1f);
        shapeRenderer.line(sx, sy, ex, ey);
    }

    private static boolean isIndicatorVisible(GameObject obj) {
        return !(obj instanceof SpaceShip ship) || ship.isVisible();
    }

    private void drawSelectionRing(GameObject obj, Color color) {
        shapeRenderer.setColor(color);
        if (obj instanceof SpaceShip ship && ship == cameraFollowTarget && isFollowingShip()) {
            shapeRenderer.circle(obj.getX(), obj.getY(), SELECTION_RING_PADDING);
        } else {
            snapWorldPoint(obj.getX(), obj.getY(), renderSnapScratch);
            shapeRenderer.circle(renderSnapScratch.x, renderSnapScratch.y, SELECTION_RING_PADDING);
        }
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
        
        // Keep viewport/zoom in sync; camera position is driven by updateCamera().
        camera.zoom = cameraZoom;
        if (!isFollowingShip()) {
            snapCameraToPixelGrid();
        }
        refreshCameraProjection();
    }

    public OrthographicCamera getCamera() {
        return camera;
    }
    
    private void drawRotatedTriangle(float x, float y, float size, float rotation) {
        // Calculate triangle vertices (pointing in direction of rotation)
        float[] vertices = new float[6]; // 3 vertices * 2 coordinates
        
        // Base triangle vertices (before rotation)
        float radius = size;
        for (int i = 0; i < 3; i++) {
            float angle = (float) Math.toRadians(rotation + i * 120f - 90f); // -90 to point up initially
            vertices[i * 2] = x + radius * (float) Math.cos(angle);
            vertices[i * 2 + 1] = y + radius * (float) Math.sin(angle);
        }
        
        // Draw triangle using ShapeRenderer
        shapeRenderer.triangle(vertices[0], vertices[1], 
                              vertices[2], vertices[3], 
                              vertices[4], vertices[5]);
    }
    
    public GameObject getObjectAt(float x, float y) {
        // Check objects in reverse order (top-most first)
        for (int i = gameObjects.size - 1; i >= 0; i--) {
            GameObject obj = gameObjects.get(i);
            if (obj.isInteractable() && obj.containsPoint(x, y)) {
                return obj;
            }
        }
        return null;
    }

    public void drawClickDebugIndicator(float x, float y, GameObject clicked) {
        setSelectedObject(clicked);
    }

    public void setSelectedObject(GameObject obj) {
        selectedObject = obj;
        cameraFollowTarget = obj instanceof SpaceShip ship ? ship : null;
    }

    public GameObject getPlanet() {
        return firstBodyOfType(GameObject.Type.PLANET);
    }
    
    public GameObject getSpaceStation() {
        return firstBodyOfType(GameObject.Type.SPACE_STATION);
    }

    /** Stations only; planets are background scenery and are not orbit anchors. */
    public Array<GameObject> getOrbitTargets() {
        return orbitTargets;
    }
    
    public void dispose() {
        GameUtils.clearSpaceShips();
        shapeRenderer.dispose();
        spriteBatch.dispose();
        for (Texture t : shipTextureCache.values()) {
            if (t != null) t.dispose();
        }
        shipTextureCache.clear();
        for (Texture t : weaponTextureCache.values()) {
            if (t != null) t.dispose();
        }
        weaponTextureCache.clear();
        if (engineTrailRenderer != null) {
            engineTrailRenderer.dispose();
            engineTrailRenderer = null;
        }
    }
}
