package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * Pobiera na żywo hud-config.json z GitHuba (HudConfigLoader) i od razu odświeża HUD wszystkim
 * online graczom - bez potrzeby przebudowania/wgrania nowego jara. Przydatne do włączania/
 * wyłączania eksperymentalnych elementów (TEST1/TEST2) i dostrajania pozycji w locie.
 */
public class ReloadHudCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public ReloadHudCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        sender.sendMessage("§7Pobieram hud-config.json z GitHuba...");
        plugin.getBalanceHud().reloadConfigAndRefresh(ok -> sender.sendMessage(ok
                ? "§aHUD przeładowany z GitHuba i odświeżony wszystkim online graczom."
                : "§cNie udało się pobrać hud-config.json - HUD odświeżony ze starą konfiguracją."));
        return true;
    }
}
