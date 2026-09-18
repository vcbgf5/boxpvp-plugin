package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/** Trzyma mapowanie slot -> definicja misji (null dla slotów dekoracyjnych), do obsługi kliknięć. */
public class MissionsGuiHolder implements InventoryHolder {

    private final List<MissionManager.Definition> slotDefinitions;

    public MissionsGuiHolder(List<MissionManager.Definition> slotDefinitions) {
        this.slotDefinitions = slotDefinitions;
    }

    public MissionManager.Definition definitionAt(int slot) {
        return slot >= 0 && slot < slotDefinitions.size() ? slotDefinitions.get(slot) : null;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
