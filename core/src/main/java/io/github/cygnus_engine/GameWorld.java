package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.Gdx;

public class GameWorld {
    private static final float WORLD_WIDTH = 800f;
    private static final float WORLD_HEIGHT = 600f;
    
    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;
    private Array<GameObject> gameObjects;
    private GameObject planet;
    private GameObject spaceStation;
    private Array<SpaceShip> spaceShips;
    private float warpTimer;
    private float warpInterval;
    private GameObject debugIndicator;
    private GameObject clickedObject;

    private SpriteBatch spriteBatch; // For future use with textures and fonts
    private ShipData playerShipData;
    /** Optional second hull template for mixed NPC spawns (falls back to fighter). */
    private ShipData npcFrigateTemplate;
    private float playerShipRadius = 18f;
    private ProjectileManager projectileManager;
    private final ObjectMap<String, ShipData> shipDataById = new ObjectMap<>();
    private final ObjectMap<String, Texture> shipTextureCache = new ObjectMap<>();
    private final ObjectMap<String, Texture> weaponTextureCache = new ObjectMap<>();
    
    public GameWorld() {
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(WORLD_WIDTH, WORLD_HEIGHT);
        camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();
        
        gameObjects = new Array<>();
        spaceShips = new Array<>();
        projectileManager = new ProjectileManager();
        warpTimer = 0f;
        warpInterval = 3f; // Warp every 10 seconds on average

        spriteBatch = new SpriteBatch();
        spriteBatch.setProjectionMatrix(camera.combined);
        loadAllShipSprites();
        loadPlayerShip("fighter");
        npcFrigateTemplate = shipDataById.get("frigate");
        if (npcFrigateTemplate == null) {
            npcFrigateTemplate = playerShipData;
        }

        initialize();
    }

