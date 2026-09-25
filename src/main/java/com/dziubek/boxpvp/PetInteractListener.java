package com.dziubek.boxpvp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Odtwarza aktywnego peta gracza po wejściu na serwer, chowa go po wyjściu. */
public class PetInteractListener implements Listener {

    private final BoxPvpPlugin plugin;

    public PetInteractListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPetDisplays().despawn(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPetManager().restoreActive(event.getPlayer());
    }
}
