package io.github.cygnus_engine;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

public class TradeScreen extends Window {
    private Cargo playerCargo;
    private Cargo otherCargo;
    private Skin skin;
    private Table contentTable;
    private int[] moneyRef; // Player's money reference

    public TradeScreen(Skin skin, Cargo playerCargo, int[] moneyRef, Cargo otherCargo) {
        super("Trade", skin, "border");
        this.skin = skin;
        this.playerCargo = playerCargo;
        this.otherCargo = otherCargo;
        this.moneyRef = moneyRef;

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

        // Money display
        Label moneyLabel = new Label("Money: $" + moneyRef[0], skin);
        contentTable.add(moneyLabel).colspan(11).padBottom(8f).row();

        // Header
        contentTable.add(new Label("Commodity", skin)).width(120f);
        contentTable.add(new Label("Price", skin)).width(80f);
        //contentTable.add(new Label("Base", skin)).width(80f);
        contentTable.add(new Label("Owned", skin)).width(80f);
        contentTable.add(new Label("Modifier", skin)).width(80f);
        contentTable.add(new Label("Buy", skin)).colspan(3).center();
        contentTable.add(new Label("", skin)).width(80f);
        contentTable.add(new Label("Owned", skin)).width(80f);
        contentTable.add(new Label("Modifier", skin)).width(80f);
        contentTable.add(new Label("Sell", skin)).colspan(3).center();
        contentTable.row();

        // Commodity rows
        for (Commodity commodity : Commodity.values()) {
            //int basePrice = commodity.getBasePrice();
            double modifier = otherCargo.getModifier(commodity);
            int modifiedPrice = otherCargo.getModifiedPrice(commodity);
            Double owned = playerCargo.getQuantity(commodity);
            Double ownedByOther = otherCargo.getQuantity(commodity);

            // Commodity name
            contentTable.add(new Label(commodity.getName(), skin)).width(120f);

            // Modified price
            contentTable.add(new Label("$" + modifiedPrice, skin)).width(80f);

            // Base price
            //contentTable.add(new Label("$" + basePrice, skin)).width(80f);

            // Other ship Owned
            contentTable.add(new Label(String.valueOf(ownedByOther), skin)).width(80f);

            // Modifier (colored)
            Label modLabel = new Label(String.format("%+.0f%%", modifier * 100), skin);
            if (modifier > 0) {
                modLabel.setColor(Color.RED);
            } else if (modifier < 0) {
                modLabel.setColor(Color.GREEN);
            }
            contentTable.add(modLabel).width(80f).right();

            // Buy buttons
            for (int amount : new int[]{1, 10, 100}) {
                TextButton buyButton = new TextButton(String.valueOf(amount), skin);
                final int buyAmount = amount;
                final Commodity buyCommodity = commodity;
                buyButton.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        int unitCost = otherCargo.getModifiedPrice(buyCommodity);
                        int cost = unitCost * buyAmount;
                        if (otherCargo != null) {
                            // Buy from otherCargo
                            if (otherCargo.getQuantity(buyCommodity) >= buyAmount && playerCargo.getFreeSpace() >= buyAmount && moneyRef[0] >= cost) {
                                otherCargo.removeCommodity(buyCommodity, Double.valueOf(buyAmount));
                                playerCargo.addCommodity(buyCommodity, Double.valueOf(buyAmount));
                                moneyRef[0] -= cost;
                                updateContent();
                            }
                        } else {
                            // Buy to playerCargo
                            if (moneyRef[0] >= cost && playerCargo.getFreeSpace() >= buyAmount) {
                                playerCargo.addCommodity(buyCommodity, Double.valueOf(buyAmount));
                                moneyRef[0] -= cost;
                                updateContent();
                            }
                        }
                    }
                });
                contentTable.add(buyButton).width(50f);
            }

            contentTable.add(new Label("", skin)).width(80f);
            
            // Owned
            contentTable.add(new Label(String.valueOf(owned), skin)).width(80f);

            // Modifier (colored)
            Label modLabelSell = new Label(String.format("%+.0f%%", modifier * 100), skin);
            if (modifier > 0) {
                modLabelSell.setColor(Color.GREEN);
            } else if (modifier < 0) {
                modLabelSell.setColor(Color.RED);
            }
            contentTable.add(modLabelSell).width(80f).right();

            // Sell buttons
            for (int amount : new int[]{1, 10, 100}) {
                TextButton sellButton = new TextButton(String.valueOf(amount), skin);
                final int sellAmount = amount;
                final Commodity sellCommodity = commodity;
                sellButton.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        int unitPrice = otherCargo.getModifiedPrice(sellCommodity);
                        if (otherCargo != null) {
                            // Sell to otherCargo
                            if (playerCargo.getQuantity(sellCommodity) >= sellAmount && otherCargo.getFreeSpace() >= sellAmount) {
                                playerCargo.removeCommodity(sellCommodity, Double.valueOf(sellAmount));
                                otherCargo.addCommodity(sellCommodity, Double.valueOf(sellAmount));
                                moneyRef[0] += unitPrice * sellAmount;
                                updateContent();
                            }
                        } else {
                            // Sell from playerCargo
                            if (playerCargo.getQuantity(sellCommodity) >= sellAmount) {
                                playerCargo.removeCommodity(sellCommodity, Double.valueOf(sellAmount));
                                moneyRef[0] += unitPrice * sellAmount;
                                updateContent();
                            }
                        }
                    }
                });
                contentTable.add(sellButton).width(50f);
            }

            contentTable.row();
        }

        // Cargo space info
        Label cargoLabel = new Label("Cargo: " + playerCargo.getUsedSpace() + "/" + playerCargo.getMaxSpace(), skin);
        contentTable.add(cargoLabel).colspan(11).padTop(8f).row();

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
        contentTable.add(closeButton).colspan(11).padTop(8f);
    }

    public int getMoney() {
        return moneyRef[0];
    }
}
