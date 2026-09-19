package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class MarketListener implements Listener {

    private final BoxPvpPlugin plugin;

    public MarketListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MarketGuiHolder)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return; // klik we własnym ekwipunku gracza - normalne
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        MarketGuiHolder holder = (MarketGuiHolder) event.getInventory().getHolder();

        if (slot == MarketGuiManager.PREV_SLOT) {
            plugin.getMarketGui().open(player, holder.getPage() - 1);
            return;
        }
        if (slot == MarketGuiManager.NEXT_SLOT) {
            plugin.getMarketGui().open(player, holder.getPage() + 1);
            return;
        }

        List<String> ids = holder.getListingIds();
        if (slot >= ids.size()) {
            return;
        }
        plugin.getMarket().buy(player, ids.get(slot));
        plugin.getMarketGui().open(player, holder.getPage());
    }
}
