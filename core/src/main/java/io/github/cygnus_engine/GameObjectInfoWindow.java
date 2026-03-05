package io.github.cygnus_engine;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class GameObjectInfoWindow extends Window {
    public GameObjectInfoWindow(Skin skin, GameObject gameObject, Stage stage, Cargo playerCargo, int[] moneyRef) {
        super("Game Object Info", skin, "border");
        
        setModal(true);
        setMovable(true);
        setResizable(false);
        
        defaults().pad(4f);
        
        add(new Label("Name: " + gameObject.getName(), skin)).row();
        add(new Label("Type: " + gameObject.getType().toString(), skin)).row();
        add(new Label("Position: (" + String.format("%.1f", gameObject.getX()) + ", " + 
                      String.format("%.1f", gameObject.getY()) + ")", skin)).row();
        add(new Label("Size: " + String.format("%.1f", gameObject.getSize()), skin)).row();
        add(new Label("Rotation: " + String.format("%.1f", gameObject.getRotation()) + "°", skin)).row();
        
        if (gameObject.getType() == GameObject.Type.SPACE_SHIP) {
            TextButton tradeButton = new TextButton("Trade", skin);
            tradeButton.padRight(8f).padLeft(8f);
            tradeButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    TradeScreen tradeScreen = new TradeScreen(skin, playerCargo, moneyRef, ((SpaceShip)gameObject).getCargo());
                    // Assuming createAndCenterWindow is available, but since it's in Main, perhaps inline
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
        }
        
        TextButton closeButton = new TextButton("Close", skin);
        closeButton.padRight(8f).padLeft(8f);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setVisible(false);
            }
        });
        add(closeButton).padTop(8f);
        
        pack();
    }
}
