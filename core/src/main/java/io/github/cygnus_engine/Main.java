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

// todo:
// add a UI to show current ship info (hp) or cargo or hail the closest ship/planet/station
// the main menu screen should be turned into a static UI element, with buttons to open up the other screens.
// keybinds to open inventory screen.

// long-term:

// a lite ship modding system that allows defining the extents of a ship
// add a ship modding system, which allows importing a texture and/or existing JSON
// defining the bounds of the ship, and drawing weapon/engine points and center-of-mass

// add a minimap/radar
// add a star-system UI to jump to another area, and include a procgen system map
// add a dynamic faction system
// have a FTL-like text screen which allows handling "events"

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    // Screen states
    private enum ScreenState {
        MAIN_MENU, PLAY, MODDING
    }
    
    private ScreenState currentScreen = ScreenState.MAIN_MENU;
    
    // UI components
    private Stage stage;
    private Skin skin;
    private Cargo cargo;
    private CargoMenuScreen cargoMenuScreen;
    private GameObjectInfoWindow objectInfoWindow;
    private int[] moneyRef = {1000}; // Starting money
    
    // Screen windows
    private MainMenuScreen mainMenuScreen;
    private ModdingScreen moddingScreen;
    private Window gameplayWindow;

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
        cargo = new Cargo(false);

        // Create main menu screen
        mainMenuScreen = new MainMenuScreen(skin, new MainMenuScreen.ScreenListener() {
            @Override
            public void onPlayPressed() {
                switchToPlayScreen();
            }

            @Override
            public void onModdingPressed() {
                switchToModdingScreen();
            }

            @Override
            public void onExitPressed() {
                Gdx.app.exit();
            }
        });

        createAndCenterWindow(mainMenuScreen, stage);

        // Set up input handling - game world clicks first, then UI
        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                // Only handle game world clicks when in play screen
                if (currentScreen != ScreenState.PLAY) {
                    return false;
                }
                
                // Convert screen coordinates (Y is inverted)
                float clientX = Gdx.graphics.getWidth();
                float clientY = Gdx.graphics.getHeight();

                Vector3 clickedPos = new Vector3(screenX, screenY, 0);
                Vector3 unprojected = gameWorld.getCamera().unproject(clickedPos, 0.0f, 0.0f, clientX, clientY);

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
    
    private void switchToPlayScreen() {
        // Clear stage and show gameplay
        stage.clear();
        currentScreen = ScreenState.PLAY;
        
        // Create exit button for gameplay
        gameplayWindow = new Window("Gameplay", skin, "border");
        gameplayWindow.defaults().pad(4f);
        gameplayWindow.add("Welcome to the Trading Game").row();

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
        gameplayWindow.add(cargoButton);

        // Exit to menu button
        TextButton exitButton = new TextButton("Exit to Menu", skin);
        exitButton.pad(8f);
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(final ChangeEvent event, final Actor actor) {
                switchToMainMenu();
            }
        });
        gameplayWindow.add(exitButton);
        
        gameplayWindow.addAction(Actions.sequence(Actions.alpha(0f), Actions.fadeIn(1f)));
        createAndCenterWindow(gameplayWindow, stage);
    }
    
    private void switchToModdingScreen() {
        stage.clear();
        currentScreen = ScreenState.MODDING;
        
        moddingScreen = new ModdingScreen(skin, new ModdingScreen.ScreenListener() {
            @Override
            public void onBackPressed() {
                switchToMainMenu();
            }
        });
        createAndCenterWindow(moddingScreen, stage);
    }
    
    private void switchToMainMenu() {
        stage.clear();
        currentScreen = ScreenState.MAIN_MENU;
        
        // Reset menu screen
        mainMenuScreen = new MainMenuScreen(skin, new MainMenuScreen.ScreenListener() {
            @Override
            public void onPlayPressed() {
                switchToPlayScreen();
            }

            @Override
            public void onModdingPressed() {
                switchToModdingScreen();
            }

            @Override
            public void onExitPressed() {
                Gdx.app.exit();
            }
        });
        createAndCenterWindow(mainMenuScreen, stage);
    }
    
    private void showObjectInfo(GameObject gameObject) {
        if (objectInfoWindow != null) {
            objectInfoWindow.remove();
        }
        objectInfoWindow = new GameObjectInfoWindow(skin, gameObject, stage, cargo, moneyRef);
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
    }

    @Override
    public void resize(int width, int height) {
        // If the window is minimized on a desktop (LWJGL3) platform, width and height are 0, which causes problems.
        // In that case, we don't resize anything, and wait for the window to be a normal size before updating.
        if(width <= 0 || height <= 0) return;

        stage.getViewport().update(width, height, true);
        
        // Only resize game world when in play screen
        if (currentScreen == ScreenState.PLAY) {
            gameWorld.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
        gameWorld.dispose();
    }
}