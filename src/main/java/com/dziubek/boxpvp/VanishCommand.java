package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public VanishCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        boolean nowVanished = plugin.getModeration().toggleVanish(player);
        player.sendMessage(nowVanished ? "§7Jesteś teraz §8niewidzialny §7dla graczy." : "§aJesteś znowu widoczny.");
        return true;
    }
}
