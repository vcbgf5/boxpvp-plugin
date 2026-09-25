package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Znacznik GUI wyboru peta (PetGuiManager) - identyfikuje kliknięcia w PetGuiListener. */
public class PetGuiHolder implements InventoryHolder {

    @Override
    public Inventory getInventory() {
        return null;
    }
}
