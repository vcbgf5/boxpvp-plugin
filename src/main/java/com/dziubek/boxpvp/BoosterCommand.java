package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BoosterCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public BoosterCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        double multiplier = plugin.getBoosters().getMultiplier(player.getUniqueId());
        if (multiplier <= 1.0) {
            player.sendMessage("§7Nie masz aktywnego boostera. Kup go w §f/sklep§7!");
            return true;
        }
        long secondsLeft = plugin.getBoosters().getMillisRemaining(player.getUniqueId()) / 1000;
        player.sendMessage(Branding.chatPrefix() + "§7Aktywny booster: §fx" + multiplier
                + " §7- zostało §f" + (secondsLeft / 60) + "min " + (secondsLeft % 60) + "s");
        return true;
    }
}
