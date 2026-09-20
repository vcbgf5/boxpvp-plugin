package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class EloCommand implements CommandExecutor {

    public static final int TOP_LIMIT = 10;
    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public EloCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("top")) {
            sendTop(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("season")) {
            sendSeason(sender);
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("endseason")) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage("§cNie masz uprawnień.");
                return true;
            }
            plugin.getElo().endSeason();
            sender.sendMessage("§aZakończono sezon ELO ręcznie.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz (albo /elo top|season).");
            return true;
        }
        Player player = (Player) sender;
        int rating = plugin.getElo().getRating(player.getUniqueId());
        player.sendMessage(Branding.chatPrefix() + "§7Twój ranking ELO (z pojedynków): §f" + rating);
        return true;
    }

    private void sendSeason(CommandSender sender) {
        long millis = plugin.getElo().getSeasonMillisRemaining();
        long days = millis / (24L * 60 * 60 * 1000);
        long hours = (millis / (60L * 60 * 1000)) % 24;
        sender.sendMessage(Branding.chatPrefix() + "§7Sezon ELO kończy się za: §f" + days + "d " + hours + "h");
        sender.sendMessage("§7Nagrody za TOP 3 na koniec sezonu: §a500$ / 300$ / 150$");
    }

    private void sendTop(CommandSender sender) {
        List<StatsManager.TopEntry> top = plugin.getElo().top(TOP_LIMIT);
        sender.sendMessage(Branding.accent("--- TOP " + TOP_LIMIT + " ELO ---"));
        if (top.isEmpty()) {
            sender.sendMessage("§7(brak danych - nikt jeszcze nie rozegrał pojedynku)");
            return;
        }
        for (int i = 0; i < top.size(); i++) {
            StatsManager.TopEntry entry = top.get(i);
            sender.sendMessage("§7#" + (i + 1) + " §f" + entry.name() + " §7- §b" + (long) entry.value());
        }
    }
}
