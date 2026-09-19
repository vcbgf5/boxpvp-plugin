package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Eventy serwerowe: czasowy mnożnik monet (auto-sprzedaż + nagrody za zabójstwa) i "skrzynka
 * z nieba" (envoy) spadająca na LOSOWE miejsce w wyznaczonym prostokątnym obszarze (dwa rogi,
 * jak przy zaznaczaniu generatora) - NIE na region WorldGuard, tylko własna strefa pluginu.
 */
public class EventManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final Random random = new Random();

    private double activeMultiplier = 1.0;
    private long multiplierExpiresAt = 0L;
    private Location envoyZoneCorner1;
    private Location envoyZoneCorner2;
    private long nextNormalEnvoyAt;
    private long nextMegaEnvoyAt;
    private long nextZombieAt;
    private long nextMegaZombieAt;

    public EventManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć events.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        loadEnvoyZone();
    }

    /**
     * Uruchamia automatyczne zrzuty - każdy typ ma własny, stały interwał (niezależny od
     * pozostałych): zwykła skrzynka co 5 min, MEGA skrzynka co 10 min, zombie-event co 1 min,
     * mega-zombie (wielka skrzynka + Giant) co 30 min.
     */
    public void start() {
        long normalEnvoyTicks = minutesToTicks("envoy.normal-interval-minutes", 5);
        long megaEnvoyTicks = minutesToTicks("envoy.mega-interval-minutes", 10);
        long zombieTicks = minutesToTicks("envoy.zombie-interval-minutes", 1);
        long megaZombieTicks = minutesToTicks("envoy.megazombie-interval-minutes", 30);

        nextNormalEnvoyAt = System.currentTimeMillis() + normalEnvoyTicks * 50L;
        nextMegaEnvoyAt = System.currentTimeMillis() + megaEnvoyTicks * 50L;
        nextZombieAt = System.currentTimeMillis() + zombieTicks * 50L;
        nextMegaZombieAt = System.currentTimeMillis() + megaZombieTicks * 50L;

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            nextNormalEnvoyAt = System.currentTimeMillis() + normalEnvoyTicks * 50L;
            spawnEnvoy(false);
        }, normalEnvoyTicks, normalEnvoyTicks);

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            nextMegaEnvoyAt = System.currentTimeMillis() + megaEnvoyTicks * 50L;
            spawnEnvoy(true);
        }, megaEnvoyTicks, megaEnvoyTicks);

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            nextZombieAt = System.currentTimeMillis() + zombieTicks * 50L;
            spawnZombieEvent();
        }, zombieTicks, zombieTicks);

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            nextMegaZombieAt = System.currentTimeMillis() + megaZombieTicks * 50L;
            spawnMegaZombieEvent();
        }, megaZombieTicks, megaZombieTicks);

        long coin2xTicks = minutesToTicks("envoy.coin2x-interval-minutes", 30);
        long coin3xTicks = minutesToTicks("envoy.coin3x-interval-minutes", 45);
        int coinEventMinutes = plugin.getConfig().getInt("envoy.coin-event-minutes", 10);
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> startCoinEvent(2.0, coinEventMinutes), coin2xTicks, coin2xTicks);
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> startCoinEvent(3.0, coinEventMinutes), coin3xTicks, coin3xTicks);
    }

    private long minutesToTicks(String configKey, long defaultMinutes) {
        return plugin.getConfig().getLong(configKey, defaultMinutes) * 60L * 20L;
    }

    public boolean spawnZombieEvent() {
        if (!hasEnvoyZone()) {
            return false;
        }
        plugin.getZombieEvent().spawnZombie(randomPointInZone());
        return true;
    }

    public boolean spawnMegaZombieEvent() {
        if (!hasEnvoyZone() || plugin.getGiantEvent().isEventActive()) {
            return false;
        }
        plugin.getGiantEvent().dropCrate(randomPointInZone());
        return true;
    }

    /** Ile milisekund zostało do kolejnych automatycznych zrzutów - do wyświetlenia na tablicy. */
    public long getMillisUntilNextNormalEnvoy() {
        return Math.max(0, nextNormalEnvoyAt - System.currentTimeMillis());
    }

    public long getMillisUntilNextMegaEnvoy() {
        return Math.max(0, nextMegaEnvoyAt - System.currentTimeMillis());
    }

    public long getMillisUntilNextZombie() {
        return Math.max(0, nextZombieAt - System.currentTimeMillis());
    }

    public long getMillisUntilNextMegaZombie() {
        return Math.max(0, nextMegaZombieAt - System.currentTimeMillis());
    }

    /**
     * Łączny mnożnik zarobków danego gracza: aktywny event (jeśli trwa) razy jego trwały
     * mnożnik z prestiżu razy jego osobisty, czasowy booster ze sklepu (jeśli ma aktywny).
     * Używane przez auto-sprzedaż i nagrody za zabójstwa/serie.
     */
    public double totalMultiplier(Player player) {
        return getActiveMultiplier() * plugin.getPrestige().getMultiplier(player.getUniqueId())
                * plugin.getBoosters().getMultiplier(player.getUniqueId());
    }

    public double getActiveMultiplier() {
        if (System.currentTimeMillis() >= multiplierExpiresAt) {
            return 1.0;
        }
        return activeMultiplier;
    }

    /** Ile ms zostało do końca aktywnego eventu monet - do wyświetlenia na tablicy. */
    public long getMultiplierMillisRemaining() {
        return Math.max(0, multiplierExpiresAt - System.currentTimeMillis());
    }

    public void startCoinEvent(double multiplier, int minutes) {
        this.activeMultiplier = multiplier;
        long expiresAt = System.currentTimeMillis() + minutes * 60_000L;
        this.multiplierExpiresAt = expiresAt;

        Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent("★ EVENT!") + " §fMonety x" + trim(multiplier) + " przez " + minutes + " minut!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }
        BossBarUtil.showTimed(plugin, "§6§l★ EVENT: §fMonety x" + trim(multiplier), BarColor.YELLOW, minutes * 60L * 20L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (multiplierExpiresAt == expiresAt && System.currentTimeMillis() >= multiplierExpiresAt) {
                Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent("★ EVENT zakończony.") + " §7Monety wracają do normy.");
            }
        }, minutes * 60L * 20L);
    }

    public void setEnvoyZoneCorner(int corner, Location location) {
        if (corner == 1) {
            this.envoyZoneCorner1 = location.clone();
        } else {
            this.envoyZoneCorner2 = location.clone();
        }
        String base = "envoy-zone.corner" + corner;
        data.set(base + ".world", location.getWorld().getName());
        data.set(base + ".x", location.getBlockX());
        data.set(base + ".y", location.getBlockY());
        data.set(base + ".z", location.getBlockZ());
        save();
    }

    public boolean hasEnvoyZone() {
        return envoyZoneCorner1 != null && envoyZoneCorner2 != null
                && envoyZoneCorner1.getWorld() != null
                && envoyZoneCorner1.getWorld().equals(envoyZoneCorner2.getWorld());
    }

    public boolean spawnEnvoy() {
        return spawnEnvoy(false);
    }

    public boolean spawnEnvoy(boolean mega) {
        if (!hasEnvoyZone()) {
            return false;
        }
        plugin.getEnvoy().scheduleDrop(randomPointInZone(), mega);
        return true;
    }

    /**
     * Losowy punkt X/Z w wyznaczonym obszarze, na wysokości "podłogi" strefy (niższy z dwóch
     * rogów) - żeby skrzynka lądowała w środku boxa, a nie na jego dachu (highest-block trafiłby
     * w sufit, jeśli box jest zadaszony).
     */
    private Location randomPointInZone() {
        World world = envoyZoneCorner1.getWorld();
        int minX = Math.min(envoyZoneCorner1.getBlockX(), envoyZoneCorner2.getBlockX());
        int maxX = Math.max(envoyZoneCorner1.getBlockX(), envoyZoneCorner2.getBlockX());
        int minY = Math.min(envoyZoneCorner1.getBlockY(), envoyZoneCorner2.getBlockY());
        int minZ = Math.min(envoyZoneCorner1.getBlockZ(), envoyZoneCorner2.getBlockZ());
        int maxZ = Math.max(envoyZoneCorner1.getBlockZ(), envoyZoneCorner2.getBlockZ());

        int x = minX + random.nextInt(maxX - minX + 1);
        int z = minZ + random.nextInt(maxZ - minZ + 1);
        return new Location(world, x + 0.5, minY, z + 0.5);
    }

    private void loadEnvoyZone() {
        envoyZoneCorner1 = loadCorner(1);
        envoyZoneCorner2 = loadCorner(2);
    }

    private Location loadCorner(int corner) {
        String base = "envoy-zone.corner" + corner;
        String worldName = data.getString(base + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, data.getInt(base + ".x"), data.getInt(base + ".y"), data.getInt(base + ".z"));
    }

    private static String trim(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać events.yml: " + e.getMessage());
        }
    }
}
