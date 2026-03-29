package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class ModdingScreen {
    public interface ScreenListener {
        void onBackPressed();
    }

    private ScreenListener listener;
    private ShipEditor shipEditor;
    private Stage stage;
    private Skin skin;

    private enum Mode { LIST, EDIT }
    private Mode mode = Mode.LIST;

    private Window listWindow;
    private Window editorWindow;

    // Editor UI fields (simple v1)
    private TextField speedField;
    private TextField maneuverField;
    private TextField cargoField;
    private TextButton symmetryButton;
    private TextButton insertModeButton;

    public ModdingScreen(Stage stage, Skin skin, ScreenListener listener) {
        this.stage = stage;
        this.skin = skin;
        this.shipEditor = new ShipEditor();
        this.listener = listener;

        buildListUI();
        setupInput();
    }

    private void setupInput() {
        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(shipEditor.getInputProcessor());
        mux.addProcessor(stage);
        Gdx.input.setInputProcessor(mux);
    }

    private void buildListUI() {
        stage.clear();
        mode = Mode.LIST;

        listWindow = new Window("Modding - Spaceships", skin, "border");
        listWindow.defaults().pad(6f);

        TextButton backButton = new TextButton("Back", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (ModdingScreen.this.listener != null) {
                    ModdingScreen.this.listener.onBackPressed();
                }
            }
        });

        TextButton refreshButton = new TextButton("Refresh", skin);
        refreshButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                buildListUI();
            }
        });

        listWindow.add(backButton).left();
        listWindow.add(refreshButton).right().row();

        Table listTable = new Table(skin);
        listTable.defaults().pad(4f).left();
        populateShipList(listTable);

        ScrollPane scrollPane = new ScrollPane(listTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        listWindow.add(scrollPane).width(520f).height(360f).row();

        UiWindowUtils.createAndCenterWindow(listWindow, stage, 10f);
    }

    private static class ShipEntry {
        final FileHandle textureFile;
        final FileHandle jsonFile;

        ShipEntry(FileHandle textureFile) {
            this.textureFile = textureFile;
            this.jsonFile = ShipDataIO.toJsonFileForTexture(textureFile);
        }

        boolean hasData() { return jsonFile.exists(); }
    }

    private void populateShipList(Table table) {
        FileHandle modsDir = Gdx.files.local("mods");
        modsDir.mkdirs();

        SimpleArray<ShipEntry> entries = new SimpleArray<>();

        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) continue;

            addPngEntries(entries, modFolder);

            FileHandle spaceshipsDir = modFolder.child("spaceships");
            if (spaceshipsDir.exists() && spaceshipsDir.isDirectory()) {
                addPngEntries(entries, spaceshipsDir);
            }
        }

        if (entries.size() == 0) {
            table.add(new Label("No ship PNGs found. Put files under mods/<mod>/spaceships/*.png", skin)).row();
            return;
        }

        table.add(new Label("Ship", skin)).width(240f);
        table.add(new Label("Status", skin)).width(120f);
        table.add(new Label("", skin)).width(120f).row();

        for (int i = 0; i < entries.size(); i++) {
            ShipEntry e = entries.get(i);
            String shipId = e.textureFile.nameWithoutExtension();
            String status = e.hasData() ? "Implemented" : "Missing data";

            table.add(new Label(shipId, skin)).width(240f);
            table.add(new Label(status, skin)).width(120f);

            TextButton editButton = new TextButton("Edit", skin);
            editButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    openEditor(e);
                }
            });
            table.add(editButton).width(120f).row();
        }
    }

    private void addPngEntries(SimpleArray<ShipEntry> entries, FileHandle dir) {
        for (FileHandle f : dir.list()) {
            if (f.isDirectory()) continue;
            if (!"png".equalsIgnoreCase(f.extension())) continue;
            entries.add(new ShipEntry(f));
        }
    }

    private void openEditor(ShipEntry entry) {
        mode = Mode.EDIT;
        shipEditor.loadShip(entry.textureFile);
        buildEditorUI(entry);
    }

    private void buildEditorUI(ShipEntry entry) {
        if (listWindow != null) listWindow.remove();
        if (editorWindow != null) editorWindow.remove();

        editorWindow = new Window("Edit Ship - " + entry.textureFile.nameWithoutExtension(), skin, "border");
        editorWindow.defaults().pad(6f);

        TextButton backButton = new TextButton("Back to list", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                buildListUI();
            }
        });

        TextButton addWeaponButton = new TextButton("Add Weapon Slot", skin);
        addWeaponButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                shipEditor.addWeaponSlot();
            }
        });

        TextButton addEngineButton = new TextButton("Add Engine Pos", skin);
        addEngineButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                shipEditor.addEnginePosition();
            }
        });

        TextButton addColliderButton = new TextButton("Add Collider Vertex", skin);
        addColliderButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                shipEditor.addColliderVertex();
            }
        });

        insertModeButton = new TextButton(insertModeButtonText(), skin);
        insertModeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                shipEditor.setInsertModeEnabled(!shipEditor.isInsertModeEnabled());
                insertModeButton.setText(insertModeButtonText());
            }
        });

        symmetryButton = new TextButton(symmetryButtonText(), skin);
        symmetryButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                shipEditor.setSymmetryEnabled(!shipEditor.isSymmetryEnabled());
                symmetryButton.setText(symmetryButtonText());
            }
        });

        TextButton saveButton = new TextButton("Save JSON", skin);
        saveButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applyEditorFieldsToData();
                ShipDataIO.save(shipEditor.getShipData(), entry.jsonFile);
                buildListUI();
            }
        });

        editorWindow.add(backButton).left();
        editorWindow.add(addWeaponButton);
        editorWindow.add(addEngineButton);
        editorWindow.add(addColliderButton);
        editorWindow.add(insertModeButton);
        editorWindow.add(symmetryButton);
        editorWindow.add(saveButton).right().row();

        ShipData d = shipEditor.getShipData();
        speedField = new TextField(Float.toString(d.speed), skin);
        maneuverField = new TextField(Float.toString(d.maneuverability), skin);
        cargoField = new TextField(Float.toString(d.cargoSpace), skin);

        Table fields = new Table(skin);
        fields.defaults().pad(4f).left();
        fields.add(new Label("Speed", skin)).width(120f);
        fields.add(speedField).width(120f).row();
        fields.add(new Label("Maneuver", skin)).width(120f);
        fields.add(maneuverField).width(120f).row();
        fields.add(new Label("Cargo", skin)).width(120f);
        fields.add(cargoField).width(120f).row();

        editorWindow.add(fields).left().row();
        editorWindow.add(new Label("Drag points directly. Hovered points are yellow. Insert mode: click white-highlighted collider edges to add vertices.", skin)).left().row();

        editorWindow.pack();
        editorWindow.setPosition(10f, Math.max(10f, stage.getHeight() - editorWindow.getHeight() - 10f));
        stage.addActor(editorWindow);
    }

    private void applyEditorFieldsToData() {
        ShipData d = shipEditor.getShipData();
        if (d == null) return;

        d.speed = parseFloatSafe(speedField, d.speed);
        d.maneuverability = parseFloatSafe(maneuverField, d.maneuverability);
        d.cargoSpace = parseFloatSafe(cargoField, d.cargoSpace);
    }

    private float parseFloatSafe(TextField field, float fallback) {
        if (field == null) return fallback;
        try {
            return Float.parseFloat(field.getText());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String symmetryButtonText() {
        return shipEditor.isSymmetryEnabled() ? "Symmetry: ON" : "Symmetry: OFF";
    }

    private String insertModeButtonText() {
        return shipEditor.isInsertModeEnabled() ? "Insert: ON" : "Insert: OFF";
    }

    /** Tiny dynamic array helper to keep this file dependency-light. */
    private static class SimpleArray<T> {
        private Object[] data = new Object[16];
        private int size = 0;

        int size() { return size; }

        void add(T v) {
            if (size >= data.length) {
                Object[] n = new Object[data.length * 2];
                System.arraycopy(data, 0, n, 0, data.length);
                data = n;
            }
            data[size++] = v;
        }

        @SuppressWarnings("unchecked")
        T get(int idx) { return (T) data[idx]; }
    }

    public void update(float deltaTime) {
        if (mode == Mode.EDIT) shipEditor.update(deltaTime);
    }

    public void render() {
        if (mode == Mode.EDIT) shipEditor.render();
    }

    public void resize(int width, int height) {
        shipEditor.resize(width, height);
    }

    public void dispose() {
        shipEditor.dispose();
    }
}
