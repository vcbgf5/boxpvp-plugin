package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class BankChatListener implements Listener {

    private final BoxPvpPlugin plugin;

    public BankChatListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getBanks().hasPendingAmount(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getBanks().handleAmountChatInput(player, message));
    }
}
