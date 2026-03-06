package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class MainMenuScreen extends Window {
    public interface ScreenListener {
        void onPlayPressed();
        void onModdingPressed();
        void onExitPressed();
    }

    private ScreenListener listener;

    public MainMenuScreen(Skin skin, ScreenListener listener) {
        super("Cygnus Engine", skin, "border");
        this.listener = listener;

        defaults().pad(8f);
        
        // Title
        add("Welcome to Cygnus Engine").row();
        
        // Play button
        TextButton playButton = new TextButton("Play", skin);
        playButton.pad(8f);
        playButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (MainMenuScreen.this.listener != null) {
                    MainMenuScreen.this.listener.onPlayPressed();
                }
            }
        });
        add(playButton).fill().uniform().row();
        
        // Modding button
        TextButton moddingButton = new TextButton("Modding", skin);
        moddingButton.pad(8f);
        moddingButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (MainMenuScreen.this.listener != null) {
                    MainMenuScreen.this.listener.onModdingPressed();
                }
            }
        });
        add(moddingButton).fill().uniform().row();
        
        // Exit button
        TextButton exitButton = new TextButton("Exit", skin);
        exitButton.pad(8f);
        exitButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (MainMenuScreen.this.listener != null) {
                    MainMenuScreen.this.listener.onExitPressed();
                }
            }
        });
        add(exitButton).fill().uniform();
        
        pack();
    }
}
