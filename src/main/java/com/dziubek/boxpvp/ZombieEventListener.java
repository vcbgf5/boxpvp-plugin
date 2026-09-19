package com.dziubek.boxpvp;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Zombie-event: bez obrażeń od upadku, nie płonie w słońcu, jego wanilijne ataki na graczy są
 * anulowane (ZombieEventManager robi kontrolowany atak sam), a śmierć zgłasza nagrodę.
 * Ataki GRACZA na zombie zostają nietknięte - inaczej nie dałoby się go zabić.
 */
public class ZombieEventListener implements Listener {

    private final BoxPvpPlugin plugin;

    public ZombieEventListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Zombie) || !plugin.getZombieEvent().isTracked(event.getEntity())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Zombie) || !plugin.getZombieEvent().isTracked(damager)) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Zombie && plugin.getZombieEvent().isTracked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Zombie && plugin.getZombieEvent().isTracked(event.getEntity())) {
            plugin.getZombieEvent().onKilled((Zombie) event.getEntity());
        }
    }
}
