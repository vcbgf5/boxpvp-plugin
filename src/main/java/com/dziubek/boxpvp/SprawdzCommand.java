package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SprawdzCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public SprawdzCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player admin = (Player) sender;
        if (!admin.hasPermission(ADMIN_PERMISSION)) {
            admin.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length < 1) {
            sendHelp(admin);
            return true;
        }

        if (args[0].equalsIgnoreCase("setpoint")) {
            plugin.getCheckpoint().setPoint(admin.getLocation());
            admin.sendMessage("§aUstawiono miejsce sprawdzania tu, gdzie stoisz.");
            return true;
        }

        if (args[0].equalsIgnoreCase("z") || args[0].equalsIgnoreCase("zwolnij")) {
            if (args.length < 2) {
                admin.sendMessage("§cUżycie: /sprawdz z <gracz>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null || !plugin.getCheckpoint().release(target)) {
                admin.sendMessage("§cTen gracz nie jest teraz sprawdzany.");
                return true;
            }
            admin.sendMessage("§aWypuszczono " + target.getName() + ".");
            target.sendMessage("§aZostałeś wypuszczony ze sprawdzenia.");
            return true;
        }

        if (!plugin.getCheckpoint().isConfigured()) {
            admin.sendMessage("§cMiejsce sprawdzania nie jest jeszcze ustawione (/sprawdz setpoint).");
            return true;
        }
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            admin.sendMessage("§cGracz '" + args[0] + "' nie jest online.");
            return true;
        }
        if (!plugin.getCheckpoint().check(admin, target)) {
            admin.sendMessage("§cTen gracz jest już sprawdzany.");
            return true;
        }
        admin.sendMessage("§aZabrano " + target.getName() + " do sprawdzenia.");
        target.sendMessage("§c§lZOSTAŁEŚ ZABRANY DO SPRAWDZENIA §7przez administrację. Poczekaj.");
        return true;
    }

    private void sendHelp(Player admin) {
        admin.sendMessage(Branding.accent("--- /sprawdz ---"));
        admin.sendMessage("§c/sprawdz setpoint §7- ustawia miejsce sprawdzania tu, gdzie stoisz");
        admin.sendMessage("§c/sprawdz <gracz> §7- zabiera gracza (i Ciebie) do sprawdzenia, trzyma go tam");
        admin.sendMessage("§c/sprawdz z <gracz> §7- wypuszcza gracza z powrotem tam, gdzie był");
    }
}
