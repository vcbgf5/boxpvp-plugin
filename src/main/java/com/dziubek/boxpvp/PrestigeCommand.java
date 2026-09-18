package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PrestigeCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public PrestigeCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("confirm")) {
            plugin.getPrestige().prestige(player);
            return true;
        }

        int level = plugin.getPrestige().getLevel(player.getUniqueId());
        double cost = plugin.getPrestige().getCost(level);
        double multiplier = plugin.getPrestige().getMultiplier(player.getUniqueId());

        player.sendMessage("§d§l--- Prestiż ---");
        player.sendMessage("§7Obecny poziom: §d" + level + " §7(mnożnik zarobków: §fx" + String.format("%.2f", multiplier) + "§7)");
        player.sendMessage("§7Koszt kolejnego: §a" + String.format("%.2f", cost) + "$ §7(cała gotówka zostanie zabrana)");
        player.sendMessage("§ePotwierdź: §f/prestige confirm");
        return true;
    }
}
