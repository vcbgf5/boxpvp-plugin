package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MatchmakingListener implements Listener {

    private final BoxPvpPlugin plugin;

    public MatchmakingListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MatchmakingGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        MatchmakingGuiHolder holder = (MatchmakingGuiHolder) event.getInventory().getHolder();
        Player player = (Player) event.getWhoClicked();

        if (holder.getKind() == MatchmakingGuiHolder.Kind.JOIN_PROMPT) {
            int slot = event.getRawSlot();
            if (slot == 11) {
                player.closeInventory();
                plugin.getMatchmaking().awaitBet(player, false);
            } else if (slot == 15) {
                player.closeInventory();
                plugin.getMatchmaking().awaitBet(player, true);
            }
            return;
        }

        if (holder.getKind() == MatchmakingGuiHolder.Kind.PREVIEW) {
            int slot = event.getRawSlot();
            if (slot == 2) {
                plugin.getMatchmaking().accept(player);
            } else if (slot == 6) {
                plugin.getMatchmaking().decline(player);
            }
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getMatchmaking().hasPendingBet(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        String message = event.getMessage().trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getMatchmaking().handleBetChatInput(player, message));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getMatchmaking().handleQuit(event.getPlayer().getUniqueId());
    }
}
