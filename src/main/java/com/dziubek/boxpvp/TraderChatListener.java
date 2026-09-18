package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class TraderChatListener implements Listener {

    private final BoxPvpPlugin plugin;

    public TraderChatListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getTraders().hasPendingRename(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getTraders().handleChatInput(player, message));
    }
}
