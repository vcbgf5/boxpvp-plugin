package com.dziubek.boxpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boczna tablica wyników (sidebar) pokazująca saldo (Vault = ecoBalance), liczbę zabójstw,
 * aktualną serię zabójstw, poziom prestiżu i (jeśli LuckPerms jest zainstalowany) rangę gracza -
 * odświeżana co sekundę dla wszystkich online.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_ID = "bpvp_side";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final List<Double> WEALTH_MILESTONES = List.of(10_000.0, 100_000.0, 1_000_000.0, 10_000_000.0);

    private final BoxPvpPlugin plugin;
    private final Map<UUID, String> lastRank = new HashMap<>();
    private final Map<UUID, Double> lastWealthMilestone = new HashMap<>();

    public ScoreboardManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 20L);
    }

    /**
     * Tytuł idzie przez Component (nie zwykły 3-argumentowy registerNewObjective) - ten
     * ostatni ma twardy limit 32 znaków na surowy String, a gradientowe kodowanie hex
     * (§x§R§R§G§G§B§B na znak) rozdmuchuje długość dużo powyżej tego limitu.
     */
    public void assign(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        String configured = plugin.getConfig().getString("scoreboard.title", null);
        String legacyTitle = configured != null
                ? ChatColor.translateAlternateColorCodes('&', configured)
                : Branding.accent(Branding.NAME);
        Component title = LEGACY.deserialize(legacyTitle);

        Objective objective = board.registerNewObjective(OBJECTIVE_ID, Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(board);
        refresh(player);
    }

    private void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    private void refresh(Player player) {
        Scoreboard board = player.getScoreboard();
        Objective objective = board.getObjective(OBJECTIVE_ID);
        if (objective == null) {
            assign(player);
            return;
        }

        for (String entry : new ArrayList<>(board.getEntries())) {
            board.resetScores(entry);
        }

        double balance = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(player) : 0;
        int streak = plugin.getKillstreaks().getCurrent(player.getUniqueId());
        int prestige = plugin.getPrestige().getLevel(player.getUniqueId());
        int kills = plugin.getStats().getKills(player.getUniqueId());
        String rank = plugin.getLuckPerms().getPrefix(player);

        checkWealthMilestone(player, balance);
        checkRankChange(player, rank);

        int line = (rank != null && !rank.isEmpty()) ? 6 : 5;
        if (rank != null && !rank.isEmpty()) {
            objective.getScore(rank).setScore(line--);
        }
        objective.getScore("§aSaldo: §f" + String.format("%.2f", balance) + "$").setScore(line--);
        objective.getScore("§bKille: §f" + kills).setScore(line--);
        objective.getScore("§cSeria: §f" + streak).setScore(line--);
        objective.getScore("§dPrestiż: §f" + prestige).setScore(line--);
        objective.getScore("§7").setScore(line);
    }

    /** Jednorazowy, duży efekt gdy gracz pierwszy raz przekroczy kolejny próg salda. */
    private void checkWealthMilestone(Player player, double balance) {
        double highest = lastWealthMilestone.getOrDefault(player.getUniqueId(), 0.0);
        double reached = highest;
        for (double milestone : WEALTH_MILESTONES) {
            if (balance >= milestone && milestone > reached) {
                reached = milestone;
            }
        }
        if (reached <= highest) {
            return;
        }
        lastWealthMilestone.put(player.getUniqueId(), reached);
        if (highest <= 0.0) {
            return; // baseline przy pierwszym sprawdzeniu (np. świeży login) - bez fajerwerków za darmo
        }
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 50, 0.5, 0.7, 0.5, 0.3);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        TitleUtil.show(player, Branding.accent("✦ BOGACTWO!"), "§7Przekroczono §f" + formatMilestone(reached) + "§7 na koncie!");
        Bukkit.broadcastMessage(Branding.chatPrefix() + "§e" + player.getName() + " §7przekroczył(a) §f"
                + formatMilestone(reached) + "§7 na koncie!");
    }

    private static String formatMilestone(double value) {
        if (value >= 1_000_000) {
            return (long) (value / 1_000_000) + "mln$";
        }
        return (long) (value / 1_000) + "k$";
    }

    /** Fajerwerk + unoszący się tekst, gdy LuckPerms pokaże inny prefix niż poprzednio widziany. */
    private void checkRankChange(Player player, String rank) {
        if (rank == null || rank.isEmpty()) {
            return;
        }
        String previous = lastRank.put(player.getUniqueId(), rank);
        if (previous == null || previous.equals(rank)) {
            return;
        }
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.5, 0), 40, 0.4, 0.6, 0.4, 0.08);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
        FloatingTextEffect.show(plugin, player.getLocation().add(0, 2.3, 0), "§d✦ " + rank);
    }
}
