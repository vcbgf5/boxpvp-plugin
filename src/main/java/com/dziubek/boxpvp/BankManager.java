package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
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
 * Kantor - wioskowy NPC do wymiany monet (Vault) na fizyczne "banknoty" (słonecznik, żelazo,
 * złoto, diament, netheryt - patrz CurrencyManager) i z powrotem. W przeciwieństwie do
 * TraderManager NIE używa wanilijnego handlu (MerchantRecipe) - cała wymiana idzie przez
 * własne GUI (BankGuiManager/BankListener), więc wygląd jest stały (Profession.NONE, brak
 * recept), a PPM zawsze otwiera GUI zamiast czegokolwiek wanilijnego.
 */
public class BankManager {

    private static final String TAG = "bpvp_bank";

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey nameTag;

    private final Map<String, BankData> banks = new HashMap<>();

    public BankManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "bank.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć bank.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.nameTag = new NamespacedKey(plugin, "bpvp_bank_name");
    }

    /**
     * Wczytuje wszystkich skonfigurowanych kantorowych NPC - jeśli w świecie już stoi ich
     * encja (przeżyła restart, bo jest trwała), przejmuje ją zamiast tworzyć duplikat.
     */
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
            BankData bd = new BankData(name, villager);
            banks.put(name, bd);
            applyAppearance(bd);
        }
    }

    public boolean exists(String name) {
        return banks.containsKey(name) || data.contains(name);
    }

    public List<String> names() {
        return new ArrayList<>(banks.keySet());
    }

    public void createBank(String name, Location location) {
        setLocation(name, location);
        save();

        Villager villager = spawnVillager(name, location);
        BankData bd = new BankData(name, villager);
        banks.put(name, bd);
        applyAppearance(bd);
    }

    public boolean removeBank(String name) {
        BankData bd = banks.remove(name);
        if (bd == null) {
            return false;
        }
        if (bd.villager.isValid()) {
            bd.villager.remove();
        }
        data.set(name, null);
        save();
        return true;
    }

    public BankData getByEntity(Entity entity) {
        String name = entity.getPersistentDataContainer().get(nameTag, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        return banks.get(name);
    }

    private Villager spawnVillager(String name, Location loc) {
        World world = loc.getWorld();
        loc.getChunk().load();
        return world.spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setPersistent(true);
            v.setProfession(Villager.Profession.NONE);
            v.getPersistentDataContainer().set(nameTag, PersistentDataType.STRING, name);
            v.addScoreboardTag(TAG);
        });
    }

    private Villager findExisting(String name, Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        loc.getChunk().load();
        for (Entity entity : world.getNearbyEntities(loc, 3, 3, 3)) {
            if (entity instanceof Villager && name.equals(entity.getPersistentDataContainer().get(nameTag, PersistentDataType.STRING))) {
                return (Villager) entity;
            }
        }
        return null;
    }

    private void applyAppearance(BankData bd) {
        if (!bd.villager.isValid()) {
            return;
        }
        bd.villager.setCustomName(Branding.accent("Kantor"));
        bd.villager.setCustomNameVisible(true);
        bd.villager.setProfession(Villager.Profession.NONE);
    }

    private void setLocation(String name, Location location) {
        data.set(name + ".world", location.getWorld().getName());
        data.set(name + ".x", location.getX());
        data.set(name + ".y", location.getY());
        data.set(name + ".z", location.getZ());
        data.set(name + ".yaw", location.getYaw());
        data.set(name + ".pitch", location.getPitch());
    }

    private Location readLocation(String name) {
        String worldName = data.getString(name + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = data.getDouble(name + ".x");
        double y = data.getDouble(name + ".y");
        double z = data.getDouble(name + ".z");
        float yaw = (float) data.getDouble(name + ".yaw");
        float pitch = (float) data.getDouble(name + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać bank.yml: " + e.getMessage());
        }
    }

    public static final class BankData {
        final String name;
        final Villager villager;

        BankData(String name, Villager villager) {
            this.name = name;
            this.villager = villager;
        }
    }
}
