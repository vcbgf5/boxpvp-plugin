package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Król wzgórza" (pomysł #8) - w wyznaczonej prostokątnej strefie liczy się suma sekund, jaką
 * dany gracz spędzi w niej w trakcie trwania eventu (nie trzeba być tam samemu - to zwykła
 * suma czasu, a nie wyłączność). Kto ma najwięcej sekund, gdy czas eventu się skończy, wygrywa
 * nagrodę. Startowany ręcznie (/bpvp event hill <minuty>).
 */
public class HillEventManager {

    private static final long TICK_INTERVAL_TICKS = 20L;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    private Location corner1;
    private Location corner2;
    private boolean active;
    private long endsAt;
    private long totalDurationMs;
    private final Map<UUID, Long> secondsInZone = new HashMap<>();
    private BossBar bossBar;

    public HillEventManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "hillevent.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć hillevent.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public boolean isConfigured() {
        return corner1 != null && corner2 != null;
    }

    public boolean isActive() {
        return active;
    }

    public void setCorner(int corner, Location location) {
        if (corner == 1) {
            this.corner1 = location.clone();
        } else {
            this.corner2 = location.clone();
        }
        String base = "hill-zone.corner" + corner;
        data.set(base + ".world", location.getWorld().getName());
        data.set(base + ".x", location.getBlockX());
        data.set(base + ".y", location.getBlockY());
        data.set(base + ".z", location.getBlockZ());
        save();
    }

    public boolean start(int durationMinutes) {
        if (!isConfigured() || active || corner1.getWorld() == null) {
            return false;
        }
        active = true;
        secondsInZone.clear();
        totalDurationMs = durationMinutes * 60_000L;
        endsAt = System.currentTimeMillis() + totalDurationMs;

        bossBar = Bukkit.createBossBar(Branding.accent(" Król Wzgórza"), BarColor.YELLOW, BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent(" KRÓL WZGÓRZA!")
                + " §7Wejdź do strefy i przetrwaj w niej najdłużej - masz " + durationMinutes + " min!");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        }

        tick();
        return true;
    }

    private void tick() {
        if (!active) {
            return;
        }
        long msLeft = endsAt - System.currentTimeMillis();
        if (msLeft <= 0) {
            finish();
            return;
        }

        World world = corner1.getWorld();
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (isInside(player.getLocation())) {
                secondsInZone.merge(player.getUniqueId(), 1L, Long::sum);
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 0.1, 0), 2, 0.3, 0.05, 0.3, 0);
            }
        }

        UUID leader = leader();
        String leaderName = leader != null ? nameOf(leader) : "-";
        long leaderSeconds = leader != null ? secondsInZone.get(leader) : 0;
        bossBar.setTitle(Branding.accent(" Król Wzgórza") + " §7- §f" + leaderName + " §7(" + leaderSeconds
                + "s) §7- zostało " + (msLeft / 1000) + "s");
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, msLeft / (double) totalDurationMs)));

        plugin.getServer().getScheduler().runTaskLater(plugin, this::tick, TICK_INTERVAL_TICKS);
    }

    private void finish() {
        active = false;
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        UUID winner = leader();
        if (winner == null) {
            Bukkit.broadcastMessage(Branding.chatPrefix() + "§7Król Wzgórza zakończony - nikt nie wszedł do strefy.");
            return;
        }

        double reward = plugin.getConfig().getDouble("hill.reward", 500);
        Player winnerPlayer = Bukkit.getPlayer(winner);
        if (plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(winner), reward);
        }
        Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent(" Król Wzgórza!") + " §f" + nameOf(winner)
                + " §7wygrał(a) i dostaje §a" + BankGuiManager.formatMoney(reward) + "$§7!");

        if (winnerPlayer != null && winnerPlayer.isOnline()) {
            TitleUtil.show(winnerPlayer, Branding.accent(" KRÓL WZGÓRZA!"), "§7+" + BankGuiManager.formatMoney(reward) + "$");
            winnerPlayer.playSound(winnerPlayer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            winnerPlayer.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, winnerPlayer.getLocation().add(0, 1.2, 0), 60, 0.5, 0.8, 0.5, 0.3);
        }
    }

    private UUID leader() {
        UUID best = null;
        long bestValue = -1;
        for (Map.Entry<UUID, Long> entry : secondsInZone.entrySet()) {
            if (entry.getValue() > bestValue) {
                bestValue = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    private boolean isInside(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().equals(corner1.getWorld())) {
            return false;
        }
        double minX = Math.min(corner1.getX(), corner2.getX());
        double maxX = Math.max(corner1.getX(), corner2.getX());
        double minY = Math.min(corner1.getY(), corner2.getY());
        double maxY = Math.max(corner1.getY(), corner2.getY());
        double minZ = Math.min(corner1.getZ(), corner2.getZ());
        double maxZ = Math.max(corner1.getZ(), corner2.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX
                && loc.getY() >= minY && loc.getY() <= maxY
                && loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    private void load() {
        corner1 = loadCorner(1);
        corner2 = loadCorner(2);
    }

    private Location loadCorner(int corner) {
        String base = "hill-zone.corner" + corner;
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

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać hillevent.yml: " + e.getMessage());
        }
    }
}
