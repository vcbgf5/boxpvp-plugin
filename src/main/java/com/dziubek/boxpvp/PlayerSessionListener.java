package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Rejestruje gracza w statystykach (do leaderboardów) i przypina scoreboard - przy KAŻDYM
 * dołączeniu, nie tylko pierwszym (o to dba osobno FirstJoinSpawnListener).
 */
public class PlayerSessionListener implements Listener {

    private final BoxPvpPlugin plugin;

    public PlayerSessionListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getStats().touch(player.getUniqueId(), player.getName());
        plugin.getScoreboards().assign(player);
        plugin.getBalanceHud().show(player);
        plugin.getMarket().deliverPendingReturns(player);
        ResourcePackPusher.push(player);

        player.sendMessage("§7Wskazówka: §fmodele 3D broni §7działają u KAŻDEGO gracza automatycznie (zwykły resource pack). "
                + "§fWłasny wygląd zbroi §7wymaga zainstalowanego po Twojej stronie §eOptiFine §7(albo CIT Resewn) - bez tego zbroja wygląda normalnie.");
    }
}
