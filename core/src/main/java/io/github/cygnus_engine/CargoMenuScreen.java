package io.github.cygnus_engine;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import java.util.Map;

public class CargoMenuScreen extends Window {
    private Cargo cargo;
    private Skin skin;
    private Table contentTable;

    public CargoMenuScreen(Skin skin, Cargo cargo) {
        super("Cargo", skin, "border");
        this.skin = skin;
        this.cargo = cargo;

        setModal(true);
        setMovable(true);
        setResizable(false);

        contentTable = new Table(skin);
        contentTable.defaults().pad(4f);

        updateContent();

        add(contentTable);
        pack();
    }

    private void updateContent() {
        contentTable.clear();

        // Cargo space info
        Label spaceLabel = new Label("Cargo Space: " + cargo.getUsedSpace() + "/" + cargo.getMaxSpace(), skin);
        contentTable.add(spaceLabel).colspan(2).padBottom(8f).row();

        // Header
        contentTable.add(new Label("Commodity", skin)).width(200f);
        contentTable.add(new Label("Quantity", skin)).width(100f);
        contentTable.row();

        // Commodity rows
        Map<Commodity, Integer> commodities = cargo.getAllCommodities();
        if (commodities.isEmpty()) {
            Label emptyLabel = new Label("Cargo is empty", skin);
            contentTable.add(emptyLabel).colspan(2).padTop(8f).row();
        } else {
            for (Map.Entry<Commodity, Integer> entry : commodities.entrySet()) {
                contentTable.add(new Label(entry.getKey().getName(), skin)).width(200f);
                contentTable.add(new Label(String.valueOf(entry.getValue()), skin)).width(100f);
                contentTable.row();
            }
        }

        // Close button
        TextButton closeButton = new TextButton("Close", skin);
        closeButton.padRight(8f).padLeft(8f);
        closeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setVisible(false);
            }
        });
        contentTable.row();
        contentTable.add(closeButton).colspan(2).padTop(8f);
    }

    public void refresh() {
        updateContent();
    }
}
