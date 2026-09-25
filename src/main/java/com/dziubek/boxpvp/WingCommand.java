package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /wings - GUI wyboru odblokowanych skrzydeł.
 * /wings giveitem <gatunek> [gracz] [ilość] - admin: daje przedmiot odblokowujący dane skrzydła,
 * który MOŻNA POTEM RĘCZNIE dołożyć do dowolnej skrzyni przez /crate create (np. "cosmetic") -
 * ten kod nigdy sam nie dotyka konfiguracji żadnej skrzyni.
 */
public class WingCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public WingCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Tej komendy może użyć tylko gracz.");
                return true;
            }
            plugin.getWingGui().open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("giveitem")) {
            return handleGiveItem(sender, args);
        }

        sendHelp(sender);
        return true;
    }

    private boolean handleGiveItem(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /wings giveitem <gatunek> [gracz] [ilość] §7- dostępne: "
                    + String.join(", ", WingSpecies.ORDER));
            return true;
        }

        String species = args[1].toLowerCase();
        if (WingSpecies.of(species) == null) {
            sender.sendMessage("§cNieznany gatunek '" + species + "'. Dostępne: " + String.join(", ", WingSpecies.ORDER));
            return true;
        }

        Player target;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage("§cGracz " + args[2] + " nie jest online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cUżycie: /wings giveitem <gatunek> <gracz> [ilość]");
            return true;
        }

        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
                // zostaje 1
            }
        }

        target.getInventory().addItem(WingUnlockItem.create(plugin, species, amount));
        sender.sendMessage("§aDano " + target.getName() + " " + amount + "x przedmiot odblokowujący skrzydła '" + species
                + "'. §7Dołóż go do skrzyni przez /crate create <nazwa>, jeśli chcesz, żeby wypadał jako nagroda.");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§b/wings §7- GUI wyboru odblokowanych skrzydeł");
        sender.sendMessage("§b/wings giveitem <gatunek> [gracz] [ilość] §7- przedmiot odblokowujący skrzydła (admin, "
                + "wrzuć go potem ręcznie do skrzyni przez /crate create)");
    }
}
