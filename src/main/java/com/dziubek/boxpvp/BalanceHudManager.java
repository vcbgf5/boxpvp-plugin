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
 *
 * Które wiersze są włączone i ich drobne poziome dostrojenie (nudgeX) NIE są zaszyte w kodzie -
 * pochodzą z HudConfigLoader (config pobierany na żywo z GitHuba, odświeżany komendą /reloadhud) -
 * pozwala to eksperymentować/diagnozować bez przebudowywania jara przy każdej zmianie.
 */
public class BalanceHudManager {

    private static final long UPDATE_INTERVAL_TICKS = 20L;

    private final BoxPvpPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final HudConfigLoader config = new HudConfigLoader();

    public BalanceHudManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public HudConfigLoader getConfig() {
        return config;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            config.reload();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    show(player);
                }
            });
        });
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
        if (config.debugMode) {
            // DIAGNOSTYKA: minimalny test - czy POJEDYNCZY znak spacji o UJEMNEJ wartości (-16px)
            // w ogóle działa. "5" [spacja -16px] "7" - jeśli "7" wygląda jak kwadracik zamiast
            // cyfry, ujemne spacje nie są rozpoznawane przez klienta.
            return Branding.kasaDigit('5') + Branding.spaceOffset(-16) + Branding.kasaDigit('7');
        }

        double balance = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0.0;
        String formatted = Branding.formatCompact(balance);

        StringBuilder kasaContent = new StringBuilder();
        kasaContent.append(Branding.KASA_LABEL);
        int kasaWidth = Branding.KASA_LABEL_WIDTH;
        for (int i = 0; i < formatted.length(); i++) {
            char c = formatted.charAt(i);
            kasaContent.append(Branding.kasaDigit(c));
            kasaWidth += Branding.kasaDigitWidth(c);
        }

        StringBuilder title = new StringBuilder();
        if (config.kasaEnabled) {
            title.append(Branding.centeredRow(kasaContent.toString(), kasaWidth, config.kasaNudgeX));
        }
        if (config.test1Enabled) {
            title.append(Branding.centeredRow(Branding.SCREEN_TEST_1, Branding.SCREEN_TEST_1_WIDTH, config.test1NudgeX));
        }
        if (config.test2Enabled) {
            title.append(Branding.centeredRow(Branding.SCREEN_TEST_2, Branding.SCREEN_TEST_2_WIDTH, config.test2NudgeX));
        }
        return title.toString();
    }
}
