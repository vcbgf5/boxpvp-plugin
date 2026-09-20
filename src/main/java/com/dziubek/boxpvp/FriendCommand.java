package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class FriendCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public FriendCommand(BoxPvpPlugin plugin) {
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
            case "add":
                return handleAdd(player, args);
            case "remove":
                return handleRemove(player, args);
            case "list":
                return handleList(player);
            default:
                sendHelp(player);
                return true;
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Branding.accent("--- /friend ---"));
        player.sendMessage("§c/friend add <gracz> §7- dodaje do listy znajomych");
        player.sendMessage("§c/friend remove <gracz> §7- usuwa z listy znajomych");
        player.sendMessage("§c/friend list §7- pokazuje listę znajomych i kto jest online");
    }

    private boolean handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /friend add <gracz>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cNie możesz dodać samego siebie.");
            return true;
        }
        boolean added = plugin.getFriends().addFriend(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(added ? "§aDodano " + args[1] + " do znajomych." : "§cJuż masz go na liście znajomych.");
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUżycie: /friend remove <gracz>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        boolean removed = plugin.getFriends().removeFriend(player.getUniqueId(), target.getUniqueId());
        player.sendMessage(removed ? "§eUsunięto " + args[1] + " ze znajomych." : "§cNie masz go na liście znajomych.");
        return true;
    }

    private boolean handleList(Player player) {
        Set<UUID> friends = plugin.getFriends().getFriends(player.getUniqueId());
        if (friends.isEmpty()) {
            player.sendMessage("§7Nie masz jeszcze żadnych znajomych. Dodaj: §f/friend add <gracz>");
            return true;
        }
        player.sendMessage(Branding.accent("--- Znajomi ---"));
        for (UUID uuid : friends) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String status = op.isOnline() ? "§a● online" : "§7● offline";
            player.sendMessage("§f" + op.getName() + " §7- " + status);
        }
        return true;
    }
}
