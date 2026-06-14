package io.github.cygnus_engine;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class ModdingScreen {
    public interface ScreenListener {
        void onBackPressed();
    }

    private ScreenListener listener;
    private ShipEditor shipEditor;
    private StarSystemEditor starSystemEditor;
    private Stage stage;
    private Skin skin;

    private enum Mode { LIST, EDIT_SHIP, EDIT_WEAPON, EDIT_SYSTEM }
    private enum ListTab { SHIPS, WEAPONS, SYSTEMS }

    private Mode mode = Mode.LIST;
    private ListTab listTab = ListTab.SHIPS;

    private Window listWindow;
    private Window editorTopBar;
    private Window editorLeftPanel;
    private Window systemEditorTopBar;
    private Window systemEditorLeftPanel;
    private Window weaponEditorWindow;

    private Table listInnerTable;

    private FileHandle editingShipJson;
    private FileHandle editingWeaponJson;
    private FileHandle editingSystemJson;
    private WeaponData editingWeapon;

    private TextField speedField;
    private TextField maneuverField;
    private TextField cargoField;
    private SelectBox<String> combatProfileSelect;
    private TextField hullTurnField;
    private TextButton symmetryButton;
    private Label mountEditorInfoLabel;
    private TextButton slotTypeToggleButton;
    private TextButton equipWeaponButton;
    private TextButton deleteSelectionButton;
    private Table mountActionsRow;

    private Label systemSelectionLabel;
    private TextField systemNameField;
    private TextField systemWorldWidthField;
    private TextField systemWorldHeightField;
    private TextField bodyNameField;
    private TextField bodyXField;
    private TextField bodyYField;
    private TextField bodySizeField;
    private SelectBox<StationKind> bodyStationKindSelect;

    private Texture weaponPreviewTexture;
    private Image weaponPreviewImage;

    private TextField weaponNameField;
    private TextField weaponTypeField;
    private TextField weaponFireIntervalField;
    private TextField weaponProjectileSpeedField;
    private TextField weaponProjectileLifetimeField;
    private TextField weaponProjectileRadiusField;
    private TextField weaponTurretSpriteField;
    private TextField weaponTurnRateField;
    private CheckBox weaponHomingCheck;
    private CheckBox weaponTurretCompatCheck;
    private CheckBox weaponHardpointCompatCheck;
    private TextField weaponHomingTurnField;

    public ModdingScreen(Stage stage, Skin skin, ScreenListener listener) {
        this.stage = stage;
        this.skin = skin;
        this.shipEditor = new ShipEditor();
        this.starSystemEditor = new StarSystemEditor();
        this.listener = listener;

        buildListUI();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void setupInput() {
        InputMultiplexer mux = new InputMultiplexer();
        mux.addProcessor(stage);
        if (mode == Mode.EDIT_SHIP) {
            mux.addProcessor(shipEditor.getInputProcessor());
        } else if (mode == Mode.EDIT_SYSTEM) {
            mux.addProcessor(starSystemEditor.getInputProcessor());
        }
        Gdx.input.setInputProcessor(mux);
    }

    private void buildListUI() {
        stage.clear();
        mode = Mode.LIST;
        removeShipEditorPanels();
        removeSystemEditorPanels();
        if (weaponEditorWindow != null) {
            weaponEditorWindow.remove();
            weaponEditorWindow = null;
        }

        listWindow = new Window("Modding", skin, "border");
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

        TextButton shipsTab = new TextButton("Ships", skin);
        shipsTab.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listTab = ListTab.SHIPS;
                buildListUI();
            }
        });

        TextButton weaponsTab = new TextButton("Weapons", skin);
        weaponsTab.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listTab = ListTab.WEAPONS;
                buildListUI();
            }
        });

        TextButton systemsTab = new TextButton("Systems", skin);
        systemsTab.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                listTab = ListTab.SYSTEMS;
                buildListUI();
            }
        });

        TextButton createShipButton = new TextButton("Create ship…", skin);
        createShipButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openCreateShipDialog();
            }
        });

        TextButton createWeaponButton = new TextButton("Create weapon…", skin);
        createWeaponButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openCreateWeaponDialog();
            }
        });

        TextButton createSystemButton = new TextButton("Create system…", skin);
        createSystemButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openCreateSystemDialog();
            }
        });

        listWindow.add(backButton).left();
        listWindow.add(refreshButton).right().row();

        Table tabRow = new Table(skin);
        tabRow.defaults().pad(4f);
        tabRow.add(new Label("Category:", skin)).padRight(8f);
        tabRow.add(shipsTab);
        tabRow.add(weaponsTab);
        tabRow.add(systemsTab).row();
        if (listTab == ListTab.SHIPS) {
            tabRow.add(createShipButton).padTop(4f);
        } else if (listTab == ListTab.WEAPONS) {
            tabRow.add(createWeaponButton).padTop(4f);
        } else {
            tabRow.add(createSystemButton).padTop(4f);
        }
        listWindow.add(tabRow).left().row();

        listInnerTable = new Table(skin);
        listInnerTable.defaults().pad(4f).left();
        if (listTab == ListTab.SHIPS) {
            populateShipJsonList(listInnerTable);
        } else if (listTab == ListTab.WEAPONS) {
            populateWeaponJsonList(listInnerTable);
        } else {
            populateSystemJsonList(listInnerTable);
        }

        ScrollPane scrollPane = new ScrollPane(listInnerTable, skin);
        scrollPane.setFadeScrollBars(false);
        scrollPane.setScrollingDisabled(true, false);

        listWindow.add(scrollPane).width(720f).height(380f).row();

        UiWindowUtils.createAndCenterWindow(listWindow, stage, 10f);
        setupInput();
    }

    private static ArrayList<FileHandle> collectShipJsonFiles() {
        ArrayList<FileHandle> out = new ArrayList<>();
        FileHandle modsDir = ModPaths.modsRoot();
        if (!modsDir.exists()) {
            return out;
        }
        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) continue;
            for (FileHandle f : modFolder.list()) {
                if (f.isDirectory()) continue;
                if (!"json".equalsIgnoreCase(f.extension())) continue;
                out.add(f);
            }
            FileHandle spaceshipsDir = modFolder.child("spaceships");
            if (spaceshipsDir.exists() && spaceshipsDir.isDirectory()) {
                for (FileHandle f : spaceshipsDir.list()) {
                    if (f.isDirectory()) continue;
                    if (!"json".equalsIgnoreCase(f.extension())) continue;
                    out.add(f);
                }
            }
        }
        out.sort(Comparator.comparing(FileHandle::path));
        return out;
    }

    private static ArrayList<FileHandle> collectWeaponJsonFiles() {
        ArrayList<FileHandle> out = new ArrayList<>();
        FileHandle modsDir = ModPaths.modsRoot();
        if (!modsDir.exists()) {
            return out;
        }
        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) continue;
            FileHandle weaponsDir = modFolder.child("weapons");
            if (!weaponsDir.exists() || !weaponsDir.isDirectory()) continue;
            for (FileHandle f : weaponsDir.list()) {
                if (f.isDirectory()) continue;
                if (!"json".equalsIgnoreCase(f.extension())) continue;
                out.add(f);
            }
        }
        out.sort(Comparator.comparing(FileHandle::path));
        return out;
    }

    private void populateShipJsonList(Table table) {
        ArrayList<FileHandle> files = collectShipJsonFiles();
        if (files.isEmpty()) {
            Label empty = new Label("No ship JSON found. Put ship definitions in mods/<mod>/*.json or mods/<mod>/spaceships/*.json (not in weapons/).", skin);
            empty.setWrap(true);
            table.add(empty).width(680f).row();
            return;
        }

        table.add(new Label("Ship (id)", skin)).width(200f);
        table.add(new Label("JSON path", skin)).width(280f);
        table.add(new Label("Image", skin)).width(100f);
        table.add(new Label("", skin)).width(100f).row();

        for (FileHandle jsonFile : files) {
            ShipData peek = ShipDataIO.loadFromJson(jsonFile);
            String id = peek != null && peek.id != null && !peek.id.isBlank() ? peek.id : jsonFile.nameWithoutExtension();
            String texPath = peek != null ? peek.texturePath : "";
            boolean imageOk = false;
            if (texPath != null && !texPath.isBlank()) {
                FileHandle t = ModPaths.resolveLocal(texPath);
                imageOk = t.exists() || Gdx.files.internal(texPath).exists();
            }

            table.add(new Label(id, skin)).width(200f);
            table.add(new Label(jsonFile.path(), skin)).width(280f);
            table.add(new Label(imageOk ? "OK" : "Missing", skin)).width(100f);

            TextButton editButton = new TextButton("Edit", skin);
            FileHandle jsonRef = jsonFile;
            editButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    openShipEditor(jsonRef);
                }
            });
            table.add(editButton).width(100f).row();
        }
    }

    private void populateWeaponJsonList(Table table) {
        ArrayList<FileHandle> files = collectWeaponJsonFiles();
        if (files.isEmpty()) {
            Label empty = new Label("No weapon JSON found. Add mods/<mod>/weapons/*.json or use Create weapon.", skin);
            empty.setWrap(true);
            table.add(empty).width(680f).row();
            return;
        }

        table.add(new Label("Weapon id", skin)).width(200f);
        table.add(new Label("Path", skin)).width(380f);
        table.add(new Label("", skin)).width(100f).row();

        for (FileHandle jsonFile : files) {
            WeaponData w = WeaponDataIO.loadFromJsonFile(jsonFile);
            String id = w != null && w.id != null ? w.id : jsonFile.nameWithoutExtension();
            table.add(new Label(id, skin)).width(200f);
            table.add(new Label(jsonFile.path(), skin)).width(380f);

            TextButton editButton = new TextButton("Edit", skin);
            FileHandle jsonRef = jsonFile;
            editButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    openWeaponEditor(jsonRef);
                }
            });
            table.add(editButton).width(100f).row();
        }
    }

    private void populateSystemJsonList(Table table) {
        List<FileHandle> files = StarSystemDataIO.collectSystemJsonFiles();
        if (files.isEmpty()) {
            Label empty = new Label(
                "No star system JSON found. Add mods/<mod>/systems/*.json or use Create system.",
                skin
            );
            empty.setWrap(true);
            table.add(empty).width(680f).row();
            return;
        }

        table.add(new Label("System id", skin)).width(200f);
        table.add(new Label("Path", skin)).width(380f);
        table.add(new Label("", skin)).width(100f).row();

        for (FileHandle jsonFile : files) {
            StarSystemData peek = StarSystemDataIO.loadFromJson(jsonFile);
            String id = peek != null && peek.id != null && !peek.id.isBlank()
                ? peek.id
                : jsonFile.nameWithoutExtension();
            int bodyCount = peek != null && peek.bodies != null ? peek.bodies.size() : 0;

            table.add(new Label(id + " (" + bodyCount + " bodies)", skin)).width(200f);
            table.add(new Label(jsonFile.path(), skin)).width(380f);

            TextButton editButton = new TextButton("Edit", skin);
            FileHandle jsonRef = jsonFile;
            editButton.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    openSystemEditor(jsonRef);
                }
            });
            table.add(editButton).width(100f).row();
        }
    }

    private void openShipEditor(FileHandle jsonFile) {
        mode = Mode.EDIT_SHIP;
        editingShipJson = jsonFile;
        if (listWindow != null) listWindow.remove();
        removeSystemEditorPanels();
        if (weaponEditorWindow != null) {
            weaponEditorWindow.remove();
            weaponEditorWindow = null;
        }

        shipEditor.loadShipFromDefinition(jsonFile);
        buildShipEditorUI();
        setupInput();
    }

    private void removeShipEditorPanels() {
        if (editorTopBar != null) {
            editorTopBar.remove();
            editorTopBar = null;
        }
        if (editorLeftPanel != null) {
            editorLeftPanel.remove();
            editorLeftPanel = null;
        }
    }

    private void removeSystemEditorPanels() {
        if (systemEditorTopBar != null) {
            systemEditorTopBar.remove();
            systemEditorTopBar = null;
        }
        if (systemEditorLeftPanel != null) {
            systemEditorLeftPanel.remove();
            systemEditorLeftPanel = null;
        }
    }

    private void layoutShipEditorPanels() {
        if (editorTopBar == null) {
            return;
        }

        float stageW = stage.getWidth();
        float stageH = stage.getHeight();
        float topH = editorTopBar.getHeight();
        editorTopBar.setSize(stageW, topH);
        editorTopBar.setPosition(0f, stageH - topH);

        float leftW = editorLeftPanel != null ? editorLeftPanel.getWidth() : 0f;
        if (editorLeftPanel != null) {
            editorLeftPanel.setPosition(0f, 0f);
            editorLeftPanel.setHeight(Math.max(0f, stageH - topH));
        }

        shipEditor.setViewportInsets(leftW, topH, 0f, 0f);
        shipEditor.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void buildShipEditorUI() {
        removeShipEditorPanels();

        String titleId = shipEditor.getShipData() != null ? shipEditor.getShipData().id : editingShipJson.nameWithoutExtension();
        editorTopBar = new Window("Edit Ship — " + titleId, skin, "border");
        editorTopBar.defaults().pad(4f);

        TextButton backButton = new TextButton("Back to list", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                buildListUI();
            }
        });

        TextButton changeTextureButton = new TextButton("Change hull image…", skin);
        changeTextureButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openHullTexturePickerDialog();
            }
        });

        TextButton addWeaponButton = new TextButton("Add Weapon Slot", skin);
        addWeaponButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                shipEditor.addWeaponSlot();
            }
        });

        slotTypeToggleButton = new TextButton("Toggle slot type", skin);
        slotTypeToggleButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                WeaponSlot s = shipEditor.getSelectedWeaponSlot();
                if (s == null) return;
                WeaponSlot.SlotType next = s.type == WeaponSlot.SlotType.TURRET
                    ? WeaponSlot.SlotType.HARDPOINT
                    : WeaponSlot.SlotType.TURRET;
                shipEditor.setSelectedWeaponSlotType(next);
                refreshSelectionUI();
            }
        });

        equipWeaponButton = new TextButton("Equip weapon…", skin);
        equipWeaponButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openEquipWeaponDialog();
            }
        });

        deleteSelectionButton = new TextButton("Delete selected", skin);
        deleteSelectionButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (shipEditor.deleteSelected()) {
                    refreshSelectionUI();
                }
            }
        });

        TextButton addEngineButton = new TextButton("Add Engine Pos", skin);
        addEngineButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                shipEditor.addEnginePosition();
            }
        });

        TextButton addColliderButton = new TextButton("Add Collider Circle", skin);
        addColliderButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                shipEditor.addColliderCircle();
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
                ShipDataIO.save(shipEditor.getShipData(), editingShipJson);
                buildListUI();
            }
        });

        editorTopBar.add(backButton).padRight(6f);
        editorTopBar.add(changeTextureButton).padRight(6f);
        editorTopBar.add(addWeaponButton).padRight(6f);
        editorTopBar.add(addEngineButton).padRight(6f);
        editorTopBar.add(addColliderButton).padRight(6f);
        editorTopBar.add(symmetryButton).padRight(6f);
        editorTopBar.add(saveButton).padRight(6f).row();

        mountEditorInfoLabel = new Label("", skin);
        mountEditorInfoLabel.setWrap(true);

        Table deleteColumn = new Table(skin);
        deleteColumn.defaults().pad(2f);
        deleteColumn.add(deleteSelectionButton).left().row();
        deleteColumn.add(mountEditorInfoLabel).width(220f).left().padTop(2f).row();

        mountActionsRow = new Table(skin);
        mountActionsRow.defaults().pad(4f);
        mountActionsRow.add(equipWeaponButton).padRight(8f);
        mountActionsRow.add(slotTypeToggleButton).padRight(8f);
        mountActionsRow.add(deleteColumn).left();
        editorTopBar.add(mountActionsRow).left().colspan(7).padTop(2f).row();

        editorLeftPanel = new Window("Stats & layers", skin, "border");
        editorLeftPanel.defaults().pad(4f).left();

        Table layerToggles = new Table(skin);
        layerToggles.defaults().pad(2f).left();
        layerToggles.add(new Label("Show:", skin)).left().row();
        layerToggles.add(makeLayerCheckbox("Weapons", true, shipEditor::setLayerWeaponsVisible)).left().row();
        layerToggles.add(makeLayerCheckbox("Engines", true, shipEditor::setLayerEnginesVisible)).left().row();
        layerToggles.add(makeLayerCheckbox("COM", true, shipEditor::setLayerCenterOfMassVisible)).left().row();
        layerToggles.add(makeLayerCheckbox("Hit colliders", true, shipEditor::setLayerCollidersVisible)).left().row();
        layerToggles.add(makeLayerCheckbox("Click bounds", true, shipEditor::setLayerClickBoundsVisible)).left().row();
        editorLeftPanel.add(layerToggles).left().padBottom(8f).row();

        ShipData d = shipEditor.getShipData();
        speedField = new TextField(Float.toString(d.speed), skin);
        maneuverField = new TextField(Float.toString(d.maneuverability), skin);
        cargoField = new TextField(Float.toString(d.cargoSpace), skin);

        Table fields = new Table(skin);
        fields.defaults().pad(2f).left();
        fields.add(new Label("Texture", skin)).width(90f).row();
        Label texturePathLabel = new Label(d.texturePath == null ? "(none)" : d.texturePath, skin);
        texturePathLabel.setWrap(true);
        fields.add(texturePathLabel).width(210f).left().row();
        fields.add(new Label("Speed", skin)).width(90f).row();
        fields.add(speedField).width(120f).row();
        fields.add(new Label("Maneuver", skin)).width(90f).row();
        fields.add(maneuverField).width(120f).row();
        fields.add(new Label("Cargo", skin)).width(90f).row();
        fields.add(cargoField).width(120f).row();

        d.normalizeCombatProfile();
        combatProfileSelect = new SelectBox<>(skin);
        Array<String> profiles = new Array<>();
        profiles.add("FIGHTER");
        profiles.add("FRIGATE");
        combatProfileSelect.setItems(profiles);
        combatProfileSelect.setSelected(d.combatProfile);
        fields.add(new Label("Profile", skin)).width(90f).row();
        fields.add(combatProfileSelect).width(140f).row();

        hullTurnField = new TextField(Float.toString(d.hullTurnDegPerSec), skin);

        fields.add(new Label("Hull turn °/s", skin)).width(90f).row();
        fields.add(hullTurnField).width(120f).row();

        ScrollPane fieldsScroll = new ScrollPane(fields, skin);
        fieldsScroll.setFadeScrollBars(false);
        fieldsScroll.setScrollingDisabled(true, false);
        editorLeftPanel.add(fieldsScroll).width(230f).growY().row();

        editorTopBar.pack();
        editorLeftPanel.pack();

        stage.addActor(editorLeftPanel);
        stage.addActor(editorTopBar);

        layoutShipEditorPanels();
        refreshSelectionUI();
    }

    private void openSystemEditor(FileHandle jsonFile) {
        mode = Mode.EDIT_SYSTEM;
        editingSystemJson = jsonFile;
        if (listWindow != null) {
            listWindow.remove();
        }
        removeShipEditorPanels();
        if (weaponEditorWindow != null) {
            weaponEditorWindow.remove();
            weaponEditorWindow = null;
        }

        starSystemEditor.loadFromDefinition(jsonFile);
        buildSystemEditorUI();
        setupInput();
    }

    private void layoutSystemEditorPanels() {
        if (systemEditorTopBar == null) {
            return;
        }

        float stageW = stage.getWidth();
        float stageH = stage.getHeight();
        float topH = systemEditorTopBar.getHeight();
        systemEditorTopBar.setSize(stageW, topH);
        systemEditorTopBar.setPosition(0f, stageH - topH);

        float leftW = systemEditorLeftPanel != null ? systemEditorLeftPanel.getWidth() : 0f;
        if (systemEditorLeftPanel != null) {
            systemEditorLeftPanel.setPosition(0f, 0f);
            systemEditorLeftPanel.setHeight(Math.max(0f, stageH - topH));
        }

        starSystemEditor.setViewportInsets(leftW, topH, 0f, 0f);
        starSystemEditor.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void buildSystemEditorUI() {
        removeSystemEditorPanels();

        StarSystemData data = starSystemEditor.getSystemData();
        String titleId = data != null ? data.id : editingSystemJson.nameWithoutExtension();
        systemEditorTopBar = new Window("Edit Star System — " + titleId, skin, "border");
        systemEditorTopBar.defaults().pad(4f);

        TextButton backButton = new TextButton("Back to list", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                buildListUI();
            }
        });

        TextButton addPlanetButton = new TextButton("Add planet", skin);
        addPlanetButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                starSystemEditor.addPlanet();
                refreshSystemSelectionUI();
            }
        });

        TextButton addStationButton = new TextButton("Add station", skin);
        addStationButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                starSystemEditor.addStation();
                refreshSystemSelectionUI();
            }
        });

        TextButton deleteButton = new TextButton("Delete selected", skin);
        deleteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (starSystemEditor.deleteSelected()) {
                    refreshSystemSelectionUI();
                }
            }
        });

        TextButton saveButton = new TextButton("Save JSON", skin);
        saveButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applySystemFieldsToData();
                StarSystemDataIO.save(starSystemEditor.getSystemData(), editingSystemJson);
            }
        });

        systemEditorTopBar.add(backButton).padRight(6f);
        systemEditorTopBar.add(addPlanetButton).padRight(6f);
        systemEditorTopBar.add(addStationButton).padRight(6f);
        systemEditorTopBar.add(deleteButton).padRight(6f);
        systemEditorTopBar.add(saveButton).padRight(6f).row();

        systemEditorLeftPanel = new Window("System & selection", skin, "border");
        systemEditorLeftPanel.defaults().pad(4f).left();

        systemSelectionLabel = new Label("", skin);
        systemSelectionLabel.setWrap(true);

        systemNameField = new TextField(data != null ? data.name : "", skin);
        systemWorldWidthField = new TextField(data != null ? Float.toString(data.worldWidth) : "800", skin);
        systemWorldHeightField = new TextField(data != null ? Float.toString(data.worldHeight) : "600", skin);
        bodyNameField = new TextField("", skin);
        bodyXField = new TextField("", skin);
        bodyYField = new TextField("", skin);
        bodySizeField = new TextField("", skin);
        bodyStationKindSelect = new SelectBox<>(skin);
        bodyStationKindSelect.setItems(StationKind.values());

        Table fields = new Table(skin);
        fields.defaults().pad(4f).left();
        fields.add(new Label("Display name", skin)).width(110f);
        fields.add(systemNameField).width(140f).row();
        fields.add(new Label("Play width", skin)).width(110f);
        fields.add(systemWorldWidthField).width(80f).row();
        fields.add(new Label("Play height", skin)).width(110f);
        fields.add(systemWorldHeightField).width(80f).row();
        Label viewportHint = new Label("(green box = play viewport)", skin);
        viewportHint.setWrap(true);
        fields.add(viewportHint).colspan(2).width(250f).row();
        fields.add(systemSelectionLabel).colspan(2).width(250f).padTop(8f).row();
        fields.add(new Label("Body name", skin)).width(110f);
        fields.add(bodyNameField).width(140f).row();
        fields.add(new Label("X", skin)).width(110f);
        fields.add(bodyXField).width(80f).row();
        fields.add(new Label("Y", skin)).width(110f);
        fields.add(bodyYField).width(80f).row();
        fields.add(new Label("Size", skin)).width(110f);
        fields.add(bodySizeField).width(80f).row();
        fields.add(new Label("Station kind", skin)).width(110f);
        fields.add(bodyStationKindSelect).width(140f).row();
        Label dragHint = new Label("(drag map or edit fields)", skin);
        dragHint.setWrap(true);
        fields.add(dragHint).colspan(2).width(250f).row();

        systemEditorLeftPanel.add(fields).left().row();

        systemEditorTopBar.pack();
        systemEditorLeftPanel.pack();

        stage.addActor(systemEditorLeftPanel);
        stage.addActor(systemEditorTopBar);

        layoutSystemEditorPanels();
        refreshSystemSelectionUI();
    }

    private void applySystemFieldsToData() {
        StarSystemData data = starSystemEditor.getSystemData();
        if (data == null) {
            return;
        }

        data.name = systemNameField.getText().trim();
        if (data.name.isBlank()) {
            data.name = data.id;
        }
        data.worldWidth = parseFloatSafe(systemWorldWidthField, data.worldWidth);
        data.worldHeight = parseFloatSafe(systemWorldHeightField, data.worldHeight);

        StarSystemBody selected = starSystemEditor.getSelectedBody();
        if (selected != null) {
            selected.name = bodyNameField.getText().trim();
            if (selected.name.isBlank()) {
                selected.normalize();
            }
            selected.x = parseFloatSafe(bodyXField, selected.x);
            selected.y = parseFloatSafe(bodyYField, selected.y);
            selected.size = parseFloatSafe(bodySizeField, selected.size);
            if (selected.type == StarSystemBody.Kind.SPACE_STATION && bodyStationKindSelect != null) {
                selected.stationKind = bodyStationKindSelect.getSelected();
            }
            selected.normalize();
        }
        data.normalize();
    }

    private void refreshSystemSelectionUI() {
        if (systemSelectionLabel != null) {
            systemSelectionLabel.setText(starSystemEditor.getSelectionSummary());
        }

        StarSystemBody selected = starSystemEditor.getSelectedBody();
        if (selected == null) {
            if (bodyNameField != null) bodyNameField.setText("");
            if (bodyXField != null) bodyXField.setText("");
            if (bodyYField != null) bodyYField.setText("");
            if (bodySizeField != null) bodySizeField.setText("");
            if (bodyStationKindSelect != null) bodyStationKindSelect.setVisible(false);
            return;
        }

        bodyNameField.setText(selected.name);
        bodyXField.setText(Integer.toString(Math.round(selected.x)));
        bodyYField.setText(Integer.toString(Math.round(selected.y)));
        bodySizeField.setText(Integer.toString(Math.round(selected.size)));
        if (bodyStationKindSelect != null) {
            boolean station = selected.type == StarSystemBody.Kind.SPACE_STATION;
            bodyStationKindSelect.setVisible(station);
            if (station) {
                bodyStationKindSelect.setSelected(
                    selected.stationKind != null ? selected.stationKind : StationKind.TRADER
                );
            }
        }
    }

    private void openCreateSystemDialog() {
        FileHandle systemsDir = ModPaths.resolveLocal("mods/core/systems");
        systemsDir.mkdirs();

        Dialog dialog = new Dialog("Create star system", skin, "dialog") {
            @Override
            protected void result(Object object) {
            }
        };
        dialog.getContentTable().defaults().pad(6f).growX();

        final Label err = new Label("", skin);
        err.setWrap(true);
        final TextField idField = new TextField("", skin);
        idField.setMessageText("system_id");

        dialog.getContentTable().add(new Label("New system id (creates mods/core/systems/<id>.json):", skin)).left().row();
        dialog.getContentTable().add(idField).width(420f).row();
        dialog.getContentTable().add(err).width(420f).row();

        TextButton create = new TextButton("Create", skin);
        create.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String stem = sanitizeFileStem(idField.getText());
                if (stem.isBlank()) {
                    err.setText("Enter a valid id.");
                    return;
                }
                FileHandle jsonOut = systemsDir.child(stem + ".json");
                if (jsonOut.exists()) {
                    err.setText("File already exists: " + jsonOut.path());
                    return;
                }
                StarSystemData data = StarSystemDataIO.createDefaultLayout(stem);
                StarSystemDataIO.save(data, jsonOut);
                dialog.hide();
                listTab = ListTab.SYSTEMS;
                buildListUI();
                openSystemEditor(jsonOut);
            }
        });
        dialog.getContentTable().add(create).row();

        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private void openHullTexturePickerDialog() {
        ShipData d = shipEditor.getShipData();
        if (d == null) return;

        openTexturePickerDialog("Pick hull PNG", collectTexturePngFiles(), fh -> {
            d.texturePath = fh.path();
            shipEditor.reloadHullTextureFromShipData();
            buildShipEditorUI();
        });
    }

    private void openTexturePickerDialog(String title, ArrayList<FileHandle> pngs, Consumer<FileHandle> onPick) {
        final ArrayList<Texture> previewTextures = new ArrayList<>();
        Dialog dialog = new Dialog(title, skin, "dialog") {
            @Override
            protected void result(Object object) {
                disposePickerTextures(previewTextures);
            }
        };
        dialog.getContentTable().defaults().pad(4f).growX();

        if (pngs.isEmpty()) {
            dialog.getContentTable().add(new Label("No PNG files found under mods/.", skin)).row();
        } else {
            Table list = new Table(skin);
            for (FileHandle fh : pngs) {
                Table row = new Table(skin);
                row.defaults().pad(4f);
                addTexturePreviewCell(row, fh, previewTextures, skin);
                TextButton pick = new TextButton(fh.path(), skin);
                pick.getLabel().setWrap(true);
                pick.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        onPick.accept(fh);
                        disposePickerTextures(previewTextures);
                        dialog.hide();
                    }
                });
                row.add(pick).growX();
                list.add(row).growX().width(560f).row();
            }
            ScrollPane scroll = new ScrollPane(list, skin);
            scroll.setFadeScrollBars(false);
            scroll.setScrollingDisabled(true, false);
            dialog.getContentTable().add(scroll).width(580f).height(320f).row();
        }

        dialog.button("Close", true);
        dialog.show(stage);
    }

    private void addTexturePreviewCell(Table row, FileHandle fh, ArrayList<Texture> previewTextures, Skin skin) {
        if (fh != null && fh.exists()) {
            Texture tex = new Texture(fh);
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            previewTextures.add(tex);
            Image thumb = new Image(new TextureRegionDrawable(new TextureRegion(tex)));
            row.add(thumb).size(64f, 64f).padRight(8f);
        } else {
            row.add(new Label("(missing)", skin)).size(64f, 64f).padRight(8f);
        }
    }

    private static void disposePickerTextures(ArrayList<Texture> textures) {
        for (Texture tex : textures) {
            tex.dispose();
        }
        textures.clear();
    }

    private static ArrayList<FileHandle> collectTexturePngFiles() {
        ArrayList<FileHandle> out = new ArrayList<>();
        FileHandle modsDir = ModPaths.modsRoot();
        if (!modsDir.exists()) {
            return out;
        }
        for (FileHandle modFolder : modsDir.list()) {
            if (!modFolder.isDirectory()) continue;
            addPngFilesInDirectory(modFolder, out, false);
            addPngFilesInDirectory(modFolder.child("spaceships"), out, true);
            addPngFilesInDirectory(modFolder.child("weapons"), out, true);
        }
        out.sort(Comparator.comparing(FileHandle::path));
        return out;
    }

    private static void addPngFilesInDirectory(FileHandle dir, ArrayList<FileHandle> out, boolean requireDir) {
        if (requireDir && (!dir.exists() || !dir.isDirectory())) {
            return;
        }
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        for (FileHandle f : dir.list()) {
            if (f.isDirectory()) continue;
            if (!"png".equalsIgnoreCase(f.extension())) continue;
            out.add(f);
        }
    }

    private void openCreateShipDialog() {
        FileHandle defaultMod = ModPaths.resolveLocal("mods/core");
        defaultMod.mkdirs();

        Dialog dialog = new Dialog("Create ship", skin, "dialog") {
            @Override
            protected void result(Object object) {
            }
        };
        dialog.getContentTable().defaults().pad(6f).growX();

        final Label err = new Label("", skin);
        err.setWrap(true);
        final TextField idField = new TextField("", skin);
        idField.setMessageText("ship_id");

        dialog.getContentTable().add(new Label("New ship id (creates mods/core/<id>.json):", skin)).left().row();
        dialog.getContentTable().add(idField).width(420f).row();
        dialog.getContentTable().add(err).width(420f).row();

        TextButton create = new TextButton("Create", skin);
        create.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String raw = idField.getText();
                String stem = sanitizeFileStem(raw);
                if (stem.isBlank()) {
                    err.setText("Enter a valid id.");
                    return;
                }
                FileHandle jsonOut = defaultMod.child(stem + ".json");
                if (jsonOut.exists()) {
                    err.setText("File already exists: " + jsonOut.path());
                    return;
                }
                ShipData data = newDefaultShipData(stem);
                ShipDataIO.save(data, jsonOut);
                dialog.hide();
                listTab = ListTab.SHIPS;
                buildListUI();
                openShipEditor(jsonOut);
            }
        });
        dialog.getContentTable().add(create).row();

        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private static ShipData newDefaultShipData(String id) {
        ShipData d = new ShipData();
        d.id = id;
        d.name = id;
        d.texturePath = "mods/core/" + id + ".png";
        d.speed = 120f;
        d.maneuverability = 2f;
        d.cargoSpace = 2f;
        d.weaponSlots = new ArrayList<>();
        d.enginePositions = new ArrayList<>();
        d.colliders = new ArrayList<>();
        d.normalizeWeaponSlots();
        d.normalizeCombatProfile();
        d.normalizeColliders();
        d.normalizeOuterBounds();
        return d;
    }

    private void openCreateWeaponDialog() {
        FileHandle weaponsDir = ModPaths.resolveLocal("mods/core/weapons");
        weaponsDir.mkdirs();

        Dialog dialog = new Dialog("Create weapon", skin, "dialog") {
            @Override
            protected void result(Object object) {
            }
        };
        dialog.getContentTable().defaults().pad(6f).growX();

        final Label err = new Label("", skin);
        err.setWrap(true);
        final TextField idField = new TextField("", skin);
        idField.setMessageText("weapon_id");

        dialog.getContentTable().add(new Label("New weapon id (creates mods/core/weapons/<id>.json):", skin)).left().row();
        dialog.getContentTable().add(idField).width(420f).row();
        dialog.getContentTable().add(err).width(420f).row();

        TextButton create = new TextButton("Create", skin);
        create.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                String stem = sanitizeFileStem(idField.getText());
                if (stem.isBlank()) {
                    err.setText("Enter a valid id.");
                    return;
                }
                FileHandle jsonOut = weaponsDir.child(stem + ".json");
                if (jsonOut.exists()) {
                    err.setText("File already exists: " + jsonOut.path());
                    return;
                }
                WeaponData w = new WeaponData();
                w.id = stem;
                w.name = stem;
                WeaponDataIO.save(w, jsonOut);
                dialog.hide();
                listTab = ListTab.WEAPONS;
                buildListUI();
                openWeaponEditor(jsonOut);
            }
        });
        dialog.getContentTable().add(create).row();

        dialog.button("Cancel", false);
        dialog.show(stage);
    }

    private static String sanitizeFileStem(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim().replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        if (s.startsWith(".")) {
            s = "ship_" + s;
        }
        return s;
    }

    private void openWeaponEditor(FileHandle jsonFile) {
        mode = Mode.EDIT_WEAPON;
        editingWeaponJson = jsonFile;
        editingWeapon = WeaponDataIO.loadFromJsonFile(jsonFile);
        if (editingWeapon == null) {
            editingWeapon = new WeaponData();
            editingWeapon.id = jsonFile.nameWithoutExtension();
            editingWeapon.name = editingWeapon.id;
        }

        if (listWindow != null) listWindow.remove();
        removeShipEditorPanels();
        removeSystemEditorPanels();

        buildWeaponEditorUI();
        setupInput();
    }

    private void buildWeaponEditorUI() {
        if (weaponEditorWindow != null) weaponEditorWindow.remove();
        disposeWeaponPreviewTexture();

        weaponEditorWindow = new Window("Edit Weapon — " + editingWeapon.id, skin, "border");
        weaponEditorWindow.defaults().pad(6f);

        TextButton backButton = new TextButton("Back to list", skin);
        backButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                buildListUI();
            }
        });

        TextButton saveButton = new TextButton("Save JSON", skin);
        saveButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                applyWeaponFieldsToData();
                WeaponDataIO.save(editingWeapon, editingWeaponJson);
                buildListUI();
            }
        });

        weaponEditorWindow.add(backButton).left();
        weaponEditorWindow.add(saveButton).right().row();

        Table previewRow = new Table(skin);
        previewRow.defaults().pad(6f);
        weaponPreviewImage = new Image();
        previewRow.add(new Label("Sprite preview", skin)).top().left().padRight(8f);
        previewRow.add(weaponPreviewImage).size(96f, 96f).top().left();
        weaponEditorWindow.add(previewRow).left().row();

        TextButton pickSpriteButton = new TextButton("Pick sprite PNG…", skin);
        pickSpriteButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                openWeaponSpritePickerDialog();
            }
        });
        weaponEditorWindow.add(pickSpriteButton).left().padBottom(6f).row();

        weaponNameField = new TextField(editingWeapon.name, skin);
        weaponTypeField = new TextField(editingWeapon.type, skin);
        weaponFireIntervalField = new TextField(Float.toString(editingWeapon.fireInterval), skin);
        weaponProjectileSpeedField = new TextField(Float.toString(editingWeapon.projectileSpeed), skin);
        weaponProjectileLifetimeField = new TextField(Float.toString(editingWeapon.projectileLifetime), skin);
        weaponProjectileRadiusField = new TextField(Float.toString(editingWeapon.projectileRadius), skin);
        weaponTurretSpriteField = new TextField(editingWeapon.turretSprite == null ? "" : editingWeapon.turretSprite, skin);
        weaponTurnRateField = new TextField(Float.toString(editingWeapon.turnRateDegPerSec), skin);
        weaponHomingCheck = new CheckBox("Homing", skin);
        weaponHomingCheck.setChecked(editingWeapon.homing);
        weaponTurretCompatCheck = new CheckBox("Turret slot", skin);
        weaponTurretCompatCheck.setChecked(editingWeapon.turretCompatible);
        weaponHardpointCompatCheck = new CheckBox("Hardpoint slot", skin);
        weaponHardpointCompatCheck.setChecked(editingWeapon.hardpointCompatible);
        weaponHomingTurnField = new TextField(Float.toString(editingWeapon.homingTurnRateDegPerSec), skin);

        Table t = new Table(skin);
        t.defaults().pad(4f).left();
        t.add(new Label("Id (from file)", skin)).width(160f);
        t.add(new Label(editingWeapon.id, skin)).width(400f).left().row();
        t.add(new Label("Name", skin)).width(160f);
        t.add(weaponNameField).width(400f).row();
        t.add(new Label("Category type", skin)).width(160f);
        t.add(weaponTypeField).width(400f).row();
        t.add(new Label("Fire interval (s)", skin)).width(160f);
        t.add(weaponFireIntervalField).width(160f).row();
        t.add(new Label("Projectile speed", skin)).width(160f);
        t.add(weaponProjectileSpeedField).width(160f).row();
        t.add(new Label("Projectile lifetime", skin)).width(160f);
        t.add(weaponProjectileLifetimeField).width(160f).row();
        t.add(new Label("Projectile radius", skin)).width(160f);
        t.add(weaponProjectileRadiusField).width(160f).row();
        t.add(new Label("Turret sprite path", skin)).width(160f);
        t.add(weaponTurretSpriteField).width(400f).row();
        t.add(new Label("Turn rate °/s", skin)).width(160f);
        t.add(weaponTurnRateField).width(160f).row();
        t.add(weaponHomingCheck).colspan(2).left().row();
        t.add(new Label("Homing turn °/s", skin)).width(160f);
        t.add(weaponHomingTurnField).width(160f).row();
        t.add(weaponTurretCompatCheck).colspan(2).left().row();
        t.add(weaponHardpointCompatCheck).colspan(2).left().row();

        weaponEditorWindow.add(t).left().row();
        refreshWeaponPreviewImage();
        weaponEditorWindow.pack();
        weaponEditorWindow.setPosition(10f, Math.max(10f, stage.getHeight() - weaponEditorWindow.getHeight() - 10f));
        stage.addActor(weaponEditorWindow);
    }

    private void openWeaponSpritePickerDialog() {
        openTexturePickerDialog("Pick weapon sprite PNG", collectTexturePngFiles(), fh -> {
            weaponTurretSpriteField.setText(fh.path());
            refreshWeaponPreviewImage();
        });
    }

    private void refreshWeaponPreviewImage() {
        if (weaponPreviewImage == null) return;
        disposeWeaponPreviewTexture();
        String path = weaponTurretSpriteField != null ? weaponTurretSpriteField.getText().trim() : editingWeapon.turretSprite;
        if (path == null || path.isBlank()) {
            weaponPreviewImage.setDrawable(null);
            return;
        }
        FileHandle fh = WeaponDataIO.resolveTextureFile(path);
        if (fh == null || !fh.exists()) {
            weaponPreviewImage.setDrawable(null);
            return;
        }
        weaponPreviewTexture = new Texture(fh);
        weaponPreviewTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        weaponPreviewImage.setDrawable(new TextureRegionDrawable(new TextureRegion(weaponPreviewTexture)));
    }

    private void disposeWeaponPreviewTexture() {
        if (weaponPreviewTexture != null) {
            weaponPreviewTexture.dispose();
            weaponPreviewTexture = null;
        }
    }

    private void applyWeaponFieldsToData() {
        if (editingWeapon == null) return;
        editingWeapon.name = weaponNameField.getText().trim();
        if (editingWeapon.name.isBlank()) {
            editingWeapon.name = editingWeapon.id;
        }
        editingWeapon.type = weaponTypeField.getText().trim();
        if (editingWeapon.type.isBlank()) {
            editingWeapon.type = "GENERIC";
        }
        editingWeapon.fireInterval = parseFloatSafe(weaponFireIntervalField, editingWeapon.fireInterval);
        editingWeapon.projectileSpeed = parseFloatSafe(weaponProjectileSpeedField, editingWeapon.projectileSpeed);
        editingWeapon.projectileLifetime = parseFloatSafe(weaponProjectileLifetimeField, editingWeapon.projectileLifetime);
        editingWeapon.projectileRadius = parseFloatSafe(weaponProjectileRadiusField, editingWeapon.projectileRadius);
        String ts = weaponTurretSpriteField.getText().trim();
        editingWeapon.turretSprite = ts.isBlank() ? null : ts;
        editingWeapon.turnRateDegPerSec = parseFloatSafe(weaponTurnRateField, editingWeapon.turnRateDegPerSec);
        editingWeapon.homing = weaponHomingCheck.isChecked();
        editingWeapon.turretCompatible = weaponTurretCompatCheck.isChecked();
        editingWeapon.hardpointCompatible = weaponHardpointCompatCheck.isChecked();
        editingWeapon.homingTurnRateDegPerSec = parseFloatSafe(weaponHomingTurnField, editingWeapon.homingTurnRateDegPerSec);
    }

    private void applyEditorFieldsToData() {
        ShipData d = shipEditor.getShipData();
        if (d == null) return;

        d.speed = parseFloatSafe(speedField, d.speed);
        d.maneuverability = parseFloatSafe(maneuverField, d.maneuverability);
        d.cargoSpace = parseFloatSafe(cargoField, d.cargoSpace);
        if (combatProfileSelect != null) {
            d.combatProfile = combatProfileSelect.getSelected();
        }
        d.hullTurnDegPerSec = parseFloatSafe(hullTurnField, d.hullTurnDegPerSec);
        d.normalizeCombatProfile();
    }

    private float parseFloatSafe(TextField field, float fallback) {
        if (field == null) return fallback;
        try {
            return Float.parseFloat(field.getText().trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String symmetryButtonText() {
        return shipEditor.isSymmetryEnabled() ? "Symmetry: ON" : "Symmetry: OFF";
    }

    private CheckBox makeLayerCheckbox(String title, boolean initial, java.util.function.Consumer<Boolean> onChange) {
        CheckBox cb = new CheckBox(title, skin);
        cb.setChecked(initial);
        cb.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                onChange.accept(cb.isChecked());
            }
        });
        return cb;
    }

    private void refreshSelectionUI() {
        if (mountEditorInfoLabel != null) {
            mountEditorInfoLabel.setText(shipEditor.getSelectionSummary());
        }
        boolean weaponSelected = shipEditor.getSelectionKind() == ShipEditor.SelectionKind.WEAPON;
        if (equipWeaponButton != null) {
            equipWeaponButton.setVisible(weaponSelected);
        }
        if (slotTypeToggleButton != null) {
            slotTypeToggleButton.setVisible(weaponSelected);
        }
        boolean canDelete = shipEditor.getSelectionKind() == ShipEditor.SelectionKind.WEAPON
            || shipEditor.getSelectionKind() == ShipEditor.SelectionKind.ENGINE
            || shipEditor.getSelectionKind() == ShipEditor.SelectionKind.COLLIDER;
        boolean outerBoundsSelected = shipEditor.getSelectionKind() == ShipEditor.SelectionKind.OUTER_BOUNDS;
        if (deleteSelectionButton != null) {
            deleteSelectionButton.setVisible(canDelete);
        }
        if (mountActionsRow != null) {
            mountActionsRow.setVisible(weaponSelected || canDelete || outerBoundsSelected);
        }
    }

    private void refreshMountEditorLabels() {
        refreshSelectionUI();
    }

    private void openEquipWeaponDialog() {
        WeaponSlot slot = shipEditor.getSelectedWeaponSlot();
        if (slot == null) return;

        final ArrayList<Texture> previewTextures = new ArrayList<>();
        Dialog dialog = new Dialog("Equip weapon", skin, "dialog") {
            @Override
            protected void result(Object object) {
                disposePickerTextures(previewTextures);
            }
        };
        dialog.getContentTable().defaults().pad(4f).growX();
        List<WeaponData> weapons = WeaponDataIO.listAllWeapons();
        if (weapons.isEmpty()) {
            dialog.getContentTable().add(new Label("No weapons found. Add JSON under mods/<mod>/weapons/", skin)).row();
        } else {
            Table list = new Table(skin);
            for (WeaponData wd : weapons) {
                if (!wd.canEquipOn(slot.type)) continue;
                Table row = new Table(skin);
                row.defaults().pad(4f);
                if (wd.turretSprite != null && !wd.turretSprite.isBlank()) {
                    FileHandle spriteFile = WeaponDataIO.resolveTextureFile(wd.turretSprite);
                    addTexturePreviewCell(row, spriteFile, previewTextures, skin);
                } else {
                    row.add(new Label("(no sprite)", skin)).size(64f, 64f).padRight(8f);
                }
                TextButton pick = new TextButton(wd.id + " — " + wd.name, skin);
                pick.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        shipEditor.setSelectedWeaponEquippedId(wd.id);
                        refreshSelectionUI();
                        disposePickerTextures(previewTextures);
                        dialog.hide();
                    }
                });
                row.add(pick).growX();
                list.add(row).growX().width(420f).row();
            }
            ScrollPane scroll = new ScrollPane(list, skin);
            scroll.setFadeScrollBars(false);
            scroll.setScrollingDisabled(true, false);
            dialog.getContentTable().add(scroll).width(440f).height(260f).row();
        }

        TextButton clear = new TextButton("Clear slot", skin);
        clear.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                shipEditor.setSelectedWeaponEquippedId(null);
                refreshSelectionUI();
                disposePickerTextures(previewTextures);
                dialog.hide();
            }
        });
        dialog.getContentTable().add(clear).row();

        dialog.button("Close", true);
        dialog.show(stage);
    }

    public void update(float deltaTime) {
        if (mode == Mode.EDIT_SHIP) {
            shipEditor.update(deltaTime);
            refreshSelectionUI();
        } else if (mode == Mode.EDIT_SYSTEM) {
            starSystemEditor.update(deltaTime);
            refreshSystemSelectionUI();
        }
    }

    public void render() {
        if (mode == Mode.EDIT_SHIP) {
            shipEditor.render();
        } else if (mode == Mode.EDIT_SYSTEM) {
            starSystemEditor.render();
        }
    }

    public void resize(int width, int height) {
        if (mode == Mode.EDIT_SHIP) {
            layoutShipEditorPanels();
        } else if (mode == Mode.EDIT_SYSTEM) {
            layoutSystemEditorPanels();
        } else {
            shipEditor.setViewportInsets(0f, 0f, 0f, 0f);
            shipEditor.resize(width, height);
            starSystemEditor.setViewportInsets(0f, 0f, 0f, 0f);
            starSystemEditor.resize(width, height);
        }
    }

    public void dispose() {
        disposeWeaponPreviewTexture();
        shipEditor.dispose();
        starSystemEditor.dispose();
    }
}
