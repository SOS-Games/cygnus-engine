package io.github.cygnus_engine;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class GameObjectInfoWindow extends Window {
    private final GameObject gameObject;
    private final Runnable onClose;
    private final Label positionLabel;
    private final Label sizeLabel;
    private final Label rotationLabel;
    private final Label shipBehaviorLabel;
    private final Label shipWarpTimerLabel;
    private final Label shipVisibleLabel;
    private final Label shipCombatTargetLabel;
    private final Label directionChangeTimer;
    private final Label distanceToOrbitTarget;
    private final Label targetAngle;

    public GameObjectInfoWindow(Skin skin, GameObject gameObject, Stage stage, Cargo playerCargo, int[] moneyRef,
                                Runnable onClose) {
        super("Game Object Info", skin, "border");
        this.gameObject = gameObject;
        this.onClose = onClose;

        setModal(false);
        setMovable(true);
        setResizable(false);

        defaults().pad(4f);

        add(new Label("Name: " + gameObject.getName(), skin)).row();
        add(new Label("Type: " + gameObject.getType().toString(), skin)).row();
        positionLabel = new Label("", skin);
        add(positionLabel).row();
        sizeLabel = new Label("", skin);
        add(sizeLabel).row();
        rotationLabel = new Label("", skin);
        add(rotationLabel).row();

        if (gameObject.getType() == GameObject.Type.SPACE_SHIP) {
            shipBehaviorLabel = new Label("", skin);
            add(shipBehaviorLabel).row();
            shipWarpTimerLabel = new Label("", skin);
            add(shipWarpTimerLabel).row();
            shipVisibleLabel = new Label("", skin);
            add(shipVisibleLabel).row();
            shipCombatTargetLabel = new Label("", skin);
            add(shipCombatTargetLabel).row();
            directionChangeTimer = new Label("", skin);
            add(directionChangeTimer).row();
            distanceToOrbitTarget = new Label("", skin);
            add(distanceToOrbitTarget).row();
            targetAngle = new Label("", skin);
            add(targetAngle).row();

            TextButton tradeButton = new TextButton("Trade", skin);
            tradeButton.padRight(8f).padLeft(8f);
            tradeButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    TradeScreen tradeScreen = new TradeScreen(skin, playerCargo, moneyRef, ((SpaceShip) gameObject).getCargo());
                    tradeScreen.setMovable(true);
                    tradeScreen.pack();
                    tradeScreen.setPosition(
                        MathUtils.roundPositive(stage.getWidth() / 2f - tradeScreen.getWidth() / 2f),
                        MathUtils.roundPositive(stage.getHeight() / 2f - tradeScreen.getHeight() / 2f)
                    );
                    stage.addActor(tradeScreen);
                }
            });
            add(tradeButton).padTop(8f);
        } else {
            shipBehaviorLabel = null;
            shipWarpTimerLabel = null;
            shipVisibleLabel = null;
            shipCombatTargetLabel = null;
            directionChangeTimer = null;
            distanceToOrbitTarget = null;
            targetAngle = null;
        }

        TextButton closeButton = new TextButton("Close", skin);
        closeButton.padRight(8f).padLeft(8f);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (onClose != null) {
                    onClose.run();
                }
                setVisible(false);
            }
        });
        add(closeButton).padTop(8f);

        pack();
        refreshDynamicLabels();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (isVisible()) {
            refreshDynamicLabels();
        }
    }

    private void refreshDynamicLabels() {
        /*
        positionLabel.setText("Position: (" + String.format("%.1f", gameObject.getX()) + ", "
            + String.format("%.1f", gameObject.getY()) + ")");
        sizeLabel.setText("Size: " + String.format("%.1f", gameObject.getSize()));
        rotationLabel.setText("Rotation: " + String.format("%.1f", gameObject.getRotation()) + "°");
        */
        if (shipBehaviorLabel != null && gameObject instanceof SpaceShip) {
            SpaceShip ship = (SpaceShip) gameObject;
            shipBehaviorLabel.setText("currentBehavior: " + ship.getCurrentBehavior());
            shipWarpTimerLabel.setText("warpTimer: " + String.format("%.2f", ship.getWarpTimer()));
            shipVisibleLabel.setText("isVisible: " + ship.isVisible());
            if (shipCombatTargetLabel != null) {
                shipCombatTargetLabel.setText(ship.buildCombatTargetDebugText());
            }
            directionChangeTimer.setText("directionChangeTimer: " + String.format("%.2f", ship.getDirectionChangeTimer()));
            float stringDistanceToOrbitTarget = (float) Math.sqrt(ship.getDistanceToOrbitTargetSquared());
            distanceToOrbitTarget.setText("distanceToOrbitTarget: " + String.format("%.0f", stringDistanceToOrbitTarget));

            targetAngle.setText("targetAngle: " + String.format("%.0f", ship.getTargetAngle()));
        }
    }
}
