package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * /party - drużyny bez trwałości (nie przeżywają restartu). Członkowie nie zadają sobie
 * obrażeń (patrz CombatDamageListener) - to jedyny "efekt uboczny" bycia w drużynie.
 */
public class PartyCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public PartyCommand(BoxPvpPlugin plugin) {
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
            case "create":
                return handleCreate(player);
            case "invite":
                return handleInvite(player, args);
            case "accept":
                return handleAccept(player);
            case "leave":
                return handleLeave(player);
            case "kick":
                return handleKick(player, args);
            case "list":
                return handleList(player);
            default:
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Branding.accent("--- /party ---"));
        player.sendMessage("§c/party create §7- zakłada drużynę");
        player.sendMessage("§c/party invite <gracz> §7- zaprasza gracza (tylko lider)");
        player.sendMessage("§c/party accept §7- akceptuje ostatnie zaproszenie");
        player.sendMessage("§c/party leave §7- opuszcza drużynę");
        player.sendMessage("§c/party kick <gracz> §7- wyrzuca gracza (tylko lider)");
        player.sendMessage("§c/party list §7- lista członków Twojej drużyny");
    }

    private boolean handleCreate(Player player) {
        if (plugin.getParty().inParty(player.getUniqueId())) {
            player.sendMessage("§cJesteś już w drużynie.");
            return true;
        }
        plugin.getParty().createParty(player.getUniqueId());
        player.sendMessage("§aZałożono drużynę! Zaproś kogoś: §f/party invite <gracz>");
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /party invite <gracz>");
            return true;
        }
        Party party = plugin.getParty().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage("§cMusisz najpierw stworzyć drużynę: §f/party create");
            return true;
        }
        if (!party.isLeader(player.getUniqueId())) {
            player.sendMessage("§cTylko lider drużyny może zapraszać.");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cGracz '" + args[1] + "' nie jest online.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cNie możesz zaprosić samego siebie.");
            return true;
        }
        if (plugin.getParty().inParty(target.getUniqueId())) {
            player.sendMessage("§c" + target.getName() + " jest już w jakiejś drużynie.");
            return true;
        }

        plugin.getParty().invite(player.getUniqueId(), target.getUniqueId());
        long timeout = plugin.getParty().inviteTimeoutSeconds();
        target.sendMessage("§e" + player.getName() + " zaprasza Cię do drużyny.");
        target.sendMessage("§a/party accept §7(masz " + timeout + "s)");
        player.sendMessage("§aWysłano zaproszenie do " + target.getName() + ".");
        return true;
    }

    private boolean handleAccept(Player player) {
        UUID inviterUuid = plugin.getParty().getInviter(player.getUniqueId());
        if (inviterUuid == null) {
            player.sendMessage("§cNie masz żadnego aktualnego zaproszenia do drużyny.");
            return true;
        }
        if (plugin.getParty().inParty(player.getUniqueId())) {
            player.sendMessage("§cJesteś już w drużynie.");
            return true;
        }
        Party party = plugin.getParty().getParty(inviterUuid);
        if (party == null) {
            player.sendMessage("§cTa drużyna już nie istnieje.");
            plugin.getParty().clearInvite(player.getUniqueId());
            return true;
        }

        plugin.getParty().addMember(party, player.getUniqueId());
        plugin.getParty().clearInvite(player.getUniqueId());

        player.sendMessage("§aDołączyłeś do drużyny!");
        for (UUID memberUuid : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && !member.getUniqueId().equals(player.getUniqueId())) {
                member.sendMessage("§a" + player.getName() + " dołączył do drużyny!");
            }
        }
        return true;
    }

    private boolean handleLeave(Player player) {
        Party party = plugin.getParty().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage("§cNie jesteś w żadnej drużynie.");
            return true;
        }
        UUID previousLeader = party.getLeader();
        Party remaining = plugin.getParty().removeMember(player.getUniqueId());
        player.sendMessage("§aOpuściłeś drużynę.");

        if (remaining != null) {
            boolean leaderChanged = !remaining.getLeader().equals(previousLeader);
            for (UUID memberUuid : remaining.getMembers()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member == null) {
                    continue;
                }
                member.sendMessage("§e" + player.getName() + " opuścił drużynę.");
                if (leaderChanged && remaining.isLeader(memberUuid)) {
                    member.sendMessage("§aJesteś teraz liderem drużyny!");
                }
            }
        }
        return true;
    }

    private boolean handleKick(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /party kick <gracz>");
            return true;
        }
        Party party = plugin.getParty().getParty(player.getUniqueId());
        if (party == null || !party.isLeader(player.getUniqueId())) {
            player.sendMessage("§cTylko lider drużyny może wyrzucać.");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cGracz '" + args[1] + "' nie jest online.");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cNie możesz wyrzucić samego siebie - użyj §f/party leave§c.");
            return true;
        }
        if (!party.getMembers().contains(target.getUniqueId())) {
            player.sendMessage("§c" + target.getName() + " nie jest w Twojej drużynie.");
            return true;
        }

        plugin.getParty().removeMember(target.getUniqueId());
        target.sendMessage("§cZostałeś wyrzucony z drużyny.");
        player.sendMessage("§aWyrzucono " + target.getName() + " z drużyny.");
        for (UUID memberUuid : party.getMembers()) {
            Player member = Bukkit.getPlayer(memberUuid);
            if (member != null && !member.getUniqueId().equals(player.getUniqueId())) {
                member.sendMessage("§e" + target.getName() + " został wyrzucony z drużyny.");
            }
        }
        return true;
    }

    private boolean handleList(Player player) {
        Party party = plugin.getParty().getParty(player.getUniqueId());
        if (party == null) {
            player.sendMessage("§cNie jesteś w żadnej drużynie.");
            return true;
        }
        player.sendMessage(Branding.accent("Twoja drużyna:"));
        for (UUID memberUuid : party.getMembers()) {
            String name = plugin.getStats().getName(memberUuid);
            String suffix = party.isLeader(memberUuid) ? " §d(lider)" : "";
            player.sendMessage("§7- §f" + name + suffix);
        }
        return true;
    }
}
