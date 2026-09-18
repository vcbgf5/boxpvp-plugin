package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TraderEditorGuiHolder implements InventoryHolder {

    private final String traderName;

    public TraderEditorGuiHolder(String traderName) {
        this.traderName = traderName;
    }

    public String getTraderName() {
        return traderName;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
