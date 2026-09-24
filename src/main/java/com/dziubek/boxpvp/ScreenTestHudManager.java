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
 * EKSPERYMENT: "TEST 1" / "TEST 2" - CELOWO dwa OSOBNE bossbary (nie sklejone z Kasa HUD w jeden
 * tytuł) - Minecraft centruje tytuł każdego bossbara niezależnie i automatycznie, więc to jedyny
 * sposób, żeby każdy element faktycznie wylądował na środku, bez ręcznego liczenia szerokości.
 *
 * Każdy tytuł to JEDEN obrazkowy glif (screentest_1.png / screentest_2.png) - wysoki, w większości
 * przezroczysty kanwas z samym napisem narysowanym blisko dołu. Deklarowany "ascent" w
 * font/default.json wynosi 0, więc góra obrazka renderuje się przy normalnej (górnej) pozycji
 * bossbara, a napis "przewleka się" w dół ekranu o wysokość kanwasu ponad ten punkt.
 *
 * Wysokości kanwasów skalibrowane na podstawie realnego zrzutu ekranu (height=200 wylądowało na
 * ~365px w pionie) - test1 (295px, cel: środek ekranu), test2 (520px, cel: dół, z zapasem).
 */
public class ScreenTestHudManager {

    private final BoxPvpPlugin plugin;
    private final Map<UUID, BossBar> line1 = new HashMap<>();
    private final Map<UUID, BossBar> line2 = new HashMap<>();

    public ScreenTestHudManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            show(player);
        }
    }

    public void show(Player player) {
        hide(player);
        line1.put(player.getUniqueId(), create(player, Branding.SCREEN_TEST_1));
        line2.put(player.getUniqueId(), create(player, Branding.SCREEN_TEST_2));
    }

    public void hide(Player player) {
        BossBar a = line1.remove(player.getUniqueId());
        if (a != null) {
            a.removeAll();
        }
        BossBar b = line2.remove(player.getUniqueId());
        if (b != null) {
            b.removeAll();
        }
    }

    private BossBar create(Player player, String title) {
        BossBar bar = Bukkit.createBossBar(title, BarColor.PINK, BarStyle.SOLID);
        bar.setProgress(1.0);
        bar.addPlayer(player);
        return bar;
    }
}
