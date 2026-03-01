package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

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

    private SpriteBatch spriteBatch; // For future use with textures and fonts
    private Texture playerTexture; // Placeholder for player ship texture
    private Sprite playerSprite; // Sprite for player ship (if using SpriteBatch)
    
    public GameWorld() {
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(WORLD_WIDTH, WORLD_HEIGHT);
        camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();
        
        gameObjects = new Array<>();
        spaceShips = new Array<>();
        warpTimer = 0f;
        warpInterval = 3f; // Warp every 10 seconds on average

        spriteBatch = new SpriteBatch();
        spriteBatch.setProjectionMatrix(camera.combined);
        playerTexture = new Texture("images/fighter.png");

        playerTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        playerSprite = new Sprite(playerTexture);
        playerSprite.setSize(20f, 20f);
        playerSprite.rotate90(false);
        playerSprite.setOriginCenter();

        initialize();
    }
    
    private void initialize() {
        // Create planet (circle) - center-left of world
        planet = new GameObject(GameObject.Type.PLANET, WORLD_WIDTH * 0.25f, WORLD_HEIGHT * 0.5f, 40f, "Planet");
        gameObjects.add(planet);
        
        // Create space station (square) - center-right of world
        spaceStation = new GameObject(GameObject.Type.SPACE_STATION, WORLD_WIDTH * 0.75f, WORLD_HEIGHT * 0.5f, 50f, "Space Station");
        gameObjects.add(spaceStation);

        debugIndicator = new GameObject(GameObject.Type.DEBUG_INDICATOR, 0, 0, 10f, "Debug Indicator");
        gameObjects.add(debugIndicator);
        
        // Create space ships
        for (int i = 0; i < 6; i++) {
            float x = MathUtils.random(100f, WORLD_WIDTH - 100f);
            float y = MathUtils.random(100f, WORLD_HEIGHT - 100f);
            SpaceShip ship = new SpaceShip(x, y, 18f, "Random Ship " + (i + 1), 
                                                        planet, spaceStation);
            spaceShips.add(ship);
            gameObjects.add(ship);
        }
    }
    
    public void update(float deltaTime) {
        // Update space ships (they handle their own behavior)
        for (SpaceShip ship : spaceShips) {
            ship.update(deltaTime);
        }
        
        // Update other game objects
        for (GameObject obj : gameObjects) {
            if (!(obj instanceof SpaceShip) && !(obj instanceof SpaceShip)) {
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

                    float halfSizeShip = obj.getSize() * 0.5f;
                    spriteBatch.begin();
                    playerSprite.setPosition(obj.getX() - halfSizeShip, obj.getY() - halfSizeShip);
                    playerSprite.setRotation(obj.getRotation());
                    playerSprite.draw(spriteBatch);
                    spriteBatch.end();

                    // the sprites do not respect viewport size!!!
                    break;
                case DEBUG_INDICATOR:
                    shapeRenderer.setColor(Color.RED);
                    shapeRenderer.circle(obj.getX(), obj.getY(), obj.getSize());
            }
        }
        
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

    public void drawClickDebugIndicator(float x, float y) {
        debugIndicator.setX(x);
        debugIndicator.setY(y);
    }

    public GameObject getPlanet() {
        return planet;
    }
    
    public GameObject getSpaceStation() {
        return spaceStation;
    }
    
    public void dispose() {
        shapeRenderer.dispose();
    }
}
