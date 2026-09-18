package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;

/**
 * Boczna tablica wyników (sidebar) pokazująca saldo (Vault = ecoBalance), aktualną serię
 * zabójstw i poziom prestiżu - odświeżana co sekundę dla wszystkich online.
 */
public class ScoreboardManager {

    private static final String OBJECTIVE_ID = "bpvp_side";

    private final BoxPvpPlugin plugin;

    public ScoreboardManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 20L);
    }

    public void assign(Player player) {
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        String title = ChatColor.translateAlternateColorCodes('&', plugin.getConfig().getString("scoreboard.title", "&6&lBoxPvP"));
        Objective objective = board.registerNewObjective(OBJECTIVE_ID, "dummy", title);
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

        int line = 4;
        objective.getScore("§aSaldo: §f" + String.format("%.2f", balance) + "$").setScore(line--);
        objective.getScore("§cSeria: §f" + streak).setScore(line--);
        objective.getScore("§dPrestiż: §f" + prestige).setScore(line--);
        objective.getScore("§7").setScore(line);
    }
}
