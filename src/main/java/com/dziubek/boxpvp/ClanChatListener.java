package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/** Dokleja tag klanu przed wiadomością na czacie - niski priorytet, żeby nie nadpisywać innych formaterów czatu. */
public class ClanChatListener implements Listener {

    private final BoxPvpPlugin plugin;

    public ClanChatListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String tag = plugin.getClans().getClanTag(player.getUniqueId());
        if (tag == null) {
            return;
        }
        event.setFormat("§7[§f" + plugin.getClans().getDisplayTag(tag) + "§7] " + event.getFormat());
    }
}
