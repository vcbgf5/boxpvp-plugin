package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PlaytimeCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public PlaytimeCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        long seconds = plugin.getPlaytime().getSeconds(player.getUniqueId());
        player.sendMessage(Branding.chatPrefix() + "§7Czas gry: §f" + PlaytimeManager.formatDuration(seconds));

        PlaytimeManager.Milestone next = plugin.getPlaytime().nextMilestone(player.getUniqueId());
        if (next != null) {
            long left = Math.max(0, next.seconds - seconds);
            player.sendMessage("§7Następna nagroda (§a+" + BankGuiManager.formatMoney(next.reward)
                    + "$§7) za §f" + PlaytimeManager.formatDuration(left) + "§7.");
        } else {
            player.sendMessage("§7Odebrałeś już wszystkie nagrody za czas gry!");
        }
        return true;
    }
}
