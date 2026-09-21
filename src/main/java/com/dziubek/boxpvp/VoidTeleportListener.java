package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/** Gdy gracz wpadnie do void'u (poza mapą) - zamiast dalej spadać i umrzeć, od razu ląduje na spawnie. */
public class VoidTeleportListener implements Listener {

    private final BoxPvpPlugin plugin;

    public VoidTeleportListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!plugin.hasSurvivalSpawn()) {
            return;
        }
        event.setCancelled(true);
        player.setFallDistance(0f);
        player.teleport(plugin.getSurvivalSpawn());
        player.sendMessage("§cWpadłeś do void'a - teleportacja na spawn.");
    }
}
