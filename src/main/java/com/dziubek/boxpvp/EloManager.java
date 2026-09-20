package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranking ELO (pomysł #7) - aktualizowany po KAŻDYM zakończonym /duel (nie po zwykłych
 * zabójstwach w otwartym boxie - zbyt łatwo by było nabijać go farmieniem słabszych graczy).
 * Standardowy wzór szachowego ELO z K=32: im większa różnica ratingów, tym mniej zyskuje
 * faworyt za wygraną i tym więcej traci za przegraną z outsiderem.
 * Sezony (pomysł #13): co SEASON_LENGTH_MILLIS ratingi resetują się do DEFAULT_RATING, a TOP 3
 * dostaje jednorazową nagrodę w monetach - patrz endSeason().
 */
public class EloManager {

    private static final int DEFAULT_RATING = 1000;
    private static final double K_FACTOR = 32.0;
    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;
    private static final long SEASON_LENGTH_MILLIS = 14L * 24 * 60 * 60 * 1000;
    private static final long SEASON_CHECK_INTERVAL_TICKS = 20L * 60 * 5;
    private static final double[] SEASON_REWARDS = {500, 300, 150};

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private volatile boolean dirty = false;
    private long seasonEndsAt;

    public EloManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "elo.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć elo.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.seasonEndsAt = data.getLong("season-ends-at", 0);
        if (seasonEndsAt <= 0) {
            seasonEndsAt = System.currentTimeMillis() + SEASON_LENGTH_MILLIS;
            data.set("season-ends-at", seasonEndsAt);
            dirty = true;
        }
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkSeason, SEASON_CHECK_INTERVAL_TICKS, SEASON_CHECK_INTERVAL_TICKS);
    }

    public long getSeasonMillisRemaining() {
        return Math.max(0, seasonEndsAt - System.currentTimeMillis());
    }

    private void checkSeason() {
        if (System.currentTimeMillis() >= seasonEndsAt) {
            endSeason();
        }
    }

    /** Kończy sezon: nagradza TOP 3 monetami, resetuje WSZYSTKIE ratingi do DEFAULT_RATING, startuje nowy sezon. */
    public void endSeason() {
        ConfigurationSection players = data.getConfigurationSection("players");
        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>();
        if (players != null) {
            for (String uuidStr : players.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    int rating = data.getInt("players." + uuidStr + ".rating", DEFAULT_RATING);
                    ranked.add(Map.entry(uuid, rating));
                } catch (IllegalArgumentException ignored) {
                    // klucz spoza formatu UUID - pomiń
                }
            }
        }
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        Bukkit.broadcastMessage(Branding.chatPrefix() + "§d§l★ KONIEC SEZONU ELO! §7Ratingi zresetowane, zaczyna się nowy sezon.");
        for (int i = 0; i < ranked.size() && i < SEASON_REWARDS.length; i++) {
            UUID uuid = ranked.get(i).getKey();
            int rating = ranked.get(i).getValue();
            String name = data.getString("players." + uuid + ".name", uuid.toString());
            double reward = SEASON_REWARDS[i];
            Bukkit.broadcastMessage("§7#" + (i + 1) + " §f" + name + " §7- §b" + rating + " ELO §7(§a+" + (long) reward + "$§7)");
            if (plugin.getEconomy() != null) {
                plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(uuid), reward);
            }
        }

        if (players != null) {
            for (String uuidStr : players.getKeys(false)) {
                data.set("players." + uuidStr + ".rating", DEFAULT_RATING);
            }
        }
        seasonEndsAt = System.currentTimeMillis() + SEASON_LENGTH_MILLIS;
        data.set("season-ends-at", seasonEndsAt);
        dirty = true;
        flush();
    }

    public int getRating(UUID uuid) {
        return data.getInt("players." + uuid + ".rating", DEFAULT_RATING);
    }

    /**
     * Aktualizuje ratingi obu graczy po wygranym/przegranym pojedynku i zwraca zmianę
     * [zwycięzcy, przegranego] (dodatnia/ujemna) - do pokazania w wiadomości na czacie.
     */
    public int[] recordDuelResult(UUID winnerUuid, String winnerName, UUID loserUuid, String loserName) {
        int ratingWinner = getRating(winnerUuid);
        int ratingLoser = getRating(loserUuid);

        double expectedWinner = 1.0 / (1.0 + Math.pow(10, (ratingLoser - ratingWinner) / 400.0));
        int newWinner = (int) Math.round(ratingWinner + K_FACTOR * (1.0 - expectedWinner));
        int newLoser = Math.max(0, (int) Math.round(ratingLoser - K_FACTOR * (1.0 - expectedWinner)));

        data.set("players." + winnerUuid + ".rating", newWinner);
        data.set("players." + winnerUuid + ".name", winnerName);
        data.set("players." + loserUuid + ".rating", newLoser);
        data.set("players." + loserUuid + ".name", loserName);
        dirty = true;

        return new int[]{newWinner - ratingWinner, newLoser - ratingLoser};
    }

    public List<StatsManager.TopEntry> top(int limit) {
        List<StatsManager.TopEntry> list = new ArrayList<>();
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null) {
            return list;
        }
        for (String uuidStr : players.getKeys(false)) {
            int rating = data.getInt("players." + uuidStr + ".rating", DEFAULT_RATING);
            String name = data.getString("players." + uuidStr + ".name", uuidStr);
            list.add(new StatsManager.TopEntry(name, rating));
        }
        list.sort((a, b) -> Double.compare(b.value(), a.value()));
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    public void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać elo.yml: " + e.getMessage());
        }
    }
}
