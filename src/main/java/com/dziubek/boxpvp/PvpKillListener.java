package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

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
        }
    }
}
