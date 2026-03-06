package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

// todo - back button, other UI elements

public class ModdingScreen {
    public interface ScreenListener {
        void onBackPressed();
    }

    private ScreenListener listener;
    private ShipEditor shipEditor;
    private Stage stage;

    public ModdingScreen(Stage stage, Skin skin, ScreenListener listener) {
        this.stage = stage;
        this.shipEditor = new ShipEditor();
        this.listener = listener;

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

        doModding();
    }


    public void doModding() {
        FileHandle modFolderDir = Gdx.files.local("mods/");

        for (FileHandle modFolder : modFolderDir.list()) {
            if (modFolder.isDirectory()) {
                System.out.println("Folder " + modFolder.name() + "   " + modFolder.path());

                FileHandle modDir = Gdx.files.local(modFolder.path() + "/");
                for (FileHandle file : modDir.list()) {
                    String fileExt = file.extension().toLowerCase();
                    switch (fileExt) {
                        case "png": 
                            System.out.println("File " + file.name() + "   " + file.path());
                            break;
                        case "json":
                            System.out.println("File " + file.name() + "   " + file.path());
                            break;
                        /*
                        // We found a ship image!
                        String shipName = file.nameWithoutExtension();
                        FileHandle dataFile = modDir.child(shipName + ".json");
                        
                        if (dataFile.exists()) {
                            // SHIP IMPLEMENTED: Load the JSON
                        } else {
                            // MISSING DATA: Show in menu as "New/Unconfigured"
                        }
                        */
                    }
                }
            }
        }
    }

    public void update(float deltaTime) {
        shipEditor.update(deltaTime);
    }

    public void render() {
        shipEditor.render();
    }

    public void resize(int width, int height) {
        shipEditor.resize(width, height);
    }

    public void dispose() {
        shipEditor.dispose();
    }
}
