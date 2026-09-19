package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Czas spędzony na serwerze (sumaryczne sekundy) + jednorazowe nagrody pieniężne za
 * przekroczenie progów - liczone co minutę tylko dla graczy aktualnie online. Zapis na dysk
 * jest "leniwy" (dirty+flush raz na 30s), tak samo jak StatsManager/MissionManager.
 */
public class PlaytimeManager {

    private static final long TICK_INTERVAL_TICKS = 20L * 60;
    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;
    private static final List<Milestone> MILESTONES = List.of(
            new Milestone(30 * 60L, 200),
            new Milestone(60 * 60L, 500),
            new Milestone(3 * 60 * 60L, 1500),
            new Milestone(6 * 60 * 60L, 3500),
            new Milestone(24 * 60 * 60L, 10000)
    );

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private volatile boolean dirty = false;

    public PlaytimeManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "playtime.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć playtime.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickOnlinePlayers, TICK_INTERVAL_TICKS, TICK_INTERVAL_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    private void tickOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            addSeconds(player, TICK_INTERVAL_TICKS / 20L);
        }
    }

    public long getSeconds(UUID uuid) {
        return data.getLong("players." + uuid, 0);
    }

    /** Najbliższy nieodebrany próg - null, jeśli gracz odebrał już wszystkie. */
    public Milestone nextMilestone(UUID uuid) {
        long current = getSeconds(uuid);
        for (Milestone milestone : MILESTONES) {
            if (current < milestone.seconds) {
                return milestone;
            }
        }
        return null;
    }

    private void addSeconds(Player player, long seconds) {
        UUID uuid = player.getUniqueId();
        long before = getSeconds(uuid);
        long after = before + seconds;
        data.set("players." + uuid, after);
        dirty = true;

        for (Milestone milestone : MILESTONES) {
            if (before < milestone.seconds && after >= milestone.seconds) {
                grantMilestone(player, milestone);
            }
        }
    }

    private void grantMilestone(Player player, Milestone milestone) {
        if (plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(player, milestone.reward);
        }
        player.sendMessage(Branding.chatPrefix() + "§a§lNagroda za czas gry! §7" + formatDuration(milestone.seconds)
                + " na serwerze - §a+" + BankGuiManager.formatMoney(milestone.reward) + "$");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 40, 0.4, 0.6, 0.4, 0.3);
    }

    public static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "min";
        }
        return minutes + "min";
    }

    public void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać playtime.yml: " + e.getMessage());
        }
    }

    public static final class Milestone {
        public final long seconds;
        public final double reward;

        Milestone(long seconds, double reward) {
            this.seconds = seconds;
            this.reward = reward;
        }
    }
}
