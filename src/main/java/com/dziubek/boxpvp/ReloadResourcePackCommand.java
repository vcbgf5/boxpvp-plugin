package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Wypycha aktualny resource pack ponownie do wszystkich online graczy, bez potrzeby rejoina -
 * przydatne od razu po zmianie samej paczki (admin nie musi restartować pluginu ani czekać, aż
 * gracze sami się przełączą).
 *
 * NAJPIERW liczy hash na nowo (refreshHash() - sieciowe, więc async) - bez tego wysyłałby graczom
 * stary hash policzony raz przy starcie pluginu, a Minecraft rozpoznaje "nowa paczka" właśnie po
 * zmianie hasha, nie samej treści pod URL-em - więc bez odświeżenia klient myślał, że już ma
 * aktualną paczkę i w ogóle jej nie pobierał, mimo że plik pod linkiem się realnie zmienił.
 */
public class ReloadResourcePackCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final Plugin plugin;

    public ReloadResourcePackCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        sender.sendMessage("§7Liczę aktualny hash paczki...");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean refreshed = ResourcePackPusher.refreshHash();
            PetResourcePackMerger.refresh((BoxPvpPlugin) plugin);
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ResourcePackPusher.push(player);
                }
                if (refreshed) {
                    sender.sendMessage("§aŚwieży hash policzony, paczka wysłana ponownie do "
                            + Bukkit.getOnlinePlayers().size() + " graczy.");
                } else {
                    sender.sendMessage("§eNie udało się pobrać paczki do przeliczenia hasha - "
                            + "wysłano ze starym hashem (mogła nie zostać uznana za nową).");
                }
            });
        });
        return true;
    }
}
