package io.github.cygnus_engine;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class GameObjectInfoWindow extends Window {
    public GameObjectInfoWindow(Skin skin, GameObject gameObject) {
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
