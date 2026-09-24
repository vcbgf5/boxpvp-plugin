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
 * Stałe "GUI" w prawym górnym rogu ekranu ("Kasa: X$") zrobione z bossbara. Tekstury WSZYSTKICH
 * kolorów/stylów bossbara (pasek/tło) są w resource packu w pełni przezroczyste, więc żaden
 * bossbar w pluginie (ten, mega-zombie HP, ogłoszenia eventów) nie pokazuje już paska - widać
 * wyłącznie tytuł. Sam napis nie jest zwykłym kolorowym tekstem Minecrafta, tylko sekwencją
 * własnych obrazkowych glifów z resource packa (Branding.KASA_*) - etykieta "Kasa:" i każda cyfra
 * salda to osobna, narysowana tekstura. Tekst zostaje na domyślnej, wyśrodkowanej pozycji bossbara
 * (próba przesunięcia go w stronę krawędzi przez sztuczny "space" offset wypychała go poza ekran -
 * usunięte) - jedyna korekta to lekkie przesunięcie w dół (patrz ascent glifów kasa_* w
 * font/default.json). Bez resource packa gracz zobaczy zwykły, wyśrodkowany, niewidoczny pasek z
 * "chińskimi znaczkami" zamiast glifów - nie psuje się, tylko wygląda gorzej.
 *
 * JEDEN bossbar dla całego własnego HUD-u (saldo + eksperymentalne TEST 1/TEST 2) - jak w prawdziwym
 * BetterHud (kr.toxicity.hud.component.LayoutComponentContainer): każdy "wiersz" jest owinięty
 * przez Branding.centeredRow() w spację -szerokość/2 przed i po, co zeruje jego wkład do sumy
 * zaawansowań całego tytułu (Minecraft centruje tytuł na bazie tej sumy) - dzięki temu każdy
 * wiersz wyśrodkowuje się NIEZALEŻNIE od szerokości pozostałych, mimo że wszystkie są w jednym
 * tytule jednego bossbara.
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
        String formatted = String.format(java.util.Locale.ROOT, "%.2f", balance);

        StringBuilder kasaContent = new StringBuilder();
        kasaContent.append(Branding.KASA_COIN);
        kasaContent.append(Branding.KASA_LABEL);
        int kasaWidth = Branding.KASA_COIN_WIDTH + Branding.KASA_LABEL_WIDTH;
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            kasaContent.append(Branding.kasaDigit(c));
            kasaWidth += Branding.kasaDigitWidth(c);
        }

        // DIAGNOSTYKA: TEST1/TEST2 chwilowo wyłączone - izolujemy, czy samo centeredRow() na
        // JEDNYM wierszu (bez łączenia z innymi) działa poprawnie, zanim wracamy do pełnej wersji.
        StringBuilder title = new StringBuilder();
        title.append(Branding.centeredRow(kasaContent.toString(), kasaWidth));
        return title.toString();
    }
}
