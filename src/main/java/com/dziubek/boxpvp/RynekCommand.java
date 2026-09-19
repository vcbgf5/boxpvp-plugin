package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /rynek - otwiera GUI Rynku (pierwsza strona). */
public class RynekCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public RynekCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        plugin.getMarketGui().open((Player) sender, 0);
        return true;
    }
}
