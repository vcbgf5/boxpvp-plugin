package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public class DuelListener implements Listener {

    private final BoxPvpPlugin plugin;

    public DuelListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!plugin.getDuels().hasActiveDuel(victim.getUniqueId())) {
            return;
        }
        UUID opponent = plugin.getDuels().getOpponent(victim.getUniqueId());
        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.getDuels().finish(opponent, victim.getUniqueId(), true);
    }

    /**
     * Ekwipunek/HP/lokację przegranego przywracamy dopiero TU (nie w PlayerDeathEvent) - nie da
     * się bezpiecznie teleportować/nadpisywać ekwipunku jeszcze martwej postaci.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DuelManager.PendingRestore pending = plugin.getDuels().consumePendingRestore(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if (pending.getReturnLocation() != null) {
            event.setRespawnLocation(pending.getReturnLocation());
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getDuels().applySnapshot(player, pending.getSnapshot()));
    }

    /**
     * Podczas wejścia (spadanie z góry) i odliczania przed startem gracz jest "zamrożony" -
     * nie może się ruszyć, ale może się rozglądać (yaw/pitch przechodzą, pozycja nie).
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDuels().isFrozen(player.getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }
        event.setTo(new Location(to.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDuels().hasActiveDuel(player.getUniqueId())) {
            return;
        }
        plugin.getDuels().forfeit(player);
    }
}
