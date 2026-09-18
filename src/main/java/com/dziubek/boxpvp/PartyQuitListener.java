package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PartyQuitListener implements Listener {

    private final BoxPvpPlugin plugin;

    public PartyQuitListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Party remaining = plugin.getParty().disconnect(player.getUniqueId());
        if (remaining == null) {
            return;
        }
        for (UUID memberUuid : remaining.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage("§e" + player.getName() + " wyszedł z gry i opuścił drużynę.");
            }
        }
    }
}
