package com.dziubek.boxpvp;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renderuje aktywne skrzydła gracza przez BetterModel - dokładnie jak PetDisplayManager (model
 * "przypięty" do niewidzialnego ArmorStanda-znacznika), ale w odróżnieniu od peta znacznik jest
 * SZTYWNO noszony - co tick trafia dokładnie na plecy gracza (bez opóźnienia/smyczy jak pet),
 * obracając się 1:1 z jego yaw, jak zwykła zbroja.
 */
public class WingDisplayManager {

    private static final long TICK_INTERVAL = 1L;
    // Wszystkie 12 modeli .bbmodel zostaly przecentrowane tak, ze X/Z siedzi na 0 (symetrycznie
    // wzgledem punktu zaczepienia) a Y=0 to naboda skrzydla ("korzen" przy plecach) - dokladnie w
    // punkcie zaczepienia. Skrzydla rosna W GORE od tego punktu, wiec zaczep trzeba ustawic na
    // wysokosci barkow/lopatek gracza (~1.4 bloku nad stopami)
    private static final double BACK_HEIGHT_OFFSET = 1.4;

    static final String ANCHOR_TAG_KEY = "wing_anchor";

    private final Map<UUID, ActiveWing> activeWings = new HashMap<>();

    private static class ActiveWing {
        final String species;
        final ArmorStand anchor;
        final EntityTracker tracker;
        BukkitTask task;

        ActiveWing(String species, ArmorStand anchor, EntityTracker tracker) {
            this.species = species;
            this.anchor = anchor;
            this.tracker = tracker;
        }
    }

    /** Zwraca false jeśli BetterModel nie jest zainstalowany albo model nie istnieje - nic nie stawia. */
    public boolean spawn(BoxPvpPlugin plugin, Player owner, String species) {
        despawn(owner);

        if (!BetterModelInstaller.isBetterModelPresent()) {
            owner.sendMessage("§cBetterModel nie jest zainstalowany na serwerze - skrzydła 3D nie działają.");
            return false;
        }

        var rendererOpt = BetterModel.model(BetterModelInstaller.wingModelName(species));
        if (rendererOpt.isEmpty()) {
            owner.sendMessage("§cModel skrzydeł '" + species + "' nie jest wczytany w BetterModel "
                    + "(spróbuj /bettermodel reload).");
            return false;
        }

        Location start = backLocation(owner.getLocation());
        NamespacedKey anchorTag = new NamespacedKey(plugin, ANCHOR_TAG_KEY);
        ArmorStand anchor = start.getWorld().spawn(start, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setPersistent(false);
            a.getPersistentDataContainer().set(anchorTag, PersistentDataType.BYTE, (byte) 1);
        });

        EntityTracker tracker = rendererOpt.get().getOrCreate(BukkitAdapter.adapt(anchor));
        ActiveWing active = new ActiveWing(species, anchor, tracker);
        active.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(active, owner),
                TICK_INTERVAL, TICK_INTERVAL);
        activeWings.put(owner.getUniqueId(), active);
        return true;
    }

    public void despawn(Player owner) {
        ActiveWing active = activeWings.remove(owner.getUniqueId());
        if (active == null) {
            return;
        }
        if (active.task != null) {
            active.task.cancel();
        }
        active.tracker.close();
        if (active.anchor.isValid()) {
            active.anchor.remove();
        }
    }

    public boolean hasActiveWings(Player owner) {
        return activeWings.containsKey(owner.getUniqueId());
    }

    public String activeSpecies(Player owner) {
        ActiveWing active = activeWings.get(owner.getUniqueId());
        return active == null ? null : active.species;
    }

    private void tick(ActiveWing active, Player owner) {
        if (!owner.isOnline() || !active.anchor.isValid()) {
            return;
        }
        active.anchor.teleport(backLocation(owner.getLocation()));
    }

    private static Location backLocation(Location ownerLoc) {
        Location loc = ownerLoc.clone();
        loc.setY(loc.getY() + BACK_HEIGHT_OFFSET);
        loc.setPitch(0);
        return loc;
    }
}
