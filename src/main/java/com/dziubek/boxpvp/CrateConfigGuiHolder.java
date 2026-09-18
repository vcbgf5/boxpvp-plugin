package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CrateConfigGuiHolder implements InventoryHolder {

    private final String crateName;

    public CrateConfigGuiHolder(String crateName) {
        this.crateName = crateName;
    }

    public String getCrateName() {
        return crateName;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
