package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public class MarketGuiHolder implements InventoryHolder {

    private final int page;
    private final List<String> listingIds;

    public MarketGuiHolder(int page, List<String> listingIds) {
        this.page = page;
        this.listingIds = listingIds;
    }

    public int getPage() {
        return page;
    }

    public List<String> getListingIds() {
        return listingIds;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
