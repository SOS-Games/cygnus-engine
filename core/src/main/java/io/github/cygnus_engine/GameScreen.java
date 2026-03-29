package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class GameScreen {
    public interface ScreenListener {
        void onExitToMenu();
    }

    private ScreenListener listener;
    private Stage stage;
    private Skin skin;
    private GameWorld gameWorld;
    private Cargo cargo;
    private int[] moneyRef = {1000};
    
    private Window gameplayWindow;
    private CargoMenuScreen cargoMenuScreen;
    private GameObjectInfoWindow objectInfoWindow;

    public GameScreen(Stage stage, Skin skin, ScreenListener listener) {
        this.stage = stage;
        this.skin = skin;
        this.listener = listener;
        
        // Initialize game world and cargo only when entering the game screen
        gameWorld = new GameWorld();
        cargo = new Cargo(false);
        
        setupUI();
        setupInputHandling();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void setupUI() {
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
                    UiWindowUtils.createAndCenterWindow(cargoMenuScreen, stage);
                } else {
                    cargoMenuScreen.refresh();
                    cargoMenuScreen.setVisible(true);
                }
            }
        });
        gameplayWindow.add(cargoButton).row();

        // Exit to menu button
        TextButton exitButton = new TextButton("Exit to Menu", skin);
        exitButton.pad(8f);
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(final ChangeEvent event, final Actor actor) {
                if (GameScreen.this.listener != null) {
                    GameScreen.this.listener.onExitToMenu();
                }
            }
        });
        gameplayWindow.add(exitButton);
        
        gameplayWindow.addAction(Actions.sequence(Actions.alpha(0f), Actions.fadeIn(1f)));
        UiWindowUtils.createAndCenterWindow(gameplayWindow, stage);
    }

    private void setupInputHandling() {
        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
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

    private void showObjectInfo(GameObject gameObject) {
        if (objectInfoWindow != null) {
            objectInfoWindow.remove();
        }
        objectInfoWindow = new GameObjectInfoWindow(skin, gameObject, stage, cargo, moneyRef);
        UiWindowUtils.createAndCenterWindow(objectInfoWindow, stage);
    }

    public void update(float deltaTime) {
        gameWorld.update(deltaTime);
    }

    public void render() {
        gameWorld.render();
    }

    public void resize(int width, int height) {
        gameWorld.resize(width, height);
    }

    public void dispose() {
        gameWorld.dispose();
    }
}
