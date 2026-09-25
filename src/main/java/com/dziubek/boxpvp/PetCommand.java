package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /pet, /pety, /pets - GUI wyboru aktywnego peta; /pet npc|givekey - narzędzia admina. */
public class PetCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public PetCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Tej komendy może użyć tylko gracz.");
                return true;
            }
            plugin.getPetGui().open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "npc" -> {
                return handleNpc(sender, args);
            }
            case "givekey" -> {
                return handleGiveKey(sender, args);
            }
            default -> {
                sendHelp(sender);
                return true;
            }
        }
    }

    private boolean handleNpc(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /pet npc create|remove <nazwa>");
            return true;
        }
        String action = args[1].toLowerCase();
        String name = args[2];

        if (action.equals("create")) {
            if (plugin.getPetShelters().exists(name)) {
                sender.sendMessage("§cSchronisko '" + name + "' już istnieje.");
                return true;
            }
            plugin.getPetShelters().create(name, player.getLocation());
            sender.sendMessage("§aStworzono Schronisko Petów '" + name + "' w Twoim miejscu.");
            return true;
        }
        if (action.equals("remove")) {
            if (plugin.getPetShelters().remove(name)) {
                sender.sendMessage("§aUsunięto Schronisko Petów '" + name + "'.");
            } else {
                sender.sendMessage("§cSchronisko '" + name + "' nie istnieje.");
            }
            return true;
        }
        sender.sendMessage("§cUżycie: /pet npc create|remove <nazwa>");
        return true;
    }

    private boolean handleGiveKey(CommandSender sender, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień.");
            return true;
        }
        Player target;
        int amount = 1;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cGracz " + args[1] + " nie jest online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cUżycie: /pet givekey <gracz> [ilość]");
            return true;
        }
        if (args.length >= 3) {
            try {
                amount = Math.max(1, Integer.parseInt(args[2]));
            } catch (NumberFormatException ignored) {
                // zostaje 1
            }
        }
        target.getInventory().addItem(PetKeyItem.create(plugin, amount));
        sender.sendMessage("§aDano " + target.getName() + " " + amount + "x Klucz do Peta.");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§d/pet §7- otwiera GUI wyboru aktywnego peta");
        sender.sendMessage("§d/pet npc create|remove <nazwa> §7- Schronisko Petów (admin)");
        sender.sendMessage("§d/pet givekey <gracz> [ilość] §7- daje Klucz do Peta (admin)");
    }
}
