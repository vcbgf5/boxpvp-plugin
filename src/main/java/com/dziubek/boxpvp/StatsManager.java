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

    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private volatile boolean dirty = false;

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

    /**
     * Tak jak MissionManager - zapis na dysk nie idzie już przy każdym increment() (kille,
     * bloki wykopane itd.), tylko raz na 30s i tylko jeśli coś się realnie zmieniło.
     */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
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
        markDirty();
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

    public void recordKill(UUID uuid, String name) {
        increment(uuid, name, "kills");
    }

    public void recordDeath(UUID uuid, String name) {
        increment(uuid, name, "deaths");
    }

    public int getKills(UUID uuid) {
        return data.getInt("players." + uuid + ".kills", 0);
    }

    public int getDeaths(UUID uuid) {
        return data.getInt("players." + uuid + ".deaths", 0);
    }

    public void recordBlockMined(UUID uuid, String name) {
        increment(uuid, name, "blocks-mined");
    }

    public int getBlocksMined(UUID uuid) {
        return data.getInt("players." + uuid + ".blocks-mined", 0);
    }

    public int getBestKillstreak(UUID uuid) {
        return data.getInt("players." + uuid + ".best-killstreak", 0);
    }

    public void setBestKillstreakIfHigher(UUID uuid, String name, int value) {
        if (value > getBestKillstreak(uuid)) {
            data.set("players." + uuid + ".best-killstreak", value);
            touchName(uuid, name);
            markDirty();
        }
    }

    /**
     * Rejestruje gracza (imię + istnienie wpisu) bez zmiany żadnej statystyki - wywoływane
     * przy każdym dołączeniu, żeby leaderboardy (np. TOP monety) wiedziały kogo w ogóle sprawdzać.
     */
    public void touch(UUID uuid, String name) {
        touchName(uuid, name);
        markDirty();
    }

    public String getName(UUID uuid) {
        return data.getString("players." + uuid + ".name", uuid.toString());
    }

    public List<UUID> knownPlayers() {
        List<UUID> list = new ArrayList<>();
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return list;
        }
        for (String uuidStr : players.getKeys(false)) {
            try {
                list.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
                // klucz spoza formatu UUID - pomiń
            }
        }
        return list;
    }

    /**
     * Top N graczy wg wybranej statystyki ("crates", "daily", "money", "kills" albo "killstreak"), malejąco.
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
                case "kills":
                    value = data.getInt(base + ".kills", 0);
                    break;
                case "killstreak":
                    value = data.getInt(base + ".best-killstreak", 0);
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
        markDirty();
    }

    private void touchName(UUID uuid, String name) {
        if (name != null) {
            data.set("players." + uuid + ".name", name);
        }
    }

    private void markDirty() {
        dirty = true;
    }

    public void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać playerstats.yml: " + e.getMessage());
        }
    }
}
