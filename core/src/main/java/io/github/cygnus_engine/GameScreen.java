package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
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
    
    private Window pauseMenuWindow;
    private CargoMenuScreen cargoMenuScreen;
    private GameObjectInfoWindow objectInfoWindow;
    private float savedInfoWindowX = Float.NaN;
    private float savedInfoWindowY = Float.NaN;
    private final Vector3 mouseWorldScratch = new Vector3();

    public GameScreen(Stage stage, Skin skin, ScreenListener listener) {
        this.stage = stage;
        this.skin = skin;
        this.listener = listener;
        
        // Initialize game world and cargo only when entering the game screen
        gameWorld = new GameWorld();
        cargo = new Cargo(false);
        SpaceShip playerShip = gameWorld.getPlayerShip();
        if (playerShip != null) {
            playerShip.setCargo(cargo);
        }
        
        setupPauseMenu();
        setupInputHandling();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void setupPauseMenu() {
        pauseMenuWindow = new Window("Paused", skin, "border");
        pauseMenuWindow.defaults().pad(4f);
        pauseMenuWindow.setMovable(true);
        pauseMenuWindow.setVisible(false);

        TextButton exitButton = new TextButton("Exit to Main Menu", skin);
        exitButton.pad(8f);
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(final ChangeEvent event, final Actor actor) {
                if (listener != null) {
                    listener.onExitToMenu();
                }
            }
        });
        pauseMenuWindow.add(exitButton);
        pauseMenuWindow.pack();
        UiWindowUtils.createAndCenterWindow(pauseMenuWindow, stage);
    }

    private void setupInputHandling() {
        InputMultiplexer inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(stage);
        inputMultiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                gameWorld.adjustZoom(amountY);
                return true;
            }
        });
        Gdx.input.setInputProcessor(inputMultiplexer);
    }

    private void rememberInfoWindowPosition() {
        if (objectInfoWindow != null) {
            savedInfoWindowX = objectInfoWindow.getX();
            savedInfoWindowY = objectInfoWindow.getY();
        }
    }

    private void showObjectInfo(GameObject gameObject) {
        rememberInfoWindowPosition();
        if (objectInfoWindow != null) {
            objectInfoWindow.remove();
            objectInfoWindow = null;
        }
        objectInfoWindow = new GameObjectInfoWindow(skin, gameObject, stage, cargo, moneyRef, this::rememberInfoWindowPosition);
        objectInfoWindow.pack();
        if (!Float.isNaN(savedInfoWindowX) && !Float.isNaN(savedInfoWindowY)) {
            objectInfoWindow.setPosition(savedInfoWindowX, savedInfoWindowY);
        } else {
            UiWindowUtils.createAndCenterWindow(objectInfoWindow, stage);
            return;
        }
        stage.addActor(objectInfoWindow);
    }

    public void update(float deltaTime) {
        handleEscapeKey();
        if (isPaused()) {
            return;
        }
        updatePlayerInput();
        handleGameplayHotkeys();
        gameWorld.update(deltaTime);
    }

    private boolean isPaused() {
        return pauseMenuWindow != null && pauseMenuWindow.isVisible();
    }

    private void handleEscapeKey() {
        if (!Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            return;
        }

        Window topWindow = findTopmostVisibleWindow();
        if (topWindow != null) {
            closeWindow(topWindow);
            return;
        }

        showPauseMenu();
    }

    /** Topmost visible window on the stage (last drawn). */
    private Window findTopmostVisibleWindow() {
        for (int i = stage.getActors().size - 1; i >= 0; i--) {
            Actor actor = stage.getActors().get(i);
            if (actor instanceof Window window && window.isVisible()) {
                return window;
            }
        }
        return null;
    }

    private void closeWindow(Window window) {
        if (window == objectInfoWindow) {
            rememberInfoWindowPosition();
            objectInfoWindow.setVisible(false);
            return;
        }
        if (window == cargoMenuScreen) {
            cargoMenuScreen.setVisible(false);
            return;
        }
        if (window == pauseMenuWindow) {
            pauseMenuWindow.setVisible(false);
            return;
        }
        window.remove();
    }

    private void showPauseMenu() {
        pauseMenuWindow.setVisible(true);
        pauseMenuWindow.toFront();
        UiWindowUtils.createAndCenterWindow(pauseMenuWindow, stage);
    }

    private void handleGameplayHotkeys() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            GameObject subject = resolveHailSubject();
            if (subject != null) {
                showObjectInfo(subject);
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            showCargoMenu();
        }
    }

    private void showCargoMenu() {
        if (cargoMenuScreen == null) {
            cargoMenuScreen = new CargoMenuScreen(skin, cargo);
            UiWindowUtils.createAndCenterWindow(cargoMenuScreen, stage);
        } else {
            cargoMenuScreen.refresh();
            cargoMenuScreen.setVisible(true);
            cargoMenuScreen.toFront();
            UiWindowUtils.createAndCenterWindow(cargoMenuScreen, stage);
        }
    }

    /** Player lock, then ship/object under the cursor. */
    private GameObject resolveHailSubject() {
        SpaceShip player = gameWorld.getPlayerShip();
        if (player != null) {
            GameObject locked = player.getPlayerTarget();
            if (locked != null && locked.isInteractable()) {
                return locked;
            }
        }

        if (stage.hit(Gdx.input.getX(), Gdx.input.getY(), true) != null) {
            return null;
        }

        float clientW = Gdx.graphics.getWidth();
        float clientH = Gdx.graphics.getHeight();
        mouseWorldScratch.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        gameWorld.getCamera().unproject(mouseWorldScratch, 0f, 0f, clientW, clientH);

        GameObject underMouse = gameWorld.getObjectAt(mouseWorldScratch.x, mouseWorldScratch.y);
        if (underMouse != null && underMouse != player) {
            return underMouse;
        }
        return null;
    }

    private void updatePlayerInput() {
        SpaceShip player = gameWorld.getPlayerShip();
        if (player == null) {
            return;
        }

        float clientW = Gdx.graphics.getWidth();
        float clientH = Gdx.graphics.getHeight();
        mouseWorldScratch.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        gameWorld.getCamera().unproject(mouseWorldScratch, 0f, 0f, clientW, clientH);

        float aimAngle = CustomMathUtils.getAngleBetweenPoints(
            player.getX(),
            player.getY(),
            mouseWorldScratch.x,
            mouseWorldScratch.y
        );

        float forward = 0f;
        float strafe = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            forward += 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            forward -= 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            strafe += 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            strafe -= 1f;
        }

        float inputLen = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (inputLen > 1e-4f) {
            forward /= inputLen;
            strafe /= inputLen;
        }

        player.applyPlayerInput(
            aimAngle,
            forward,
            strafe,
            mouseWorldScratch.x,
            mouseWorldScratch.y
        );

        boolean overUi = stage.hit(Gdx.input.getX(), Gdx.input.getY(), true) != null;
        GameObject hoverTarget = null;
        if (!overUi) {
            hoverTarget = gameWorld.getObjectAt(mouseWorldScratch.x, mouseWorldScratch.y);
            if (hoverTarget == player) {
                hoverTarget = null;
            }
            if (hoverTarget != null && hoverTarget.isInteractable()) {
                player.setPlayerTarget(hoverTarget);
            }
        }
        player.validatePlayerTarget();

        boolean fireHeld = Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !overUi;
        player.setPlayerFireHeld(fireHeld);
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
