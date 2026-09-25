package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Znacznik GUI wyboru skrzydeł (WingGuiManager) - identyfikuje kliknięcia w WingGuiListener. */
public class WingGuiHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
