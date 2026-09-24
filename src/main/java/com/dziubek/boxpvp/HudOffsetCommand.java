package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Żywe kalibrowanie pozycji testowego "przyklejonego do ekranu" HUD-u (ScreenAnchorTestManager) -
 * bez tej komendy każda korekta wymagałaby przebudowania i wgrania nowego jara.
 * Użycie: /hudoffset <1|2> <prawo> <gora> <dystans>
 * np. /hudoffset 1 1.6 1.3 3.0
 */
public class HudOffsetCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public HudOffsetCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        ScreenAnchorTestManager hud = plugin.getScreenAnchorTest();
        if (args.length == 0) {
            sender.sendMessage("§7Aktualnie: §e1§7: prawo=" + hud.line1Right + " gora=" + hud.line1Up + " dystans=" + hud.line1Dist);
            sender.sendMessage("§7Aktualnie: §b2§7: prawo=" + hud.line2Right + " gora=" + hud.line2Up + " dystans=" + hud.line2Dist);
            sender.sendMessage("§7Użycie: /hudoffset <1|2> <prawo> <gora> <dystans>");
            return true;
        }
        if (args.length != 4) {
            sender.sendMessage("§cUżycie: /hudoffset <1|2> <prawo> <gora> <dystans>");
            return true;
        }
        double right, up, dist;
        try {
            right = Double.parseDouble(args[1]);
            up = Double.parseDouble(args[2]);
            dist = Double.parseDouble(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLiczby muszą być poprawnymi wartościami dziesiętnymi.");
            return true;
        }
        if ("1".equals(args[0])) {
            hud.line1Right = right;
            hud.line1Up = up;
            hud.line1Dist = dist;
        } else if ("2".equals(args[0])) {
            hud.line2Right = right;
            hud.line2Up = up;
            hud.line2Dist = dist;
        } else {
            sender.sendMessage("§cPierwszy argument musi być 1 albo 2.");
            return true;
        }
        if (sender instanceof Player) {
            hud.spawn((Player) sender);
        }
        sender.sendMessage("§aZaktualizowano linię " + args[0] + ": prawo=" + right + " gora=" + up + " dystans=" + dist);
        return true;
    }
}
