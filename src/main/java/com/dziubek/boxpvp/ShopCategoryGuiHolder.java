package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ShopCategoryGuiHolder implements InventoryHolder {

    private final String categoryId;

    public ShopCategoryGuiHolder(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
