package com.dziubek.boxpvp;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class FirstJoinSpawnListener implements Listener {

    private final BoxPvpPlugin plugin;

    public FirstJoinSpawnListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.hasPlayedBefore()) {
            return;
        }
        if (!plugin.hasSurvivalSpawn()) {
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.teleport(plugin.getSurvivalSpawn());
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.3);
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
            }
        });
    }
}
