package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TradeCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public TradeCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§cUżycie: /trade <gracz> §7lub §c/trade accept §7lub §c/trade cancel");
            return true;
        }
        if (args[0].equalsIgnoreCase("accept")) {
            return handleAccept(player);
        }
        if (args[0].equalsIgnoreCase("cancel")) {
            return handleCancel(player);
        }
        return handleInvite(player, args[0]);
    }

    private boolean handleInvite(Player player, String targetName) {
        if (plugin.getTrades().hasActiveTrade(player.getUniqueId())) {
            player.sendMessage("§cJesteś już w trakcie wymiany.");
            return true;
        }
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cGracz '" + targetName + "' nie jest online.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cNie możesz wymieniać się sam ze sobą.");
            return true;
        }
        if (plugin.getTrades().hasActiveTrade(target.getUniqueId())) {
            player.sendMessage("§cTen gracz jest już w trakcie innej wymiany.");
            return true;
        }

        plugin.getTrades().invite(player, target);
        target.sendMessage("§e" + player.getName() + " §7chce się z Tobą wymienić przedmiotami.");
        target.sendMessage("§a/trade accept §7żeby przyjąć (masz 60s)");
        player.sendMessage("§aWysłano prośbę o wymianę do " + target.getName() + ".");
        return true;
    }

    private boolean handleAccept(Player player) {
        TradeManager.Invite invite = plugin.getTrades().getInvite(player.getUniqueId());
        if (invite == null) {
            player.sendMessage("§cNie masz żadnej aktywnej prośby o wymianę.");
            return true;
        }
        Player inviter = Bukkit.getPlayer(invite.getInviter());
        if (inviter == null || !inviter.isOnline()) {
            player.sendMessage("§cTen gracz jest już offline.");
            plugin.getTrades().clearInvite(player.getUniqueId());
            return true;
        }
        if (plugin.getTrades().hasActiveTrade(player.getUniqueId()) || plugin.getTrades().hasActiveTrade(inviter.getUniqueId())) {
            player.sendMessage("§cJedno z was jest już w trakcie innej wymiany.");
            plugin.getTrades().clearInvite(player.getUniqueId());
            return true;
        }

        plugin.getTrades().clearInvite(player.getUniqueId());
        plugin.getTrades().start(inviter, player);
        return true;
    }

    private boolean handleCancel(Player player) {
        TradeManager.ActiveTrade trade = plugin.getTrades().getActiveTrade(player.getUniqueId());
        if (trade != null) {
            plugin.getTrades().cancel(trade);
            return true;
        }
        if (plugin.getTrades().cancelOutgoingInvite(player.getUniqueId())) {
            player.sendMessage("§aAnulowano wysłaną prośbę o wymianę.");
            return true;
        }
        player.sendMessage("§cNie jesteś w trakcie wymiany ani nie masz wysłanej prośby.");
        return true;
    }
}
