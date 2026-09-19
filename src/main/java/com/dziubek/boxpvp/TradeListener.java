package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TradeListener implements Listener {

    private final BoxPvpPlugin plugin;

    public TradeListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeGuiHolder) || !(event.getWhoClicked() instanceof Player)) {
            return;
        }
        TradeGuiHolder holder = (TradeGuiHolder) event.getInventory().getHolder();
        plugin.getTrades().handleClick(event, holder.getTrade(), (Player) event.getWhoClicked());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TradeGuiHolder)) {
            return;
        }
        TradeGuiHolder holder = (TradeGuiHolder) event.getInventory().getHolder();
        plugin.getTrades().cancel(holder.getTrade());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        TradeManager.ActiveTrade trade = plugin.getTrades().getActiveTrade(event.getPlayer().getUniqueId());
        if (trade != null) {
            plugin.getTrades().cancel(trade);
        }
    }
}
