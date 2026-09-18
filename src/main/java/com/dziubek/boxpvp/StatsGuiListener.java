package com.dziubek.boxpvp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class StatsGuiListener implements Listener {

    public StatsGuiListener(BoxPvpPlugin plugin) {
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof StatsGuiHolder) {
            event.setCancelled(true);
        }
    }
}
