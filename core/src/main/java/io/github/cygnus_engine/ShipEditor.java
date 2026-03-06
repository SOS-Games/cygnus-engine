package io.github.cygnus_engine;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

public class ShipEditor {

    private static final float WORLD_WIDTH = 800f;
    private static final float WORLD_HEIGHT = 600f;

    private ShapeRenderer shapeRenderer;
    private OrthographicCamera camera;

    private SpriteBatch spriteBatch; // For future use with textures and fonts
    private Texture playerTexture; // Placeholder for player ship texture
    private Sprite playerSprite; // Sprite for player ship (if using SpriteBatch)
    
    public ShipEditor() {
        shapeRenderer = new ShapeRenderer();
        camera = new OrthographicCamera(WORLD_WIDTH, WORLD_HEIGHT);
        camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
        camera.position.set(WORLD_WIDTH / 2f, WORLD_HEIGHT / 2f, 0);
        camera.update();
        
        spriteBatch = new SpriteBatch();
        spriteBatch.setProjectionMatrix(camera.combined);
        // todo - load from mods folder
        playerTexture = new Texture("images/fighter.png");

        playerTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        playerSprite = new Sprite(playerTexture);
        playerSprite.setSize(20f, 20f);
        //playerSprite.rotate90(false);
        playerSprite.setOriginCenter();
    };

    public void update(float deltaTime) {
        
    }

    public void render() {

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

        // this ensures sprites do not stretch with the viewport, and instead maintain their size relative to the world coordinates
        spriteBatch.setProjectionMatrix(camera.combined);
    }

    public void dispose() {
    }

}