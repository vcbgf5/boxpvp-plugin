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
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renderuje aktywnego peta gracza przez BetterModel (zewnętrzny plugin - patrz
 * BetterModelInstaller) zamiast własnej matematyki hierarchii kości: model jest "przypięty"
 * (EntityTracker) do niewidzialnego ArmorStanda-znacznika, którego po prostu przesuwamy w
 * świecie (teleport co tick, wygładzony) - BetterModel sam renderuje/animuje model śledząc
 * pozycję tego znacznika, więc nie musimy sami liczyć pivotów ani klatek animacji.
 */
public class PetDisplayManager {

    private static final long TICK_INTERVAL = 2L;
    private static final double FOLLOW_DISTANCE = 1.6;
    private static final double SMOOTH_FACTOR = 0.22;
    private static final double SNAP_DISTANCE = 10.0;
    private static final double MOVING_EPSILON = 0.02;

    /**
     * Tag na znaczniku (ArmorStand) peta - odczytywany przez PetInteractListener, żeby zawsze
     * przepuścić spawn peta nawet w strefie z zablokowanym spawnowaniem mobów (np. flaga
     * WorldGuard mob-spawning/DENY na spawnie) - to nasz WŁASNY, celowy spawn, nie naturalny mob.
     */
    static final String ANCHOR_TAG_KEY = "pet_anchor";

    private final Map<UUID, ActivePet> activePets = new HashMap<>();

    private static class ActivePet {
        final String species;
        final ArmorStand anchor;
        final EntityTracker tracker;
        String currentAnim;
        BukkitTask task;

        ActivePet(String species, ArmorStand anchor, EntityTracker tracker) {
            this.species = species;
            this.anchor = anchor;
            this.tracker = tracker;
        }
    }

    /** Zwraca false jeśli BetterModel nie jest zainstalowany albo model nie istnieje - nic nie stawia. */
    public boolean spawn(BoxPvpPlugin plugin, Player owner, String species) {
        despawn(owner);

        if (!BetterModelInstaller.isBetterModelPresent()) {
            owner.sendMessage("§cBetterModel nie jest zainstalowany na serwerze - pety 3D nie działają.");
            return false;
        }

        var rendererOpt = BetterModel.model(BetterModelInstaller.modelName(species));
        if (rendererOpt.isEmpty()) {
            owner.sendMessage("§cModel peta '" + species + "' nie jest wczytany w BetterModel "
                    + "(spróbuj /bettermodel reload).");
            return false;
        }

        Location start = owner.getLocation().clone();
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
        ActivePet active = new ActivePet(species, anchor, tracker);
        active.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(active, owner),
                TICK_INTERVAL, TICK_INTERVAL);
        activePets.put(owner.getUniqueId(), active);
        return true;
    }

    public void despawn(Player owner) {
        ActivePet active = activePets.remove(owner.getUniqueId());
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

    public boolean hasActivePet(Player owner) {
        return activePets.containsKey(owner.getUniqueId());
    }

    public String activeSpecies(Player owner) {
        ActivePet active = activePets.get(owner.getUniqueId());
        return active == null ? null : active.species;
    }

    private void tick(ActivePet active, Player owner) {
        if (!owner.isOnline() || !active.anchor.isValid()) {
            return;
        }

        boolean changedWorld = !owner.getWorld().equals(active.anchor.getWorld());
        Location anchorLoc = active.anchor.getLocation();

        Location ownerLoc = owner.getLocation();
        Vector3f behind = new Vector3f((float) -Math.sin(Math.toRadians(ownerLoc.getYaw())), 0,
                (float) Math.cos(Math.toRadians(ownerLoc.getYaw())));
        Location target = ownerLoc.clone().add(behind.x() * FOLLOW_DISTANCE, 0, behind.z() * FOLLOW_DISTANCE);

        double distance = changedWorld ? Double.MAX_VALUE : target.distance(anchorLoc);
        boolean moving;

        if (changedWorld || distance > SNAP_DISTANCE) {
            active.anchor.teleport(target);
            moving = false;
        } else if (distance > MOVING_EPSILON) {
            double dx = (target.getX() - anchorLoc.getX()) * SMOOTH_FACTOR;
            double dy = (target.getY() - anchorLoc.getY()) * SMOOTH_FACTOR;
            double dz = (target.getZ() - anchorLoc.getZ()) * SMOOTH_FACTOR;
            Location next = anchorLoc.clone().add(dx, dy, dz);
            moving = distance > 0.08;
            if (moving) {
                double moveYaw = Math.toDegrees(Math.atan2(target.getX() - anchorLoc.getX(),
                        -(target.getZ() - anchorLoc.getZ())));
                next.setYaw(smoothAngle(anchorLoc.getYaw(), (float) moveYaw, 0.35f));
            } else {
                next.setYaw(anchorLoc.getYaw());
            }
            active.anchor.teleport(next);
        } else {
            moving = false;
        }

        String wantAnim = moving ? "walk" : "idle";
        if (!wantAnim.equals(active.currentAnim)) {
            active.tracker.animate(wantAnim);
            active.currentAnim = wantAnim;
        }
    }

    private static float smoothAngle(float current, float target, float factor) {
        float diff = ((target - current + 540f) % 360f) - 180f;
        return current + diff * factor;
    }
}
