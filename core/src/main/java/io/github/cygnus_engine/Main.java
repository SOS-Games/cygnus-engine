package io.github.cygnus_engine;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

// todo:
// add a UI to show current ship info (hp) or cargo or hail the closest ship/planet/station
// keybinds to open inventory screen.

// a lite ship modding system that allows defining the extents of a ship
// add a ship modding system, which allows importing a texture and/or existing JSON
// defining the bounds of the ship, and drawing weapon/engine points and center-of-mass

// long-term:

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
    
    // Screen windows
    private MainMenuScreen mainMenuScreen;
    private ModdingScreen moddingScreen;
    
    // Game screen (initialized only when user clicks Play)
    private GameScreen gameScreen;

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

        // Set up input for menu navigation
        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(stage);
        Gdx.input.setInputProcessor(inputMultiplexer);
    }
    
    private void switchToPlayScreen() {
        stage.clear();
        currentScreen = ScreenState.PLAY;

        gameScreen = new GameScreen(stage, skin, new GameScreen.ScreenListener() {
            @Override
            public void onExitToMenu() {
                switchToMainMenu();
            }
        });
    }
    
    private void switchToModdingScreen() {
        stage.clear();
        currentScreen = ScreenState.MODDING;
        
        moddingScreen = new ModdingScreen(stage, skin, new ModdingScreen.ScreenListener() {
            @Override
            public void onBackPressed() {
                switchToMainMenu();
            }
        });
    }
    
    private void switchToMainMenu() {
        stage.clear();
        currentScreen = ScreenState.MAIN_MENU;
        
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
    
    @Override
    public void render() {
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        
        float deltaTime = Gdx.graphics.getDeltaTime();
        
        if (currentScreen == ScreenState.PLAY) {
            gameScreen.update(deltaTime);
            gameScreen.render();
        } else if (currentScreen == ScreenState.MODDING) {
            moddingScreen.update(deltaTime);
            moddingScreen.render();
        }
        
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
            gameScreen.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        stage.dispose();
    };
}