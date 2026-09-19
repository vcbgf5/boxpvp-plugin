package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LmsCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public LmsCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("join")) {
            if (!plugin.getLms().isSignupOpen()) {
                player.sendMessage("§cZapisy na Ostatniego Ocalałego nie są teraz otwarte.");
                return true;
            }
            boolean joined = plugin.getLms().join(player);
            player.sendMessage(joined ? "§aZapisano Cię do Ostatniego Ocalałego! Poczekaj na start." : "§cJuż jesteś zapisany/a.");
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            boolean left = plugin.getLms().leave(player);
            player.sendMessage(left ? "§aWypisano Cię z Ostatniego Ocalałego." : "§cNie jesteś zapisany/a (albo runda już trwa).");
            return true;
        }
        player.sendMessage("§cUżycie: /lms join|leave");
        return true;
    }
}
