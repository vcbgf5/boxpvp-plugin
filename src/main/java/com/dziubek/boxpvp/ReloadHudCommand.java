package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Pobiera na żywo hud-config.json ORAZ aktualną paczkę tekstur z GitHuba (świeży hash liczony w
 * locie, nie z jara) i od razu wysyła wszystko wszystkim online graczom - bez potrzeby
 * przebudowania/wgrania nowego jara. Przydatne do włączania/wyłączania eksperymentalnych
 * elementów (TEST1/TEST2) i dostrajania pozycji w locie, nawet po samej zmianie paczki w repo.
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
        sender.sendMessage("§7Pobieram hud-config.json i aktualną paczkę tekstur z GitHuba...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean configOk = plugin.getBalanceHud().getConfig().reload();
            boolean packOk = ResourcePackPusher.refreshHash();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getBalanceHud().refreshAll();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ResourcePackPusher.push(player);
                }
                sender.sendMessage((configOk ? "§aConfig" : "§cConfig (nieudane, zostaje stary)") + " | "
                        + (packOk ? "§apaczka tekstur" : "§cpaczka tekstur (nieudane, zostaje stary hash)")
                        + " §7- odświeżono " + Bukkit.getOnlinePlayers().size() + " graczom.");
            });
        });
        return true;
    }
}
