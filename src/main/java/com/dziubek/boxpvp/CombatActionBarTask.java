package com.dziubek.boxpvp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class CombatActionBarTask implements Runnable {

    private final BoxPvpPlugin plugin;

    public CombatActionBarTask(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getCombatManager().isTagged(player.getUniqueId())) {
                continue;
            }
            long left = plugin.getCombatManager().secondsLeft(player.getUniqueId());
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§c⚔ Walka: §f" + left + "s"));
        }
    }
}
