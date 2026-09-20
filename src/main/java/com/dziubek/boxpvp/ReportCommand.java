package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

/** Ogólne zgłoszenie gracza adminom - w odróżnieniu od /reportduel (tylko trwający pojedynek). */
public class ReportCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public ReportCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length < 1) {
            player.sendMessage("§cUżycie: /report <gracz> [powód]");
            return true;
        }
        if (!plugin.getModeration().canReport(player.getUniqueId())) {
            player.sendMessage("§cPoczekaj chwilę przed kolejnym zgłoszeniem.");
            return true;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "(brak powodu)";
        plugin.getModeration().recordReport(player.getUniqueId());

        boolean anyAdminOnline = false;
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (!admin.hasPermission(ADMIN_PERMISSION)) {
                continue;
            }
            anyAdminOnline = true;
            admin.sendMessage(Branding.chatPrefix() + "§c§l[Zgłoszenie] §f" + player.getName()
                    + " §7zgłasza §f" + targetName + " §7- powód: §f" + reason);
        }
        player.sendMessage(anyAdminOnline
                ? "§aZgłoszono " + targetName + " administracji."
                : "§eZgłoszono, ale żaden admin nie jest teraz online.");
        return true;
    }
}
