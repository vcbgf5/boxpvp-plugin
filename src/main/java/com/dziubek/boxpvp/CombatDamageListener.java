package com.dziubek.boxpvp;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

public class CombatDamageListener implements Listener {

    private final BoxPvpPlugin plugin;

    public CombatDamageListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player victim = (Player) event.getEntity();

        Player attacker = resolvePlayerAttacker(event);
        if (attacker == null) {
            return;
        }
        if (attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        if (plugin.getParty().sameParty(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        long duration = plugin.getCombatDurationSeconds();
        alertIfFreshTag(victim, duration);
        alertIfFreshTag(attacker, duration);
    }

    /**
     * Tytuł+dźwięk tylko przy PIERWSZYM otagowaniu danej walki - kolejne trafienia w trakcie
     * trwającego już tagu tylko odświeżają czas, bez powtarzania alertu na ekranie.
     */
    private void alertIfFreshTag(Player player, long duration) {
        boolean wasAlreadyTagged = plugin.getCombatManager().isTagged(player.getUniqueId());
        plugin.getCombatManager().tag(player.getUniqueId(), duration);

        if (!wasAlreadyTagged) {
            TitleUtil.show(player, "§c§l⚔ WALKA!", "§7Nie wychodź z gry przez " + duration + "s!");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 1.8f);
        }
    }

    private Player resolvePlayerAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            return (Player) event.getDamager();
        }
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player) {
                return (Player) source;
            }
        }
        return null;
    }
}
