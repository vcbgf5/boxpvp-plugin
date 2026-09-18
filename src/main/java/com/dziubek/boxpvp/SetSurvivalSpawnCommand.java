package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSurvivalSpawnCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public SetSurvivalSpawnCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }

        Player player = (Player) sender;
        plugin.setSurvivalSpawn(player.getLocation());
        player.sendMessage("§aSpawn survivala ustawiony w tym miejscu!");
        return true;
    }
}
