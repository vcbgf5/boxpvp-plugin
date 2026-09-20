package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class ClanCommand implements CommandExecutor {

    private static final int MAX_TAG_LENGTH = 6;

    private final BoxPvpPlugin plugin;

    public ClanCommand(BoxPvpPlugin plugin) {
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
                return handleCreate(player, args);
            case "invite":
                return handleInvite(player, args);
            case "accept":
                return handleAccept(player);
            case "leave":
                return handleLeave(player);
            case "kick":
                return handleKick(player, args);
            case "disband":
                return handleDisband(player);
            case "info":
                return handleInfo(player, args);
            case "list":
                return handleList(player);
            case "bank":
                return handleBank(player, args);
            default:
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Branding.accent("--- /clan ---"));
        player.sendMessage("§c/clan create <tag> §7- zakłada nowy klan (max " + MAX_TAG_LENGTH + " znaków)");
        player.sendMessage("§c/clan invite <gracz> §7- zaprasza do klanu (tylko lider)");
        player.sendMessage("§c/clan accept §7- przyjmuje ostatnie zaproszenie");
        player.sendMessage("§c/clan leave §7- opuszcza klan");
        player.sendMessage("§c/clan kick <gracz> §7- wyrzuca z klanu (tylko lider)");
        player.sendMessage("§c/clan disband §7- rozwiązuje klan (tylko lider)");
        player.sendMessage("§c/clan info [tag] §7- info o klanie (Twoim albo podanym)");
        player.sendMessage("§c/clan list §7- lista wszystkich klanów");
        player.sendMessage("§c/clan bank deposit|withdraw <kwota> §7- wspólny bank klanu");
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /clan create <tag>");
            return true;
        }
        if (plugin.getClans().inClan(player.getUniqueId())) {
            player.sendMessage("§cJesteś już w klanie.");
            return true;
        }
        String tag = args[1];
        if (tag.length() > MAX_TAG_LENGTH) {
            player.sendMessage("§cTag może mieć maksymalnie " + MAX_TAG_LENGTH + " znaków.");
            return true;
        }
        if (plugin.getClans().clanExists(tag)) {
            player.sendMessage("§cTen tag jest już zajęty.");
            return true;
        }
        boolean created = plugin.getClans().create(player, tag);
        if (created) {
            player.sendMessage(Branding.chatPrefix() + "§aZałożono klan §f[" + tag + "]§a!");
        } else {
            player.sendMessage("§cNie udało się założyć klanu.");
        }
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        String tag = plugin.getClans().getClanTag(player.getUniqueId());
        if (tag == null) {
            player.sendMessage("§cNie jesteś w klanie.");
            return true;
        }
        if (!plugin.getClans().isLeader(player.getUniqueId())) {
            player.sendMessage("§cTylko lider klanu może zapraszać.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /clan invite <gracz>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cGracz '" + args[1] + "' nie jest online.");
            return true;
        }
        if (plugin.getClans().inClan(target.getUniqueId())) {
            player.sendMessage("§cTen gracz jest już w jakimś klanie.");
            return true;
        }
        plugin.getClans().invite(player.getUniqueId(), target.getUniqueId());
        target.sendMessage(Branding.chatPrefix() + "§e" + player.getName() + " §7zaprasza Cię do klanu §f["
                + plugin.getClans().getDisplayTag(tag) + "]§7! §a/clan accept §7by dołączyć.");
        player.sendMessage("§aWysłano zaproszenie do " + target.getName() + ".");
        return true;
    }

    private boolean handleAccept(Player player) {
        boolean accepted = plugin.getClans().acceptInvite(player);
        if (accepted) {
            String tag = plugin.getClans().getClanTag(player.getUniqueId());
            player.sendMessage(Branding.chatPrefix() + "§aDołączyłeś do klanu §f[" + plugin.getClans().getDisplayTag(tag) + "]§a!");
        } else {
            player.sendMessage("§cNie masz aktywnego zaproszenia do klanu (albo już jesteś w jakimś).");
        }
        return true;
    }

    private boolean handleLeave(Player player) {
        boolean left = plugin.getClans().leaveClan(player.getUniqueId());
        player.sendMessage(left ? "§eOpuściłeś klan." : "§cNie jesteś w klanie.");
        return true;
    }

    private boolean handleKick(Player player, String[] args) {
        String tag = plugin.getClans().getClanTag(player.getUniqueId());
        if (tag == null) {
            player.sendMessage("§cNie jesteś w klanie.");
            return true;
        }
        if (!plugin.getClans().isLeader(player.getUniqueId())) {
            player.sendMessage("§cTylko lider klanu może wyrzucać.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /clan kick <gracz>");
            return true;
        }
        UUID target = Bukkit.getOfflinePlayer(args[1]).getUniqueId();
        if (target.equals(player.getUniqueId())) {
            player.sendMessage("§cNie możesz wyrzucić samego siebie - użyj /clan disband.");
            return true;
        }
        boolean kicked = plugin.getClans().kick(tag, target);
        player.sendMessage(kicked ? "§aWyrzucono " + args[1] + " z klanu." : "§cTen gracz nie jest w Twoim klanie.");
        return true;
    }

    private boolean handleDisband(Player player) {
        String tag = plugin.getClans().getClanTag(player.getUniqueId());
        if (tag == null) {
            player.sendMessage("§cNie jesteś w klanie.");
            return true;
        }
        if (!plugin.getClans().isLeader(player.getUniqueId())) {
            player.sendMessage("§cTylko lider może rozwiązać klan.");
            return true;
        }
        plugin.getClans().disband(tag);
        player.sendMessage("§eRozwiązano klan.");
        return true;
    }

    private boolean handleInfo(Player player, String[] args) {
        String tag = args.length >= 2 ? args[1] : plugin.getClans().getClanTag(player.getUniqueId());
        if (tag == null) {
            player.sendMessage("§cUżycie: /clan info <tag> (nie jesteś w żadnym klanie)");
            return true;
        }
        if (!plugin.getClans().clanExists(tag)) {
            player.sendMessage("§cTaki klan nie istnieje.");
            return true;
        }
        UUID leaderUuid = plugin.getClans().getLeader(tag);
        String leaderName = Bukkit.getOfflinePlayer(leaderUuid).getName();
        player.sendMessage(Branding.accent("--- Klan [" + plugin.getClans().getDisplayTag(tag) + "] ---"));
        player.sendMessage("§7Lider: §f" + leaderName);
        player.sendMessage("§7Bank: §a" + BankGuiManager.formatMoney(plugin.getClans().getBankBalance(tag)) + "$");
        StringBuilder members = new StringBuilder();
        for (UUID uuid : plugin.getClans().getMembers(tag)) {
            if (members.length() > 0) {
                members.append("§7, §f");
            }
            members.append(Bukkit.getOfflinePlayer(uuid).getName());
        }
        player.sendMessage("§7Członkowie (" + plugin.getClans().getMembers(tag).size() + "): §f" + members);
        return true;
    }

    private boolean handleList(Player player) {
        List<String> tags = plugin.getClans().listClanTags();
        if (tags.isEmpty()) {
            player.sendMessage("§7Nie ma jeszcze żadnych klanów.");
            return true;
        }
        player.sendMessage(Branding.accent("--- Klany ---"));
        for (String tag : tags) {
            player.sendMessage("§f[" + plugin.getClans().getDisplayTag(tag) + "] §7- " + plugin.getClans().getMembers(tag).size() + " członków");
        }
        return true;
    }

    private boolean handleBank(Player player, String[] args) {
        String tag = plugin.getClans().getClanTag(player.getUniqueId());
        if (tag == null) {
            player.sendMessage("§cNie jesteś w klanie.");
            return true;
        }
        if (args.length < 3 || !(args[1].equalsIgnoreCase("deposit") || args[1].equalsIgnoreCase("withdraw"))) {
            player.sendMessage("§cUżycie: /clan bank deposit|withdraw <kwota>");
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cKwota musi być liczbą.");
            return true;
        }
        if (amount <= 0) {
            player.sendMessage("§cKwota musi być większa od 0.");
            return true;
        }
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cEkonomia (Vault) niedostępna.");
            return true;
        }
        if (args[1].equalsIgnoreCase("deposit")) {
            if (plugin.getEconomy().getBalance(player) < amount) {
                player.sendMessage("§cNie masz tyle monet.");
                return true;
            }
            plugin.getEconomy().withdrawPlayer(player, amount);
            plugin.getClans().deposit(tag, amount);
            player.sendMessage("§aWpłacono " + BankGuiManager.formatMoney(amount) + "$ do banku klanu.");
        } else {
            if (!plugin.getClans().isLeader(player.getUniqueId())) {
                player.sendMessage("§cTylko lider może wypłacać z banku klanu.");
                return true;
            }
            boolean withdrawn = plugin.getClans().withdraw(tag, amount);
            if (!withdrawn) {
                player.sendMessage("§cW banku klanu nie ma tyle monet.");
                return true;
            }
            plugin.getEconomy().depositPlayer(player, amount);
            player.sendMessage("§aWypłacono " + BankGuiManager.formatMoney(amount) + "$ z banku klanu.");
        }
        return true;
    }
}
