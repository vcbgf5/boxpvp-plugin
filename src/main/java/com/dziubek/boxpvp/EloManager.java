package com.dziubek.boxpvp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Ranking ELO (pomysł #7) - aktualizowany po KAŻDYM zakończonym /duel (nie po zwykłych
 * zabójstwach w otwartym boxie - zbyt łatwo by było nabijać go farmieniem słabszych graczy).
 * Standardowy wzór szachowego ELO z K=32: im większa różnica ratingów, tym mniej zyskuje
 * faworyt za wygraną i tym więcej traci za przegraną z outsiderem.
 */
public class EloManager {

    private static final int DEFAULT_RATING = 1000;
    private static final double K_FACTOR = 32.0;
    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private volatile boolean dirty = false;

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
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
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
