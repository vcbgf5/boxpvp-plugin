package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Wypycha aktualny resource pack ponownie do wszystkich online graczy, bez potrzeby rejoina -
 * przydatne od razu po podmianie jara na nową wersję paczki (admin nie musi czekać, aż gracze
 * sami się przełączą).
 */
public class ReloadResourcePackCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            ResourcePackPusher.push(player);
        }
        sender.sendMessage("§aWysłano resource pack ponownie do " + Bukkit.getOnlinePlayers().size() + " graczy.");
        return true;
    }
}
