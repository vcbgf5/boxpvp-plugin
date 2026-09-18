package com.dziubek.boxpvp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class StatsManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    public StatsManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "playerstats.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć playerstats.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void recordCrateOpened(UUID uuid, String name) {
        increment(uuid, name, "crates-opened");
    }

    public void recordDailyClaim(UUID uuid, String name) {
        increment(uuid, name, "daily-claims");
    }

    public void recordMoneySpent(UUID uuid, String name, double amount) {
        String path = "players." + uuid + ".money-spent";
        data.set(path, data.getDouble(path, 0) + amount);
        touchName(uuid, name);
        save();
    }

    public int getCratesOpened(UUID uuid) {
        return data.getInt("players." + uuid + ".crates-opened", 0);
    }

    public int getDailyClaims(UUID uuid) {
        return data.getInt("players." + uuid + ".daily-claims", 0);
    }

    public double getMoneySpent(UUID uuid) {
        return data.getDouble("players." + uuid + ".money-spent", 0);
    }

    /**
     * Top N graczy wg wybranej statystyki ("crates", "daily" albo "money"), malejąco.
     */
    public List<TopEntry> topN(String stat, int limit) {
        List<TopEntry> list = new ArrayList<>();
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return list;
        }

        for (String uuidStr : players.getKeys(false)) {
            String base = "players." + uuidStr;
            double value;
            switch (stat) {
                case "daily":
                    value = data.getInt(base + ".daily-claims", 0);
                    break;
                case "money":
                    value = data.getDouble(base + ".money-spent", 0);
                    break;
                default:
                    value = data.getInt(base + ".crates-opened", 0);
                    break;
            }
            if (value <= 0) {
                continue;
            }
            String name = data.getString(base + ".name", uuidStr);
            list.add(new TopEntry(name, value));
        }

        list.sort((a, b) -> Double.compare(b.value(), a.value()));
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    public record TopEntry(String name, double value) {
    }

    private void increment(UUID uuid, String name, String key) {
        String path = "players." + uuid + "." + key;
        data.set(path, data.getInt(path, 0) + 1);
        touchName(uuid, name);
        save();
    }

    private void touchName(UUID uuid, String name) {
        if (name != null) {
            data.set("players." + uuid + ".name", name);
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać playerstats.yml: " + e.getMessage());
        }
    }
}
