package com.dziubek.boxpvp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;

/**
 * Gracz kopnięty (np. przez anticheat/proxy) podczas walki nie traci itemów.
 * PlayerKickEvent zawsze poprzedza PlayerQuitEvent przy tym samym rozłączeniu,
 * więc wyczyszczenie tagu tutaj sprawia, że CombatQuitListener nic już nie ukarze.
 */
public class CombatKickListener implements Listener {

    private final BoxPvpPlugin plugin;

    public CombatKickListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onKick(PlayerKickEvent event) {
        plugin.getCombatManager().clear(event.getPlayer().getUniqueId());
    }
}
