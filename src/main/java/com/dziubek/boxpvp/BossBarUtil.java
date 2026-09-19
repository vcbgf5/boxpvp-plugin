package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tymczasowy boss bar widoczny dla wszystkich graczy online - do krótkich, mocnych ogłoszeń
 * eventów (obok zwykłego broadcastu na czacie). Pasek maleje płynnie do zera przez cały czas
 * trwania, więc sam pokazuje ile jeszcze zostało.
 */
public final class BossBarUtil {

    private static final long UPDATE_INTERVAL_TICKS = 2L;

    private BossBarUtil() {
    }

    public static void showTimed(BoxPvpPlugin plugin, String title, BarColor color, long durationTicks) {
        BossBar bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(player);
        }
        bar.setProgress(1.0);

        long totalSteps = Math.max(1, durationTicks / UPDATE_INTERVAL_TICKS);

        new BukkitRunnable() {
            long stepsLeft = totalSteps;

            @Override
            public void run() {
                stepsLeft--;
                if (stepsLeft <= 0 || !bar.isVisible()) {
                    bar.removeAll();
                    cancel();
                    return;
                }
                bar.setProgress(Math.max(0.0, Math.min(1.0, (double) stepsLeft / totalSteps)));
            }
        }.runTaskTimer(plugin, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }
}
