package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class RotatingShopListener implements Listener {

    private final BoxPvpPlugin plugin;

    public RotatingShopListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RotatingShopGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        int slot = -1;
        for (int i = 0; i < RotatingShopGuiManager.OFFER_SLOTS.length; i++) {
            if (RotatingShopGuiManager.OFFER_SLOTS[i] == rawSlot) {
                slot = i;
                break;
            }
        }
        if (slot == -1) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        if (plugin.getRotatingShop().buy(player, slot)) {
            plugin.getRotatingShopGui().open(player);
        }
    }
}
