package com.dziubek.boxpvp;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.tracker.EntityTracker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;

/**
 * Renderuje 3D model skrzyni przez BetterModel (dokładnie jak pety - patrz PetDisplayManager)
 * zamiast własnej matematyki hierarchii kości. To ostatecznie naprawia błędy typu "odklejona
 * pokrywa" - BetterModel sam liczy transformacje kości na podstawie .bbmodel, my tylko stawiamy
 * niewidzialny ArmorStand-znacznik RAZ, w stałym miejscu (skrzynia nigdzie nie "podąża").
 * Blok pod skrzynią staje się niewidzialnym Material.BARRIER, model wizualnie go zastępuje.
 */
public class CrateModelDisplayManager {

    static final String ANCHOR_TAG_KEY = "crate_anchor";

    private final Map<String, Entry> entries = new HashMap<>();

    private record Entry(ArmorStand anchor, EntityTracker tracker) {
    }

    public void spawn(BoxPvpPlugin plugin, Location blockLocation, CrateModel model, float yaw) {
        despawn(blockLocation);

        if (!BetterModelInstaller.isBetterModelPresent()) {
            plugin.getLogger().warning("BetterModel nie jest zainstalowany - model 3D skrzyni '"
                    + model.name + "' nie zostanie pokazany.");
            return;
        }
        var rendererOpt = BetterModel.model(model.name);
        if (rendererOpt.isEmpty()) {
            plugin.getLogger().warning("Model skrzyni '" + model.name + "' nie jest wczytany w "
                    + "BetterModel (spróbuj /bettermodel reload).");
            return;
        }

        blockLocation.getBlock().setType(Material.BARRIER);

        Location anchorLoc = blockLocation.clone().add(0.5, 0, 0.5);
        float entityYaw = -yaw;
        anchorLoc.setYaw(entityYaw);
        NamespacedKey anchorTag = new NamespacedKey(plugin, ANCHOR_TAG_KEY);
        ArmorStand anchor = anchorLoc.getWorld().spawn(anchorLoc, ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setGravity(false);
            a.setInvulnerable(true);
            a.setSilent(true);
            a.setPersistent(false);
            a.setRotation(entityYaw, 0);
            a.getPersistentDataContainer().set(anchorTag, PersistentDataType.BYTE, (byte) 1);
        });

        EntityTracker tracker = rendererOpt.get().getOrCreate(BukkitAdapter.adapt(anchor));
        entries.put(key(blockLocation), new Entry(anchor, tracker));
    }

    public void despawn(Location blockLocation) {
        Entry entry = entries.remove(key(blockLocation));
        if (entry == null) {
            return;
        }
        entry.tracker().close();
        if (entry.anchor().isValid()) {
            entry.anchor().remove();
        }
    }

    public boolean hasModel(Location blockLocation) {
        return entries.containsKey(key(blockLocation));
    }

    /**
     * Odpala animację otwarcia pokrywy (jeśli model ją ma) - wołane dopiero gdy realnie
     * startuje losowanie (CrateRollAnimation), NIE przy samym kliknięciu kluczem w blok.
     * Gra raz i tyle - animacja "open" ma loop=hold, więc "utyka" otwarta na ostatniej
     * klatce (bez późniejszego automatycznego odgrywania "close").
     */
    public void playOpenAnimation(Location blockLocation) {
        Entry entry = entries.get(key(blockLocation));
        if (entry == null || !entry.anchor().isValid()) {
            return;
        }
        entry.tracker().animate("open");
    }

    private static String key(Location location) {
        return location.getWorld() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
