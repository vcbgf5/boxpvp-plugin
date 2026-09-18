package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;

/**
 * Eventy serwerowe: czasowy mnożnik monet (auto-sprzedaż + nagrody za zabójstwa) i "skrzynka
 * z nieba" (envoy) spadająca na wcześniej ustawiony punkt.
 */
public class EventManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    private double activeMultiplier = 1.0;
    private long multiplierExpiresAt = 0L;
    private Location envoyPoint;

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
        loadEnvoyPoint();
    }

    /**
     * Łączny mnożnik zarobków danego gracza: aktywny event (jeśli trwa) razy jego trwały
     * mnożnik z prestiżu. Używane przez auto-sprzedaż i nagrody za zabójstwa/serie.
     */
    public double totalMultiplier(Player player) {
        return getActiveMultiplier() * plugin.getPrestige().getMultiplier(player.getUniqueId());
    }

    public double getActiveMultiplier() {
        if (System.currentTimeMillis() >= multiplierExpiresAt) {
            return 1.0;
        }
        return activeMultiplier;
    }

    public void startCoinEvent(double multiplier, int minutes) {
        this.activeMultiplier = multiplier;
        long expiresAt = System.currentTimeMillis() + minutes * 60_000L;
        this.multiplierExpiresAt = expiresAt;

        Bukkit.broadcastMessage("§6§l★ EVENT! §fMonety x" + trim(multiplier) + " przez " + minutes + " minut!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (multiplierExpiresAt == expiresAt && System.currentTimeMillis() >= multiplierExpiresAt) {
                Bukkit.broadcastMessage("§6§l★ EVENT zakończony. §7Monety wracają do normy.");
            }
        }, minutes * 60L * 20L);
    }

    public void setEnvoyPoint(Location location) {
        this.envoyPoint = location.clone();
        data.set("envoy.world", location.getWorld().getName());
        data.set("envoy.x", location.getX());
        data.set("envoy.y", location.getY());
        data.set("envoy.z", location.getZ());
        save();
    }

    public boolean spawnEnvoy() {
        if (envoyPoint == null || envoyPoint.getWorld() == null) {
            return false;
        }
        plugin.getEnvoy().spawnFallingCrate(envoyPoint.clone());
        return true;
    }

    private void loadEnvoyPoint() {
        String worldName = data.getString("envoy.world");
        if (worldName == null) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        envoyPoint = new Location(world, data.getDouble("envoy.x"), data.getDouble("envoy.y"), data.getDouble("envoy.z"));
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
