package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * /bpvp - generatory bloków (wolnostojące, bez pojęcia "areny") i handlarze-wieśniacy.
 * Sam PvP odbywa się poza kontrolą pluginu (jeden duży, otwarty box) - tu configurujemy
 * tylko ekonomiczną pętlę: generator -> kopanie -> /sklep -> lepszy sprzęt.
 */
public class GeneratorCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public GeneratorCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        boolean publicSub = sub.equals("list");
        if (!publicSub && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień do zarządzania generatorami/handlarzami.");
            return true;
        }

        switch (sub) {
            case "wand":
                return handleWand(sender);
            case "create":
                return handleCreate(sender, args);
            case "remove":
                return handleRemove(sender, args);
            case "list":
                return handleList(sender);
            case "villager":
                return handleVillager(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l--- /bpvp ---");
        sender.sendMessage("§c/bpvp wand §7- różdżka do zaznaczania obszaru generatora (LPM=pozycja 1, PPM=pozycja 2)");
        sender.sendMessage("§c/bpvp create <nazwa> <blok> <sekundy> §7- generator na zaznaczonym obszarze");
        sender.sendMessage("§c/bpvp remove <nazwa> §7- usuwa generator");
        sender.sendMessage("§c/bpvp list §7- lista generatorów");
        sender.sendMessage("§c/bpvp villager create <nazwa> §7- stawia handlarza w Twojej pozycji");
        sender.sendMessage("§c/bpvp villager remove <nazwa> §7- usuwa handlarza");
    }

    private boolean handleWand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        player.getInventory().addItem(plugin.getGenerators().createWand());
        player.sendMessage("§aOtrzymujesz różdżkę generatora - LPM = pozycja 1, PPM = pozycja 2.");
        return true;
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 4) {
            sender.sendMessage("§cUżycie: /bpvp create <nazwa> <blok> <sekundy>");
            return true;
        }
        String genName = args[1];
        if (plugin.getGenerators().exists(genName)) {
            sender.sendMessage("§cGenerator '" + genName + "' już istnieje.");
            return true;
        }
        Material material = Material.matchMaterial(args[2].toUpperCase());
        if (material == null || !material.isBlock()) {
            sender.sendMessage("§cNieznany blok: '" + args[2] + "'.");
            return true;
        }
        int intervalSeconds;
        try {
            intervalSeconds = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLiczba sekund musi być liczbą.");
            return true;
        }
        if (intervalSeconds < 5) {
            sender.sendMessage("§cMinimalny interwał to 5 sekund.");
            return true;
        }

        boolean created = plugin.getGenerators().createGenerator((Player) sender, genName, material, intervalSeconds);
        if (created) {
            sender.sendMessage("§aUtworzono generator '" + genName + "' (" + material + ", co " + intervalSeconds + "s).");
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp remove <nazwa>");
            return true;
        }
        boolean removed = plugin.getGenerators().removeGenerator(args[1]);
        sender.sendMessage(removed ? "§aUsunięto generator '" + args[1] + "'." : "§cNie znaleziono takiego generatora.");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> names = plugin.getGenerators().names();
        if (names.isEmpty()) {
            sender.sendMessage("§eGeneratory: §fbrak");
            return true;
        }
        sender.sendMessage("§eGeneratory: §f" + String.join(", ", names));
        return true;
    }

    private boolean handleVillager(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /bpvp villager create|remove <nazwa>");
            return true;
        }
        Player player = (Player) sender;
        String action = args[1].toLowerCase();
        String name = args[2];

        if (action.equals("create")) {
            if (plugin.getTraders().exists(name)) {
                sender.sendMessage("§cHandlarz '" + name + "' już istnieje.");
                return true;
            }
            plugin.getTraders().createTrader(name, player.getLocation());
            sender.sendMessage("§aPostawiono handlarza '" + name + "'. Shift+PPM otwiera edycję, zwykłe PPM otwiera sklep.");
            return true;
        }
        if (action.equals("remove")) {
            boolean removed = plugin.getTraders().removeTrader(name);
            sender.sendMessage(removed ? "§aUsunięto handlarza '" + name + "'." : "§cNie znaleziono takiego handlarza.");
            return true;
        }
        sender.sendMessage("§cUżycie: /bpvp villager create|remove <nazwa>");
        return true;
    }
}
