package io.github.cygnus_engine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    // UI components
    private Stage stage;
    private Skin skin;
    private Cargo cargo;
    private Window mainWindow;
    private TradeScreen tradeScreen;
    private CargoMenuScreen cargoMenuScreen;
    private GameObjectInfoWindow objectInfoWindow;
    private int money = 1000; // Starting money

    // Game world
    private GameWorld gameWorld;

    /**
     * Creates a draggable window, centers it on the stage, and adds it to the stage.
     * @param window The window to add and center
     * @param stage The stage to add the window to
     */
    private void createAndCenterWindow(Window window, Stage stage) {
        window.setMovable(true);
        window.pack();
        window.setPosition(
            MathUtils.roundPositive(stage.getWidth() / 2f - window.getWidth() / 2f),
            MathUtils.roundPositive(stage.getHeight() / 2f - window.getHeight() / 2f)
        );
        stage.addActor(window);
    }

    @Override
    public void create() {
        stage = new Stage(new ScreenViewport());
        skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        
        // Initialize game world
        gameWorld = new GameWorld();

        // Initialize cargo
        cargo = new Cargo();

        // Create main menu window
        mainWindow = new Window("Main Menu", skin, "border");
        mainWindow.defaults().pad(4f);
        mainWindow.add("Welcome to the Trading Game").row();
        
        // Trade button
        TextButton tradeButton = new TextButton("Open Trade Screen", skin);
        tradeButton.pad(8f);
        tradeButton.addListener(new ChangeListener() {
            @Override
            public void changed(final ChangeEvent event, final Actor actor) {
                if (tradeScreen == null) {
                    tradeScreen = new TradeScreen(skin, cargo, money);
                    createAndCenterWindow(tradeScreen, stage);
                } else {
                    // Update money in case it changed
                    if (tradeScreen.getMoney() != money) {
                        tradeScreen.remove();
                        tradeScreen = new TradeScreen(skin, cargo, money);
                        createAndCenterWindow(tradeScreen, stage);
                    }
                    tradeScreen.setVisible(true);
                }
            }
        });
        mainWindow.add(tradeButton).row();

        // Cargo menu button
        TextButton cargoButton = new TextButton("View Cargo", skin);
        cargoButton.pad(8f);
        cargoButton.addListener(new ChangeListener() {
            @Override
            public void changed(final ChangeEvent event, final Actor actor) {
                if (cargoMenuScreen == null) {
                    cargoMenuScreen = new CargoMenuScreen(skin, cargo);
                    createAndCenterWindow(cargoMenuScreen, stage);
                } else {
                    cargoMenuScreen.refresh();
                    cargoMenuScreen.setVisible(true);
                }
            }
        });
        mainWindow.add(cargoButton);
        
        // Center the main window
        mainWindow.addAction(Actions.sequence(Actions.alpha(0f), Actions.fadeIn(1f)));
        createAndCenterWindow(mainWindow, stage);

        // Set up input handling - game world clicks first, then UI
        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                
                //float worldX = screenX;
                //float worldY = clientY - screenY;
                // convert to percentage
                //float worldXDivided = worldX / clientX;
                //float worldYDivided = worldY / clientY;
                //System.out.println("worldXDivided: " + worldXDivided + " worldYDivided: " + worldYDivided);

                // if the viewport size increases, then the clicked x coordinate will be more in the center (to the right)
                // but why is the clicked y coordinate stable?
                // because objects are not moved up in the viewport, they are still centered, so y is stable
                
                // the true x,y world coordinate doesn't matter in my calculation - what matters is the padding
                // how do I get world coordinate from the camera?

                // camera viewportWidth and viewportHeight are clientX and clientY
                // WORLD_WIDTH and WORLD_HEIGHT are fixed

                // I should not use screen/viewport coordinates.

                // world size will always be the same.
                // camera position/scale might change.
                // the camera will always be centered.
                // the world will not always be centered with the camera
                

                // to get world position clicked, find the offset from the camera position,
                // based on the camera scale

                // for now, assume camera position is unchanging
                // camera scale/viewport is the same as clientX/clientY

                // in an unscaled viewport, the it goes from 0 to WORLD_WIDTH, and 0 to WORLD_HEIGHT
                // in a scaled viewport, the world coordinates are the same

                // even in a normal viewport, the click indicator is not where the user clicked
                // the click will scaled based on the viewport size
                // if a normal viewport, it'll be scaled out from the click location
                // if scaled out viewport, the click location will scaled in

                
                // Convert screen coordinates (Y is inverted)
                float clientX = Gdx.graphics.getWidth();
                float clientY = Gdx.graphics.getHeight();

                Vector3 clickedPos = new Vector3(screenX, screenY, 0);
                Vector3 unprojected = gameWorld.getCamera().unproject(clickedPos, 0.0f, 0.0f, clientX, clientY);
                // WHOOO THIS WORKS (unproject)

                // Check if we clicked on a game object
                GameObject clickedObject = gameWorld.getObjectAt(unprojected.x, unprojected.y);
                gameWorld.drawClickDebugIndicator(unprojected.x, unprojected.y);
                if (clickedObject != null) {
                    showObjectInfo(clickedObject);
                    return true; // Consume the event
                }
                return false; // Let UI handle it
            }
        });
        inputMultiplexer.addProcessor(stage);
        Gdx.input.setInputProcessor(inputMultiplexer);
    }
    
    private void showObjectInfo(GameObject gameObject) {
        if (objectInfoWindow != null) {
            objectInfoWindow.remove();
        }
        objectInfoWindow = new GameObjectInfoWindow(skin, gameObject);
        createAndCenterWindow(objectInfoWindow, stage);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        
        float deltaTime = Gdx.graphics.getDeltaTime();
        
        // Update game world
        gameWorld.update(deltaTime);
        
        // Draw game world
        gameWorld.render();
        
        // Draw UI
        stage.act(deltaTime);
        stage.draw();
        
        // Update money from trade screen if it exists and is visible
        if (tradeScreen != null && tradeScreen.isVisible()) {
            money = tradeScreen.getMoney();
        }
    }

    @Override
    public void resize(int width, int height) {
        // If the window is minimized on a desktop (LWJGL3) platform, width and height are 0, which causes problems.
        // In that case, we don't resize anything, and wait for the window to be a normal size before updating.
        if(width <= 0 || height <= 0) return;

        stage.getViewport().update(width, height, true);
        gameWorld.resize(width, height);
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        gameWorld.dispose();
    }
}