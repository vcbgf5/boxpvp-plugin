package com.dziubek.boxpvp;

import org.bukkit.entity.Giant;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Mega-zombie (Giant): bez obrażeń od upadku, nie płonie w słońcu, śmierć zgłasza nagrodę.
 * Giant nie ma żadnej wanilijnej AI ataku (Mojang go zostawił pustego) - jedyne obrażenia,
 * jakie zadaje graczom, to celowe Player#damage(...) wołane przez GiantEventManager (rzut
 * blokiem, kolce), więc NIE anulujemy tu EntityDamageByEntityEvent z Giantem jako damagerem -
 * to zablokowałoby własny, zamierzony atak pluginu. Ataki GRACZA na Gianta zostają nietknięte.
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
