package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class CratePreviewGuiHolder implements InventoryHolder {

    private final String crateName;

    public CratePreviewGuiHolder(String crateName) {
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
