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
 * Stałe "GUI" w prawym górnym rogu ekranu ("Kasa: X,XXK") zrobione z bossbara. Tekstury WSZYSTKICH
 * kolorów/stylów bossbara (pasek/tło) są w resource packu w pełni przezroczyste, więc żaden
 * bossbar w pluginie (ten, mega-zombie HP, ogłoszenia eventów) nie pokazuje już paska - widać
 * wyłącznie tytuł. Sam napis nie jest zwykłym kolorowym tekstem Minecrafta, tylko sekwencją
 * własnych obrazkowych glifów z resource packa (Branding.KASA_*) - etykieta "Kasa:", cyfry,
 * przecinek i sufiks K/M/B/T to osobne, narysowane tekstury.
 *
 * CELOWO prosty, naturalny mechanizm - tytuł to zwykła sekwencja glifów, bez żadnej ręcznej
 * matematyki przesunięć/spacji. Minecraft centruje taki tytuł SAM, automatycznie i niezawodnie -
 * próby doklejenia dodatkowych elementów (TEST1/TEST2) w tym samym tytule przez ręczne triki ze
 * spacjami psuły renderowanie (m.in. całą etykietę "Kasa:") i zostały porzucone.
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

    public void refreshAll() {
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
        String formatted = Branding.formatCompact(balance);

        StringBuilder title = new StringBuilder();
        title.append(Branding.KASA_LABEL);
        for (int i = 0; i < formatted.length(); i++) {
            title.append(Branding.kasaDigit(formatted.charAt(i)));
        }
        return title.toString();
    }
}
