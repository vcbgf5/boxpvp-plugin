package com.dziubek.boxpvp;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renderuje aktywnego peta gracza przez BetterModel (zewnętrzny plugin - patrz
 * BetterModelInstaller) zamiast własnej matematyki hierarchii kości: model jest "przypięty"
 * (EntityTracker) do niewidzialnego ArmorStanda-znacznika, którego po prostu przesuwamy w
 * świecie (teleport co tick, wygładzony) - BetterModel sam renderuje/animuje model śledząc
 * pozycję tego znacznika, więc nie musimy sami liczyć pivotów ani klatek animacji.
 *
 * Podążanie jest oparte na SMYCZY (dystans do właściciela) i trzyma się ziemi - NIE reaguje na
 * samo obracanie kamery gracza (jak wcześniej), tylko na jego faktyczny ruch/pozycję, dokładnie
 * jak wanilijne oswojone zwierzę.
 */
public class PetDisplayManager {

    private static final long TICK_INTERVAL = 2L;

    // smycz: idzie gdy dalej niz START, przestaje gdy blizej niz STOP (histereza, zeby nie
    // "drgal" w miejscu na granicy)
    private static final double LEASH_FOLLOW_DISTANCE = 3.0;
    private static final double LEASH_STOP_DISTANCE = 1.5;
    private static final double MOVE_PER_TICK = 0.5;
    private static final double SNAP_DISTANCE = 14.0;

    // szukanie podloza pod celem (zeby pet "chodzil", a nie "latal" na wysokosci gracza)
    private static final int GROUND_SCAN_UP = 2;
    private static final int GROUND_SCAN_DOWN = 6;

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
        boolean following = false;
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
        NamespacedKey ownerTag = new NamespacedKey(plugin, "pet_owner");
        ArmorStand anchor = start.getWorld().spawn(start, ArmorStand.class, a -> {
            a.setInvisible(true);
            // NIE marker - pet musi miec malutki hitbox, zeby gracz mogl kliknac PPM (trick)
            a.setMarker(false);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setPersistent(false);
            a.getPersistentDataContainer().set(anchorTag, PersistentDataType.BYTE, (byte) 1);
            a.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, owner.getUniqueId().toString());
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

    /**
     * Odpala krótką animację "sztuczki" (PPM na pecie) - jednorazowo, niezależnie od pętli
     * idle/walk w tick() (AnimationModifier.PLAY_ONCE ją nadpisuje na chwilę, a potem silnik
     * sam wraca do tego co leciało wcześniej).
     */
    public void playTrick(Player owner) {
        ActivePet active = activePets.get(owner.getUniqueId());
        if (active == null) {
            return;
        }
        active.tracker.animate("pet", AnimationModifier.DEFAULT_WITH_PLAY_ONCE);
    }

    private void tick(ActivePet active, Player owner) {
        if (!owner.isOnline() || !active.anchor.isValid()) {
            return;
        }

        Location ownerLoc = owner.getLocation();
        Location anchorLoc = active.anchor.getLocation();
        boolean changedWorld = !owner.getWorld().equals(active.anchor.getWorld());

        double flatDistance = changedWorld ? Double.MAX_VALUE
                : Math.hypot(ownerLoc.getX() - anchorLoc.getX(), ownerLoc.getZ() - anchorLoc.getZ());

        if (changedWorld || flatDistance > SNAP_DISTANCE) {
            active.anchor.teleport(grounded(ownerLoc.clone()));
            active.following = false;
            setAnim(active, "idle");
            return;
        }

        if (flatDistance > LEASH_FOLLOW_DISTANCE) {
            active.following = true;
        } else if (flatDistance < LEASH_STOP_DISTANCE) {
            active.following = false;
        }

        if (active.following) {
            double dx = ownerLoc.getX() - anchorLoc.getX();
            double dz = ownerLoc.getZ() - anchorLoc.getZ();
            double len = Math.hypot(dx, dz);
            double step = Math.max(0, Math.min(MOVE_PER_TICK, len));
            double nx = anchorLoc.getX() + (dx / len) * step;
            double nz = anchorLoc.getZ() + (dz / len) * step;

            Location next = grounded(new Location(anchorLoc.getWorld(), nx, ownerLoc.getY(), nz));
            float moveYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            next.setYaw(smoothAngle(anchorLoc.getYaw(), moveYaw, 0.35f));
            active.anchor.teleport(next);
            setAnim(active, "walk");
        } else {
            setAnim(active, "idle");
        }
    }

    /** Podmienia Y na pozycję tuż nad najbliższym stałym blokiem w pobliżu wysokości gracza - pet chodzi po ziemi, nie lata. */
    private static Location grounded(Location loc) {
        World world = loc.getWorld();
        int baseY = loc.getBlockY();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        for (int dy = GROUND_SCAN_UP; dy >= -GROUND_SCAN_DOWN; dy--) {
            int y = baseY + dy;
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid()) {
                loc.setY(y + 1);
                return loc;
            }
        }
        return loc;
    }

    private static void setAnim(ActivePet active, String anim) {
        if (!anim.equals(active.currentAnim)) {
            active.tracker.animate(anim);
            active.currentAnim = anim;
        }
    }

    private static float smoothAngle(float current, float target, float factor) {
        float diff = ((target - current + 540f) % 360f) - 180f;
        return current + diff * factor;
    }
}
