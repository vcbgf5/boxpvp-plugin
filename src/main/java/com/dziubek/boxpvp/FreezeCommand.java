package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class FreezeCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public FreezeCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUżycie: /freeze <gracz>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cGracz '" + args[0] + "' nie jest online.");
            return true;
        }
        boolean nowFrozen = plugin.getModeration().toggleFreeze(target);
        sender.sendMessage(nowFrozen ? "§aZamrożono " + target.getName() + "." : "§aOdmrożono " + target.getName() + ".");
        return true;
    }
}
