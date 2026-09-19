package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class LmsListener implements Listener {

    private final BoxPvpPlugin plugin;

    public LmsListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!plugin.getLms().isAlive(victim.getUniqueId())) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.getLms().onDeath(victim);
    }

    /**
     * Ekwipunek/HP/lokację przywracamy dopiero TU (nie w PlayerDeathEvent) - nie da się
     * bezpiecznie teleportować/nadpisywać ekwipunku jeszcze martwej postaci.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getLms().consumePendingRestore(player.getUniqueId())) {
            return;
        }
        Location returnLoc = plugin.getLms().getReturnLocation(player.getUniqueId());
        if (returnLoc != null) {
            event.setRespawnLocation(returnLoc);
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getLms().finishRestore(player));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getLms().onQuit(event.getPlayer());
    }
}
