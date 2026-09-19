package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** Zgłoszenie trwającego pojedynku adminom online - dorzuca gotową podpowiedź /duel admincheck. */
public class ReportDuelCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public ReportDuelCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        UUID opponentUuid = plugin.getDuels().getOpponent(player.getUniqueId());
        if (opponentUuid == null) {
            player.sendMessage("§cNie jesteś w trakcie pojedynku.");
            return true;
        }

        String reason = args.length > 0 ? String.join(" ", args) : "(brak powodu)";
        String opponentName = Bukkit.getOfflinePlayer(opponentUuid).getName();
        boolean anyAdminOnline = false;

        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (!admin.hasPermission(ADMIN_PERMISSION)) {
                continue;
            }
            anyAdminOnline = true;
            admin.sendMessage(Branding.chatPrefix() + "§c§l[Zgłoszenie pojedynku] §f" + player.getName()
                    + " §7vs §f" + opponentName + " §7- powód: §f" + reason);
            admin.sendMessage("§7Podejrzyj: §a/duel admincheck " + player.getName());
        }

        player.sendMessage(anyAdminOnline
                ? "§aZgłoszono pojedynek administracji."
                : "§eZgłoszono, ale żaden admin nie jest teraz online.");
        return true;
    }
}
