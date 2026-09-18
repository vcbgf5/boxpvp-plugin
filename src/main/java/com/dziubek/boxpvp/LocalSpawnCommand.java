package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LocalSpawnCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public LocalSpawnCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }

        Player player = (Player) sender;

        if (plugin.getCombatManager().isTagged(player.getUniqueId())) {
            long left = plugin.getCombatManager().secondsLeft(player.getUniqueId());
            player.sendMessage("§cNie możesz użyć /spawn podczas walki! (zostało " + left + "s)");
            return true;
        }

        if (!plugin.hasSurvivalSpawn()) {
            player.sendMessage("§cSpawn survivala nie jest jeszcze ustawiony. Poproś admina o /setsurvivalspawn.");
            return true;
        }

        plugin.getTeleportDelay().start(player, plugin.getSurvivalSpawn(), "Spawn");
        return true;
    }
}