    private void loadAllShipSprites() {
        shipDataById.clear();
        FileHandle modsDir = Gdx.files.local("mods");
        if (!modsDir.exists()) {
            return;
        }

        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) continue;
            for (FileHandle file : modFolder.list()) {
                if (!"json".equalsIgnoreCase(file.extension())) continue;
                ShipData data = ShipDataIO.loadFromJson(file);
                if (data == null || data.id == null || data.id.isBlank()) continue;
                shipDataById.put(data.id, data);
                obtainShipTexture(data);
            }
        }
    }

    private void loadPlayerShip(String shipId) {
        playerShipData = shipDataById.get(shipId);
        if (playerShipData == null) {
            FileHandle textureFile = com.badlogic.gdx.Gdx.files.local("mods/core/" + shipId + ".png");
            playerShipData = ShipDataIO.loadOrCreateDefault(textureFile);
            if (playerShipData != null && playerShipData.id != null && !playerShipData.id.isBlank()) {
                shipDataById.put(playerShipData.id, playerShipData);
                obtainShipTexture(playerShipData);
            }
        }
        if (playerShipData == null && shipDataById.size > 0) {
            playerShipData = shipDataById.values().next();
        }
        if (playerShipData == null) {
            throw new IllegalStateException("No ship data available for player ship.");
        }

        float spriteWidth = 20f;
        float spriteHeight = 20f;
        if (playerShipData.bounds != null && playerShipData.bounds.width > 0f && playerShipData.bounds.height > 0f) {
            spriteWidth = playerShipData.bounds.width;
            spriteHeight = playerShipData.bounds.height;
        }
        playerShipRadius = Math.max(spriteWidth, spriteHeight) * 0.5f;
    }

    private static float npcRadiusFromShipData(ShipData data, float fallbackRadius) {
        if (data == null || data.bounds == null) {
            return fallbackRadius;
        }
        if (data.bounds.width > 0f && data.bounds.height > 0f) {
            return Math.max(data.bounds.width, data.bounds.height) * 0.5f;
        }
        return fallbackRadius;
    }
    
    private void initialize() {
        // Create planet (circle) - center-left of world
        planet = new GameObject(GameObject.Type.PLANET, -WORLD_WIDTH * 0.25f, WORLD_HEIGHT * 0.5f, 40f, "Planet");
        gameObjects.add(planet);
        
        // Create space station (square) - center-right of world
        spaceStation = new GameObject(GameObject.Type.SPACE_STATION, WORLD_WIDTH * 1.25f, WORLD_HEIGHT * 0.5f, 50f, "Space Station");
        gameObjects.add(spaceStation);

        debugIndicator = new GameObject(GameObject.Type.DEBUG_INDICATOR, 0, 0, 10f, "Debug Indicator");
        gameObjects.add(debugIndicator);
        
        // Create space ships
        for (int i = 0; i < 6; i++) {
            float x = MathUtils.random(100f, WORLD_WIDTH - 100f);
            float y = MathUtils.random(100f, WORLD_HEIGHT - 100f);

            GameObject orbitTarget = Math.random() > 0.5 ? planet : spaceStation;

            ShipData template = (i % 2 == 0) ? playerShipData : npcFrigateTemplate;
            float shipRadius = npcRadiusFromShipData(template, playerShipRadius);

            SpaceShip ship = new SpaceShip(
                x,
                y,
                shipRadius,
                "Ship " + (i + 1) + " (" + template.id + ")",
                orbitTarget,
                template.speed
            );
            ship.configureFromShipData(template);
            ship.setHullSprite(createHullSprite(template));
            ship.configureWeaponInstances(buildWeaponInstances(template));

            spaceShips.add(ship);
            gameObjects.add(ship);
            GameUtils.addSpaceShip(ship);
        }
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
                    turretSprite.setOriginCenter();
                    turretSprite.rotate90(true);
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

        FileHandle resolvedTexture = com.badlogic.gdx.Gdx.files.local(shipData.texturePath);
        if (!resolvedTexture.exists()) {
            resolvedTexture = com.badlogic.gdx.Gdx.files.internal("images/" + shipData.id + ".png");
        }
        if (!resolvedTexture.exists()) {
            return null;
        }

        Texture texture = new Texture(resolvedTexture);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        shipTextureCache.put(shipData.id, texture);
        return texture;
    }

    private Sprite createHullSprite(ShipData shipData) {
        Texture texture = obtainShipTexture(shipData);
        if (texture == null) {
            return null;
        }

        Sprite sprite = new Sprite(texture);
        float spriteWidth = 20f;
        float spriteHeight = 20f;
        if (shipData.bounds != null && shipData.bounds.width > 0f && shipData.bounds.height > 0f) {
            spriteWidth = shipData.bounds.width;
            spriteHeight = shipData.bounds.height;
        }
        sprite.setSize(spriteHeight, spriteWidth);
        sprite.setOrigin(
            sprite.getWidth() * 0.5f + shipData.centerOfMass.x,
            sprite.getHeight() * 0.5f + shipData.centerOfMass.y
        );
        sprite.rotate90(true);
        return sprite;
    }

    private Texture obtainWeaponTexture(String path) {
        Texture cached = weaponTextureCache.get(path);
        if (cached != null) return cached;
        FileHandle fh = WeaponDataIO.resolveTextureFile(path);
        if (fh == null || !fh.exists()) return null;
        Texture tex = new Texture(fh);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        weaponTextureCache.put(path, tex);
        return tex;
    }
    
    public void update(float deltaTime) {
        // Update space ships (they handle their own behavior)
        for (SpaceShip ship : spaceShips) {
            ship.update(deltaTime, projectileManager);
        }
        projectileManager.update(deltaTime, spaceShips);
        
        // Update other game objects
        for (GameObject obj : gameObjects) {
            if (!(obj instanceof SpaceShip)) {
                obj.update(deltaTime);
            }
        }
        
        // Occasionally trigger warp out for space ships
        warpTimer += deltaTime;
        if (warpTimer >= warpInterval) {
            //System.out.println("Warp out ...");
            warpTimer = 0f;
            warpInterval = MathUtils.random(2f, 4f);
            
            // Randomly select a ship to warp out
            if (spaceShips.size > 0 && MathUtils.randomBoolean(1.0f)) {
                System.out.println("Warp out ... selecting ship");
                int index = MathUtils.random(spaceShips.size - 1);
                spaceShips.get(index).triggerWarpOut();
            }
        }

        if (clickedObject != null) {
            drawClickDebugIndicator(clickedObject.getX(), clickedObject.getY(), clickedObject);
        }
    }
    
    public void render() {
        // Set camera projection for consistent rendering
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        
        for (GameObject obj : gameObjects) {
            // Skip invisible ships (warped out)
            if (obj instanceof SpaceShip && !((SpaceShip) obj).isVisible()) {
                continue;
            }
            
            switch (obj.getType()) {
                case PLANET:
                    shapeRenderer.setColor(Color.ORANGE);
                    shapeRenderer.circle(obj.getX(), obj.getY(), obj.getSize());
                    break;
                case SPACE_STATION:
                    shapeRenderer.setColor(Color.CYAN);
                    float halfSize = obj.getSize() / 2f;
                    shapeRenderer.rect(obj.getX() - halfSize, obj.getY() - halfSize, 
                                      obj.getSize(), obj.getSize());
                    break;
                case SPACE_SHIP:
                    
                    // uncomment to see triangle clickbox
                    //shapeRenderer.setColor(Color.GREEN);
                    //drawRotatedTriangle(obj.getX(), obj.getY(), obj.getSize(), obj.getRotation());

                    SpaceShip ship = (SpaceShip) obj;
                    Sprite hullSprite = ship.getHullSprite();
                    spriteBatch.begin();
                    if (hullSprite != null) {
                        hullSprite.setPosition(obj.getX() - hullSprite.getOriginX(), obj.getY() - hullSprite.getOriginY());
                        hullSprite.setRotation(obj.getRotation());
                        hullSprite.draw(spriteBatch);
                    }

                    for (ShipWeaponInstance w : ship.getWeaponInstances()) {
                        if (w.sprite == null) continue;

                        w.sprite.setCenter(w.worldPosCache.x, w.worldPosCache.y);
                        w.sprite.setRotation(w.aimAngleDeg);
                        w.sprite.draw(spriteBatch);
                    }
                    spriteBatch.end();

                    // the sprites do not respect viewport size!!!
                    break;
                case DEBUG_INDICATOR:
                    shapeRenderer.setColor(Color.RED);
                    shapeRenderer.circle(obj.getX(), obj.getY(), obj.getSize());
                    break;
            }
        }
        projectileManager.render(shapeRenderer);
        
        shapeRenderer.end();
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
        // why does this keep the position stable?
        // well, the camera does not move. only the viewport scales up or down
        // the stable world width/height means that the camera position is stable

        // is there any correlation between the viewport size and the world coordinates?

        // if the camera moves off from center, how would I know the world position clicked?
        // I know the camera's position. I know the viewport size. I know the world size.

        camera.update();

        // this ensures sprites do not stretch with the viewport, and instead maintain their size relative to the world coordinates
        spriteBatch.setProjectionMatrix(camera.combined);
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
            if (obj.containsPoint(x, y)) {
                return obj;
            }
        }
        return null;
    }

    public void drawClickDebugIndicator(float x, float y, GameObject clickedObject) {
        debugIndicator.setX(x);
        debugIndicator.setY(y);
        if (clickedObject != null) {
            this.clickedObject = clickedObject;
        }
    }

    public GameObject getPlanet() {
        return planet;
    }
    
    public GameObject getSpaceStation() {
        return spaceStation;
    }
    
    public void dispose() {
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
    }
}
