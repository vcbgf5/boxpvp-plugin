package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stałe "GUI" w prawym górnym rogu ekranu ("Kasa: X$") zrobione z bossbara, którego tekstura
 * (pasek/tło - tylko dla BarColor.PINK, żeby nie zepsuć innych bossbarów w pluginie: mega-zombie
 * HP, ogłoszenia eventów) jest w resource packu w pełni przezroczysta - widać wyłącznie tytuł
 * (tekst). Branding.SPACE_POS200 przesuwa ten domyślnie wyśrodkowany tekst w stronę prawej
 * krawędzi ekranu. Bez resource packa gracz zobaczy zwykły, wyśrodkowany różowy pasek z tekstem -
 * nie psuje się, tylko wygląda gorzej.
 */
public class BalanceHudManager {

    private static final long UPDATE_INTERVAL_TICKS = 20L;

    private final BoxPvpPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public BalanceHudManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            show(player);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    public void show(Player player) {
        BossBar bar = Bukkit.createBossBar(titleFor(player), BarColor.PINK, BarStyle.SOLID);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        bars.put(player.getUniqueId(), bar);
    }

    public void hide(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            BossBar bar = bars.get(player.getUniqueId());
            if (bar == null) {
                show(player);
                continue;
            }
            bar.setTitle(titleFor(player));
        }
    }

    private String titleFor(Player player) {
        double balance = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0.0;
        return Branding.SPACE_POS200 + "§7Kasa: §a" + String.format("%.2f", balance) + "$";
    }
}
