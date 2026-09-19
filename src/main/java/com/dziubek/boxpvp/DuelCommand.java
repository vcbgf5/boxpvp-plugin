package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DuelCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public DuelCommand(BoxPvpPlugin plugin) {
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
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "invite":
                return handleInvite(player, args);
            case "accept":
                return handleAccept(player);
            case "leave":
                return handleLeave(player);
            case "stop":
                return handleStop(player, args);
            default:
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Branding.accent("--- /duel ---"));
        player.sendMessage("§c/duel invite <gracz> <stawka> §7- wyzwij gracza na pojedynek o pieniądze");
        player.sendMessage("§c/duel accept §7- przyjmij ostatnie zaproszenie");
        player.sendMessage("§c/duel leave §7- poddaj się w trakcie pojedynku (przegrywasz stawkę) albo anuluj wysłane zaproszenie");
        if (player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§c/duel stop <gracz> §7- (admin) przerywa czyjś pojedynek, bez przepływu pieniędzy");
        }
    }

    private boolean handleInvite(Player player, String[] args) {
        if (!plugin.getDuels().isConfigured()) {
            player.sendMessage("§cPojedynki nie są jeszcze skonfigurowane (admin: /bpvp duel setworld <świat> i /bpvp duel setpos).");
            return true;
        }
        if (args.length < 3) {
            player.sendMessage("§cUżycie: /duel invite <gracz> <stawka>");
            return true;
        }
        if (plugin.getDuels().hasActiveDuel(player.getUniqueId())) {
            player.sendMessage("§cJesteś już w trakcie pojedynku.");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cGracz '" + args[1] + "' nie jest online.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cNie możesz wyzwać samego siebie.");
            return true;
        }
        if (plugin.getDuels().hasActiveDuel(target.getUniqueId())) {
            player.sendMessage("§cTen gracz jest już w trakcie innego pojedynku.");
            return true;
        }

        double bet;
        try {
            bet = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cStawka musi być liczbą.");
            return true;
        }
        if (bet < 0) {
            player.sendMessage("§cStawka nie może być ujemna.");
            return true;
        }
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cEkonomia (Vault) niedostępna.");
            return true;
        }
        if (plugin.getEconomy().getBalance(player) < bet) {
            player.sendMessage("§cNie masz wystarczająco monet na tę stawkę.");
            return true;
        }

        plugin.getDuels().invite(player, target, bet);
        target.sendMessage("§e" + player.getName() + " §7wyzywa Cię na pojedynek o §a" + BankGuiManager.formatMoney(bet) + "$§7!");
        target.sendMessage("§a/duel accept §7żeby przyjąć (masz 60s)");
        player.sendMessage("§aWysłano wyzwanie do " + target.getName() + ".");
        return true;
    }

    private boolean handleAccept(Player player) {
        DuelManager.Invite invite = plugin.getDuels().getInvite(player.getUniqueId());
        if (invite == null) {
            player.sendMessage("§cNie masz żadnego aktywnego zaproszenia na pojedynek.");
            return true;
        }
        Player inviter = Bukkit.getPlayer(invite.getInviter());
        if (inviter == null || !inviter.isOnline()) {
            player.sendMessage("§cTen gracz jest już offline.");
            plugin.getDuels().clearInvite(player.getUniqueId());
            return true;
        }
        if (plugin.getDuels().hasActiveDuel(player.getUniqueId()) || plugin.getDuels().hasActiveDuel(inviter.getUniqueId())) {
            player.sendMessage("§cJedno z was jest już w trakcie innego pojedynku.");
            plugin.getDuels().clearInvite(player.getUniqueId());
            return true;
        }
        if (plugin.getEconomy() == null || plugin.getEconomy().getBalance(player) < invite.getBet()
                || plugin.getEconomy().getBalance(inviter) < invite.getBet()) {
            player.sendMessage("§cJedno z was nie ma już wystarczająco monet na tę stawkę.");
            plugin.getDuels().clearInvite(player.getUniqueId());
            return true;
        }

        plugin.getDuels().clearInvite(player.getUniqueId());
        boolean started = plugin.getDuels().start(inviter, player, invite.getBet());
        if (!started) {
            player.sendMessage("§cNie udało się rozpocząć pojedynku (problem ze światem areny).");
            inviter.sendMessage("§cNie udało się rozpocząć pojedynku (problem ze światem areny).");
        }
        return true;
    }

    private boolean handleLeave(Player player) {
        if (plugin.getDuels().hasActiveDuel(player.getUniqueId())) {
            plugin.getDuels().forfeit(player);
            return true;
        }
        if (plugin.getDuels().cancelOutgoingInvite(player.getUniqueId())) {
            player.sendMessage("§aAnulowano wysłane zaproszenie.");
            return true;
        }
        player.sendMessage("§cNie jesteś w pojedynku ani nie masz wysłanego zaproszenia.");
        return true;
    }

    private boolean handleStop(Player player, String[] args) {
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /duel stop <gracz>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !plugin.getDuels().hasActiveDuel(target.getUniqueId())) {
            player.sendMessage("§cTen gracz nie jest w trakcie pojedynku.");
            return true;
        }
        plugin.getDuels().adminStop(target);
        player.sendMessage("§aPrzerwano pojedynek.");
        return true;
    }
}
