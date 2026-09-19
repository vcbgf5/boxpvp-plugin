package com.dziubek.boxpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;

/**
 * Boczna tablica wyników (sidebar) pokazująca saldo (Vault = ecoBalance), liczbę zabójstw,
 * aktualną serię zabójstw, poziom prestiżu i (jeśli LuckPerms jest zainstalowany) rangę gracza -
 * odświeżana co sekundę dla wszystkich online.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_ID = "bpvp_side";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final BoxPvpPlugin plugin;

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
}
