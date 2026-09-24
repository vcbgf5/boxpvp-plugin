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
 * EKSPERYMENT: "TEST 1" / "TEST 2" pozycjonowane tą samą techniką co Kasa HUD i to, jak faktycznie
 * działa BetterHud/HUDEngine - bossbar + własny font, BEZ encji w świecie. Każdy tytuł to JEDEN
 * obrazkowy glif (screentest_1.png / screentest_2.png) - wysoki, w większości przezroczysty
 * kanwas z samym napisem narysowanym blisko dołu obrazka. Ponieważ deklarowany "ascent" w
 * font/default.json wynosi 0, góra obrazka renderuje się przy normalnej (górnej) pozycji
 * bossbara, a napis "przewleka się" w dół ekranu o wysokość kanwasu ponad ten punkt.
 *
 * Wysokości kanwasów (200px dla "środka", 380px dla "dołu") to zgadywanka - serwer nie zna
 * realnego FOV/skali GUI klienta - do skorygowania po zobaczeniu na żywo (wymaga przebudowania
 * resourcepacka, w przeciwieństwie do poprzedniej wersji na encjach).
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
