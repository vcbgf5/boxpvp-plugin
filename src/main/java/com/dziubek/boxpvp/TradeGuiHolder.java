package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class TradeGuiHolder implements InventoryHolder {

    private final TradeManager.ActiveTrade trade;

    public TradeGuiHolder(TradeManager.ActiveTrade trade) {
        this.trade = trade;
    }

    public TradeManager.ActiveTrade getTrade() {
        return trade;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
