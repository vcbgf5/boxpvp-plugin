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
        if (holder.getKind() != MatchmakingGuiHolder.Kind.JOIN_PROMPT) {
            return;
        }
        if (event.getRawSlot() != 13) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        player.closeInventory();
        plugin.getMatchmaking().awaitBet(player);
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
