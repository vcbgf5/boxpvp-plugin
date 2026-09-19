package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tablice TOP 10 (zabójstwa, monety, najlepsza seria zabójstw). Jeśli DecentHolograms jest
 * zainstalowany, renderuje przez niego (tagowane hologramy "bpvp_top_&lt;typ&gt;") - jeśli nie,
 * stawia własny, natywny TextDisplay (hologram Minecrafta bez żadnej zależności) w tym samym
 * miejscu. Tylko jeden tryb naraz na tablicę.
 */
public class LeaderboardManager {

    private static final int MAX_ENTRIES = 10;
    private static final long REFRESH_TICKS = 20L * 15;
    private static final long TIMER_REFRESH_TICKS = 20L;
    private static final String BOARD_TAG = "bpvp_leaderboard";
    private static final String TIMER_TYPE = "envoy";
    private static final List<String> STAT_TYPES = List.of("kills", "coins", "killstreak", "elo");
    private static final List<String> ALL_TYPES = List.of("kills", "coins", "killstreak", "elo", "envoy");

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey typeTag;
    private final Map<String, Location> locations = new HashMap<>();
    private final Map<String, TextDisplay> boards = new HashMap<>();

    public LeaderboardManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "leaderboards.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć leaderboards.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.typeTag = new NamespacedKey(plugin, "bpvp_leaderboard_type");
        loadLocations();
    }

    public void start() {
        purgeOrphans();
        refreshAllStats();
        refreshTimer();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAllStats, REFRESH_TICKS, REFRESH_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshTimer, TIMER_REFRESH_TICKS, TIMER_REFRESH_TICKS);
    }

    public void setLocation(String type, Location location) {
        locations.put(type, location.clone());
        data.set(type + ".world", location.getWorld().getName());
        data.set(type + ".x", location.getX());
        data.set(type + ".y", location.getY());
        data.set(type + ".z", location.getZ());
        save();
        refresh(type);
    }

    private void refreshAllStats() {
        for (String type : STAT_TYPES) {
            refresh(type);
        }
    }

    private void refreshTimer() {
        refresh(TIMER_TYPE);
    }

    private void refresh(String type) {
        Location location = locations.get(type);
        if (location == null || location.getWorld() == null) {
            return;
        }
        List<String> lines = type.equals(TIMER_TYPE) ? buildTimerLines() : buildStatLines(type);
        String hologramId = type.equals(TIMER_TYPE) ? "bpvp_envoy_timer" : "bpvp_top_" + type;

        if (plugin.getDecentHolograms().isAvailable()) {
            plugin.getDecentHolograms().createInfoHologram(hologramId, location, lines);
            removeTextBoard(type);
        } else {
            updateTextBoard(type, location, lines);
        }
    }

    private List<String> buildStatLines(String type) {
        List<StatsManager.TopEntry> top = topFor(type);
        List<String> lines = new ArrayList<>();
        lines.add(titleFor(type));
        if (top.isEmpty()) {
            lines.add("§7(brak danych)");
        } else {
            for (int i = 0; i < top.size(); i++) {
                StatsManager.TopEntry entry = top.get(i);
                lines.add(rankColor(i) + "#" + (i + 1) + " §f" + entry.name() + " §7- " + formatValue(type, entry.value()));
            }
        }
        return lines;
    }

    private List<String> buildTimerLines() {
        List<String> lines = new ArrayList<>();
        lines.add(Branding.accent("Zbliżające się eventy"));
        double multiplier = plugin.getEvents().getActiveMultiplier();
        if (multiplier > 1.0) {
            lines.add("§6§l★ TRWA: §fMonety x" + (long) multiplier
                    + " §7(" + formatCountdown(plugin.getEvents().getMultiplierMillisRemaining()) + ")");
        }
        lines.add("§fSkrzynka: " + pulse(plugin.getEvents().getMillisUntilNextNormalEnvoy()));
        lines.add("§5MEGA skrzynka: " + pulse(plugin.getEvents().getMillisUntilNextMegaEnvoy()));
        lines.add("§aZombie: " + pulse(plugin.getEvents().getMillisUntilNextZombie()));
        lines.add("§4MEGA-zombie: " + pulse(plugin.getEvents().getMillisUntilNextMegaZombie()));
        return lines;
    }

    /** Ostatnie 10s do koloru odliczania - miga między czerwonym a białym, żeby rzucało się w oczy. */
    private static String pulse(long millis) {
        String color = millis <= 10_000 ? ((System.currentTimeMillis() / 500) % 2 == 0 ? "§c§l" : "§f§l") : "§d";
        return color + formatCountdown(millis);
    }

    private static String formatCountdown(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private List<StatsManager.TopEntry> topFor(String type) {
        if (type.equals("coins")) {
            return topCoins();
        }
        if (type.equals("elo")) {
            return plugin.getElo().top(MAX_ENTRIES);
        }
        return plugin.getStats().topN(type, MAX_ENTRIES);
    }

    private List<StatsManager.TopEntry> topCoins() {
        List<StatsManager.TopEntry> list = new ArrayList<>();
        if (plugin.getEconomy() == null) {
            return list;
        }
        for (UUID uuid : plugin.getStats().knownPlayers()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            double balance = plugin.getEconomy().getBalance(offline);
            if (balance <= 0) {
                continue;
            }
            list.add(new StatsManager.TopEntry(plugin.getStats().getName(uuid), balance));
        }
        list.sort((a, b) -> Double.compare(b.value(), a.value()));
        return list.size() > MAX_ENTRIES ? list.subList(0, MAX_ENTRIES) : list;
    }

    private void updateTextBoard(String type, Location location, List<String> lines) {
        TextDisplay board = boards.get(type);
        if (board == null || !board.isValid()) {
            board = spawnBoard(type, location);
        } else if (!board.getLocation().equals(location)) {
            board.teleport(location);
        }
        board.setText(String.join("\n", lines));
    }

    private TextDisplay spawnBoard(String type, Location location) {
        World world = location.getWorld();
        TextDisplay display = world.spawn(location, TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setSeeThrough(false);
            e.setShadowed(true);
            e.getPersistentDataContainer().set(typeTag, PersistentDataType.STRING, type);
            e.addScoreboardTag(BOARD_TAG);
        });
        boards.put(type, display);
        return display;
    }

    private void removeTextBoard(String type) {
        TextDisplay board = boards.remove(type);
        if (board != null && board.isValid()) {
            board.remove();
        }
    }

    private void purgeOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains(BOARD_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private static String titleFor(String type) {
        switch (type) {
            case "coins":
                return Branding.accent("TOP 10 - Monety");
            case "killstreak":
                return Branding.accent("TOP 10 - Seria zabójstw");
            case "elo":
                return Branding.accent("TOP 10 - Ranking ELO");
            default:
                return Branding.accent("TOP 10 - Zabójstwa");
        }
    }

    private static String rankColor(int index) {
        switch (index) {
            case 0:
                return "§e";
            case 1:
                return "§7";
            case 2:
                return "§6";
            default:
                return "§f";
        }
    }

    private static String formatValue(String type, double value) {
        if (type.equals("coins")) {
            return "§a" + String.format("%.2f", value) + "$";
        }
        return "§b" + (long) value;
    }

    private void loadLocations() {
        for (String type : ALL_TYPES) {
            String worldName = data.getString(type + ".world");
            if (worldName == null) {
                continue;
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }
            locations.put(type, new Location(world, data.getDouble(type + ".x"), data.getDouble(type + ".y"), data.getDouble(type + ".z")));
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać leaderboards.yml: " + e.getMessage());
        }
    }
}
