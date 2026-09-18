package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public ShopCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    private static final String ADMIN_PERMISSION = "boxpvp.shop.admin";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Tej komendy może użyć tylko gracz.");
                return true;
            }
            plugin.getShopGui().openMain((Player) sender);
            return true;
        }

        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień do zarządzania sklepem.");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                return handleCreateCategory(sender, args);
            case "additem":
                return handleAddItem(sender, args);
            case "removeitem":
                return handleRemoveItem(sender, args);
            case "removecategory":
                return handleRemoveCategory(sender, args);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§cUżycia:");
        sender.sendMessage("§7/sklep §f- otwiera sklep");
        sender.sendMessage("§7/sklep create <id> <material> <nazwa...> §f- nowa kategoria");
        sender.sendMessage("§7/sklep additem <kategoria> §f- otwiera kreator (trzymaj ikonę w ręce)");
        sender.sendMessage("§7/sklep removeitem <kategoria> <numer>");
        sender.sendMessage("§7/sklep removecategory <kategoria>");
    }

    private boolean handleCreateCategory(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUżycie: /sklep create <id> <material> <nazwa...>");
            return true;
        }

        String id = args[1];
        Material icon;
        try {
            icon = Material.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§cNieprawidłowy material: " + args[2]);
            return true;
        }

        StringBuilder name = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (i > 3) {
                name.append(" ");
            }
            name.append(args[i]);
        }

        plugin.getShop().createCategory(id, icon, name.toString());
        sender.sendMessage("§aStworzono kategorię '" + id + "'.");
        return true;
    }

    private boolean handleAddItem(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz (przedmiot z ręki staje się ikoną w sklepie).");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /sklep additem <kategoria>");
            sender.sendMessage("§7Trzymaj w ręce przedmiot, który ma być ikoną - otworzy się kreator.");
            return true;
        }

        Player player = (Player) sender;
        String category = args[1];

        if (!plugin.getShop().categoryExists(category)) {
            player.sendMessage("§cKategoria '" + category + "' nie istnieje. Najpierw: /sklep create " + category + " <material> <nazwa>");
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            player.sendMessage("§cMusisz trzymać w ręce przedmiot, który ma być ikoną w sklepie.");
            return true;
        }

        ShopConfigSession session = new ShopConfigSession(category, held.clone());
        plugin.getShopConfig().startSession(player.getUniqueId(), session);
        plugin.getShopConfigGui().open(player);
        return true;
    }

    private boolean handleRemoveItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /sklep removeitem <kategoria> <numer>");
            return true;
        }
        int index;
        try {
            index = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cNumer musi być liczbą.");
            return true;
        }
        boolean removed = plugin.getShop().removeItem(args[1], index);
        sender.sendMessage(removed
                ? "§aUsunięto przedmiot #" + index + " z '" + args[1] + "'."
                : "§cNie znaleziono takiego przedmiotu.");
        return true;
    }

    private boolean handleRemoveCategory(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /sklep removecategory <kategoria>");
            return true;
        }
        boolean removed = plugin.getShop().removeCategory(args[1]);
        sender.sendMessage(removed
                ? "§aUsunięto kategorię '" + args[1] + "'."
                : "§cNie znaleziono takiej kategorii.");
        return true;
    }
}
