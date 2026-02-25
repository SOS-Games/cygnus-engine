package io.github.cygnus_engine;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

public class TradeScreen extends Window {
    private Cargo cargo;
    private Skin skin;
    private Table contentTable;
    private int money; // Player's money

    public TradeScreen(Skin skin, Cargo cargo, int initialMoney) {
        super("Trade", skin, "border");
        this.skin = skin;
        this.cargo = cargo;
        this.money = initialMoney;

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
        Label moneyLabel = new Label("Money: $" + money, skin);
        contentTable.add(moneyLabel).colspan(9).padBottom(8f).row();

        // Header
        contentTable.add(new Label("Commodity", skin)).width(120f);
        contentTable.add(new Label("Price", skin)).width(80f);
        contentTable.add(new Label("Owned", skin)).width(80f);
        contentTable.add(new Label("Buy", skin)).colspan(3).center();
        contentTable.add(new Label("Sell", skin)).colspan(3).center();
        contentTable.row();

        // Commodity rows
        for (Commodity commodity : Commodity.values()) {
            int price = commodity.getBasePrice();
            int owned = cargo.getQuantity(commodity);

            // Commodity name
            contentTable.add(new Label(commodity.getName(), skin)).width(120f);

            // Price
            contentTable.add(new Label("$" + price, skin)).width(80f);

            // Owned
            contentTable.add(new Label(String.valueOf(owned), skin)).width(80f);

            // Buy buttons
            for (int amount : new int[]{1, 10, 100}) {
                TextButton buyButton = new TextButton(String.valueOf(amount), skin);
                final int buyAmount = amount;
                final Commodity buyCommodity = commodity;
                buyButton.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        int cost = buyCommodity.getBasePrice() * buyAmount;
                        if (money >= cost && cargo.getFreeSpace() >= buyAmount) {
                            if (cargo.addCommodity(buyCommodity, buyAmount)) {
                                money -= cost;
                                updateContent();
                            }
                        }
                    }
                });
                contentTable.add(buyButton).width(50f);
            }

            // Sell buttons
            for (int amount : new int[]{1, 10, 100}) {
                TextButton sellButton = new TextButton(String.valueOf(amount), skin);
                final int sellAmount = amount;
                final Commodity sellCommodity = commodity;
                sellButton.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        if (cargo.getQuantity(sellCommodity) >= sellAmount) {
                            if (cargo.removeCommodity(sellCommodity, sellAmount)) {
                                money += sellCommodity.getBasePrice() * sellAmount;
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
        Label cargoLabel = new Label("Cargo: " + cargo.getUsedSpace() + "/" + cargo.getMaxSpace(), skin);
        contentTable.add(cargoLabel).colspan(9).padTop(8f).row();

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
        contentTable.add(closeButton).colspan(9).padTop(8f);
    }

    public int getMoney() {
        return money;
    }
}
