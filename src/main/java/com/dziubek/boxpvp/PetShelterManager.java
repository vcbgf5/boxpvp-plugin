package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Schronisko Petów" - NPC (prawdziwy Villager, bez AI, jak handlarze) gdzie gracz zużywa
 * Klucz do Peta (PetKeyItem) i losuje nowego peta (PetShelterListener). Admin stawia go
 * przez /pet npc create &lt;nazwa&gt;, tak jak handlarzy w TraderManager.
 */
public class PetShelterManager {

    private static final String TAG = "bpvp_pet_shelter";

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey nameTag;
    private final Map<String, Villager> shelters = new HashMap<>();
    private volatile boolean spawningShelter = false;

    public PetShelterManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "pet_shelters.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć pet_shelters.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.nameTag = new NamespacedKey(plugin, "bpvp_pet_shelter_name");
    }

    public void initialize() {
        for (String name : data.getKeys(false)) {
            Location loc = readLocation(name);
            if (loc == null) {
                continue;
            }
            Villager villager = findExisting(name, loc);
            if (villager == null) {
                villager = spawnVillager(name, loc);
            }
            shelters.put(name, villager);
        }
    }

    public boolean exists(String name) {
        return shelters.containsKey(name) || data.contains(name);
    }

    public List<String> names() {
        return new ArrayList<>(shelters.keySet());
    }

    public void create(String name, Location location) {
        data.set(name + ".world", location.getWorld().getName());
        data.set(name + ".x", location.getX());
        data.set(name + ".y", location.getY());
        data.set(name + ".z", location.getZ());
        save();

        Villager villager = spawnVillager(name, location);
        shelters.put(name, villager);
    }

    public boolean remove(String name) {
        Villager villager = shelters.remove(name);
        if (villager == null) {
            return false;
        }
        if (villager.isValid()) {
            villager.remove();
        }
        data.set(name, null);
        save();
        return true;
    }

    public boolean isShelter(Entity entity) {
        return entity instanceof Villager
                && entity.getPersistentDataContainer().has(nameTag, PersistentDataType.STRING);
    }

    public boolean isSpawningShelter() {
        return spawningShelter;
    }

    private Villager spawnVillager(String name, Location loc) {
        World world = loc.getWorld();
        loc.getChunk().load();
        spawningShelter = true;
        Villager villager;
        try {
            villager = world.spawn(loc, Villager.class, v -> {
                v.setAI(false);
                v.setGravity(false);
                v.setInvulnerable(true);
                v.setSilent(false);
                v.setPersistent(true);
                v.setProfession(Villager.Profession.CARTOGRAPHER);
                v.setCustomName("§d§lSchronisko Petów");
                v.setCustomNameVisible(true);
                v.getPersistentDataContainer().set(nameTag, PersistentDataType.STRING, name);
                v.addScoreboardTag(TAG);
            });
        } finally {
            spawningShelter = false;
        }
        return villager;
    }

    private Villager findExisting(String name, Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        loc.getChunk().load();
        for (Entity entity : world.getNearbyEntities(loc, 3, 3, 3)) {
            if (entity instanceof Villager
                    && name.equals(entity.getPersistentDataContainer().get(nameTag, PersistentDataType.STRING))) {
                return (Villager) entity;
            }
        }
        return null;
    }

    private Location readLocation(String name) {
        String worldName = data.getString(name + ".world");
        if (worldName == null) {
            return null;
        }
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, data.getDouble(name + ".x"), data.getDouble(name + ".y"), data.getDouble(name + ".z"));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać pet_shelters.yml: " + e.getMessage());
        }
    }
}
