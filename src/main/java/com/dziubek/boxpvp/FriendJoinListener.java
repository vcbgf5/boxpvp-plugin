package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class FriendJoinListener implements Listener {

    private final BoxPvpPlugin plugin;

    public FriendJoinListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joined = event.getPlayer();
        for (UUID watcherUuid : plugin.getFriends().whoHasFriend(joined.getUniqueId())) {
            Player watcher = Bukkit.getPlayer(watcherUuid);
            if (watcher != null && watcher.isOnline()) {
                watcher.sendMessage(Branding.chatPrefix() + "§eTwój znajomy §f" + joined.getName() + " §edołączył do gry!");
            }
        }
    }
}
