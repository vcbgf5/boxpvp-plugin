package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class DailyConfigGuiHolder implements InventoryHolder {

    private final int day;

    public DailyConfigGuiHolder(int day) {
        this.day = day;
    }

    public int getDay() {
        return day;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
