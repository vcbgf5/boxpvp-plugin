package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class TpaAcceptCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public TpaAcceptCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }

        Player player = (Player) sender;
        UUID requesterUuid = plugin.getTpa().getRequester(player.getUniqueId());

        if (requesterUuid == null) {
            player.sendMessage("§cNie masz żadnej oczekującej prośby o teleportację (albo wygasła).");
            return true;
        }

        Player requester = Bukkit.getPlayer(requesterUuid);
        plugin.getTpa().clear(player.getUniqueId());

        if (requester == null || !requester.isOnline()) {
            player.sendMessage("§cGracz, który wysłał prośbę, jest już offline.");
            return true;
        }

        if (plugin.getCombatManager().isTagged(requester.getUniqueId())) {
            requester.sendMessage("§cNie możesz się teleportować - jesteś w walce!");
            player.sendMessage("§c" + requester.getName() + " jest w walce, nie może się teraz teleportować.");
            return true;
        }

        requester.sendMessage("§aTwoja prośba o teleportację została zaakceptowana!");
        player.sendMessage("§a" + requester.getName() + " teleportuje się do Ciebie...");
        plugin.getTeleportDelay().start(requester, player.getLocation(), "TPA do " + player.getName());
        return true;
    }
}
