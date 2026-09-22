package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KitManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    private final Map<String, Map<UUID, Long>> lastUse = new HashMap<>();

    public KitManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "kits.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć kits.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /** Wczytuje kits.yml na nowo z dysku - do reczej edycji pliku bez restartu serwera (/bpvp reload). */
    public void reload() {
        try {
            data.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            plugin.getLogger().warning("Nie udało się przeładować kits.yml: " + e.getMessage());
        }
    }

    public boolean exists(String name) {
        return data.contains("kits." + name);
    }

    public List<String> names() {
        ConfigurationSection section = data.getConfigurationSection("kits");
        if (section == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    public void saveKit(String name, ItemStack[] contents) {
        data.set("kits." + name + ".items", null);
        int idx = 0;
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            data.set("kits." + name + ".items." + idx, item);
            idx++;
        }
        if (!data.contains("kits." + name + ".cooldown-seconds")) {
            data.set("kits." + name + ".cooldown-seconds", 3600);
        }
        save();
    }

    public List<ItemStack> getItems(String name) {
        List<ItemStack> list = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("kits." + name + ".items");
        if (section == null) {
            return list;
        }
        for (String key : section.getKeys(false)) {
            ItemStack item = data.getItemStack("kits." + name + ".items." + key);
            if (item != null) {
                list.add(item);
            }
        }
        return list;
    }

    public long getCooldownSeconds(String name) {
        return data.getLong("kits." + name + ".cooldown-seconds", 3600);
    }

    public boolean isOnCooldown(String kitName, UUID uuid) {
        Map<UUID, Long> map = lastUse.get(kitName);
        if (map == null) {
            return false;
        }
        Long last = map.get(uuid);
        if (last == null) {
            return false;
        }
        return (System.currentTimeMillis() - last) < getCooldownSeconds(kitName) * 1000L;
    }

    public long secondsLeft(String kitName, UUID uuid) {
        Map<UUID, Long> map = lastUse.get(kitName);
        if (map == null) {
            return 0;
        }
        Long last = map.get(uuid);
        if (last == null) {
            return 0;
        }
        long left = getCooldownSeconds(kitName) - (System.currentTimeMillis() - last) / 1000L;
        return Math.max(left, 0);
    }

    public void markUsed(String kitName, UUID uuid) {
        lastUse.computeIfAbsent(kitName, k -> new HashMap<>()).put(uuid, System.currentTimeMillis());
    }

    /**
     * Dostęp do kitu: gracz ma permission "kit.<nazwa>" (np. z LuckPerms) ALBO kupił go w /sklep.
     * Działa samodzielnie nawet bez pluginu do uprawnień.
     */
    public boolean hasAccess(String kitName, Player player) {
        if (player.hasPermission("kit." + kitName)) {
            return true;
        }
        return hasPurchased(kitName, player.getUniqueId());
    }

    public boolean hasPurchased(String kitName, UUID uuid) {
        List<String> list = data.getStringList("purchases." + uuid);
        return list.contains(kitName);
    }

    /**
     * Przenosi kity zapisane w starym (płaskim) formacie sprzed zmiany struktury
     * do nowego "kits.&lt;nazwa&gt;". Zwraca ile kitów zostało zmigrowanych.
     */
    public int migrateOldFormat() {
        int migrated = 0;
        for (String key : new ArrayList<>(data.getKeys(false))) {
            if (key.equals("kits") || key.equals("purchases")) {
                continue;
            }
            if (!data.contains(key + ".items")) {
                continue; // to nie jest stary kit, pomijamy
            }

            ConfigurationSection itemsSection = data.getConfigurationSection(key + ".items");
            if (itemsSection != null) {
                for (String itemKey : itemsSection.getKeys(false)) {
                    data.set("kits." + key + ".items." + itemKey, data.get(key + ".items." + itemKey));
                }
            }
            data.set("kits." + key + ".cooldown-seconds", data.getLong(key + ".cooldown-seconds", 3600));

            data.set(key, null);
            migrated++;
        }
        if (migrated > 0) {
            save();
        }
        return migrated;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać kits.yml: " + e.getMessage());
        }
    }
}
