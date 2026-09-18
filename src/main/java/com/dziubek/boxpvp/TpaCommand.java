package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TpaCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public TpaCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUżycie: /tpa <gracz>");
            return true;
        }

        Player player = (Player) sender;

        if (plugin.getCombatManager().isTagged(player.getUniqueId())) {
            long left = plugin.getCombatManager().secondsLeft(player.getUniqueId());
            player.sendMessage("§cNie możesz użyć /tpa podczas walki! (zostało " + left + "s)");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);

        if (target == null || !target.isOnline()) {
            player.sendMessage("§cGracz '" + args[0] + "' nie jest online.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cNie możesz teleportować się do samego siebie.");
            return true;
        }

        plugin.getTpa().createRequest(player.getUniqueId(), target.getUniqueId());

        long timeout = plugin.getTpa().timeoutSeconds();
        target.sendMessage("§e" + player.getName() + " chce się do Ciebie teleportować.");
        target.sendMessage("§a/tpaccept §7lub §c/tpdeny §7(masz " + timeout + "s)");
        player.sendMessage("§aWysłano prośbę o teleportację do " + target.getName() + ".");
        return true;
    }
}
