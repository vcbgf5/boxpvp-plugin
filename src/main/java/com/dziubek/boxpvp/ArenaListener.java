package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Obsługuje różdżkę generatora (zaznaczanie pos1/pos2) oraz cykl życia gracza w rundzie
 * Box PvP: śmierć = eliminacja, respawn = powrót do lobby areny, wyjście z serwera = leave.
 */
public class ArenaListener implements Listener {

    private final BoxPvpPlugin plugin;

    public ArenaListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !plugin.getGenerators().isWand(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.getGenerators().setPos1(player, event.getClickedBlock().getLocation());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.getGenerators().setPos2(player, event.getClickedBlock().getLocation());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getArenas().onPlayerDeath(event.getEntity());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getArenas().onPlayerRespawn(event.getPlayer(), event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getArenas().leave(event.getPlayer());
    }
}
