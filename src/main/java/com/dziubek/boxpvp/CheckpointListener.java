package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public class CheckpointListener implements Listener {

    private final BoxPvpPlugin plugin;

    public CheckpointListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    /** Jeśli gracz wylogował się w trakcie sprawdzania, po powrocie wraca prosto do skrzynki. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCheckpoint().isHeld(player.getUniqueId())) {
            return;
        }
        Location point = plugin.getCheckpoint().getPoint();
        if (point != null) {
            player.teleport(point);
        }
    }

    /** Trzymany gracz nie może się ruszyć, ale nadal może się rozglądać. */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getCheckpoint().isHeld(player.getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        event.setTo(new Location(to.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
    }
}
