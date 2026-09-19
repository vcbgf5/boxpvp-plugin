package com.dziubek.boxpvp;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class PvpKillListener implements Listener {

    private final BoxPvpPlugin plugin;

    public PvpKillListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        plugin.getStats().recordDeath(victim.getUniqueId(), victim.getName());
        plugin.getKillstreaks().onDeath(victim);

        Player killer = victim.getKiller();
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            plugin.getStats().recordKill(killer.getUniqueId(), killer.getName());
            plugin.getKillstreaks().onKill(killer);
            plugin.getMissions().addProgress(killer, MissionManager.Type.KILLS, 1);
            playKillEffect(killer, victim);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, event.getRespawnLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.3);
        player.playSound(event.getRespawnLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);
    }

    private void playKillEffect(Player killer, Player victim) {
        victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0), 3, 0.3, 0.5, 0.3, 0);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 0.6f);
        FloatingTextEffect.show(plugin, victim.getLocation().add(0, 2.2, 0), "§c☠ " + victim.getName());
    }
}
