package com.dziubek.boxpvp;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Mega-zombie (Giant): bez obrażeń od upadku, nie płonie w słońcu, jego ewentualne wanilijne
 * ataki na graczy są anulowane (GiantEventManager robi kontrolowany atak sam), a śmierć zgłasza
 * nagrodę. Ataki GRACZA na Gianta zostają nietknięte - inaczej nie dałoby się go zabić.
 */
public class GiantEventListener implements Listener {

    private final BoxPvpPlugin plugin;

    public GiantEventListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Giant) || !plugin.getGiantEvent().isTracked(event.getEntity())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        if (!(damager instanceof Giant) || !plugin.getGiantEvent().isTracked(damager)) {
            return;
        }
        if (event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        if (event.getEntity() instanceof Giant && plugin.getGiantEvent().isTracked(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Giant && plugin.getGiantEvent().isTracked(event.getEntity())) {
            plugin.getGiantEvent().onKilled((Giant) event.getEntity());
        }
    }
}
