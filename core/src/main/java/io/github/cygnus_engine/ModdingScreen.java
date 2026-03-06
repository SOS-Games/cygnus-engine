package io.github.cygnus_engine;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class ModdingScreen extends Window {
    public interface ScreenListener {
        void onBackPressed();
    }

    private ScreenListener listener;

    public ModdingScreen(Skin skin, ScreenListener listener) {
        super("Modding", skin, "border");
        this.listener = listener;

        defaults().pad(8f);
        
        // Title/placeholder
        add("Modding Screen").row();
        add("Coming soon...").row();
        
        // Back button
        TextButton backButton = new TextButton("Back", skin);
        backButton.pad(8f);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (ModdingScreen.this.listener != null) {
                    ModdingScreen.this.listener.onBackPressed();
                }
            }
        });
        add(backButton).fill().uniform();
        
        pack();
    }
}
