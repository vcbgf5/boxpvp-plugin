package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

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

        boolean publicSub = sub.equals("list") || sub.equals("sellprice");
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
            case "sellprice":
                return handleSellPrice(sender, args);
            case "event":
                return handleEvent(sender, args);
            case "leaderboard":
                return handleLeaderboard(sender, args);
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
        sender.sendMessage("§c/bpvp sellprice set|remove <blok> [cena] §7- ceny auto-sprzedaży bloków z generatorów");
        sender.sendMessage("§c/bpvp sellprice list §7- lista cen auto-sprzedaży");
        sender.sendMessage("§c/bpvp event start <minuty> [mnożnik] §7- czasowy event x2 (domyślnie) na monety");
        sender.sendMessage("§c/bpvp event setzone1 §7- 1. róg obszaru, w którym mogą spadać skrzynki-event (tu gdzie stoisz)");
        sender.sendMessage("§c/bpvp event setzone2 §7- 2. róg tego obszaru (przeciwległy róg)");
        sender.sendMessage("§c/bpvp event envoy §7- ręcznie zrzuca skrzynkę-event w losowe miejsce tego obszaru");
        sender.sendMessage("§c/bpvp event envoyitem add|clear|list §7- pula nagród skrzynki-eventu (add = trzymany przedmiot)");
        sender.sendMessage("§c/bpvp leaderboard setlocation <kills|coins|killstreak> §7- stawia tablicę TOP 10 tu gdzie stoisz");
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

    private boolean handleSellPrice(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp sellprice set <blok> <cena>|remove <blok>|list");
            return true;
        }
        String action = args[1].toLowerCase();
        if (!action.equals("list") && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień do zarządzania cenami auto-sprzedaży.");
            return true;
        }

        switch (action) {
            case "set": {
                if (args.length < 4) {
                    sender.sendMessage("§cUżycie: /bpvp sellprice set <blok> <cena>");
                    return true;
                }
                Material material = Material.matchMaterial(args[2].toUpperCase());
                if (material == null || !material.isBlock()) {
                    sender.sendMessage("§cNieznany blok: '" + args[2] + "'.");
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cCena musi być liczbą.");
                    return true;
                }
                plugin.getSell().setPrice(material, price);
                sender.sendMessage("§aUstawiono cenę auto-sprzedaży " + material + " na " + price + "$.");
                return true;
            }
            case "remove": {
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /bpvp sellprice remove <blok>");
                    return true;
                }
                Material material = Material.matchMaterial(args[2].toUpperCase());
                boolean removed = material != null && plugin.getSell().removePrice(material);
                sender.sendMessage(removed ? "§aUsunięto cenę auto-sprzedaży." : "§cTen blok nie ma ustawionej ceny.");
                return true;
            }
            case "list": {
                Map<Material, Double> prices = plugin.getSell().all();
                if (prices.isEmpty()) {
                    sender.sendMessage("§eCeny auto-sprzedaży: §fbrak");
                    return true;
                }
                sender.sendMessage("§6§l--- Ceny auto-sprzedaży ---");
                for (Map.Entry<Material, Double> entry : prices.entrySet()) {
                    sender.sendMessage("§7- §f" + entry.getKey() + " §7- §a" + entry.getValue() + "$");
                }
                return true;
            }
            default:
                sender.sendMessage("§cUżycie: /bpvp sellprice set <blok> <cena>|remove <blok>|list");
                return true;
        }
    }

    private boolean handleEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp event start <minuty> [mnożnik] | envoy | setzone1 | setzone2 | envoyitem add|clear|list");
            return true;
        }
        String action = args[1].toLowerCase();

        switch (action) {
            case "start": {
                int minutes = args.length >= 3 ? parseIntOr(args[2], 10) : 10;
                double multiplier = args.length >= 4 ? parseDoubleOr(args[3], 2.0) : 2.0;
                plugin.getEvents().startCoinEvent(multiplier, minutes);
                sender.sendMessage("§aWystartował event: §fx" + multiplier + " monet przez " + minutes + " min.");
                return true;
            }
            case "envoy": {
                boolean started = plugin.getEvents().spawnEnvoy();
                sender.sendMessage(started ? "§aSkrzynka-event spada z nieba w losowe miejsce wyznaczonego obszaru!"
                        : "§cNajpierw wyznacz obszar: /bpvp event setzone1 i /bpvp event setzone2 (dwa przeciwległe rogi).");
                return true;
            }
            case "setzone1":
            case "setzone2": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Tej komendy może użyć tylko gracz.");
                    return true;
                }
                int corner = action.equals("setzone1") ? 1 : 2;
                plugin.getEvents().setEnvoyZoneCorner(corner, ((Player) sender).getLocation());
                sender.sendMessage("§aUstawiono róg " + corner + " obszaru skrzynek-event w tym miejscu.");
                return true;
            }
            case "envoyitem":
                return handleEnvoyItem(sender, args);
            default:
                sender.sendMessage("§cUżycie: /bpvp event start <minuty> [mnożnik] | envoy | setzone1 | setzone2 | envoyitem add|clear|list");
                return true;
        }
    }

    private boolean handleEnvoyItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /bpvp event envoyitem add|clear|list");
            return true;
        }
        String action = args[2].toLowerCase();

        if (action.equals("add")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Tej komendy może użyć tylko gracz.");
                return true;
            }
            Player player = (Player) sender;
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                sender.sendMessage("§cTrzymaj w ręce przedmiot, który chcesz dodać do puli nagród.");
                return true;
            }
            plugin.getEnvoy().addReward(hand);
            sender.sendMessage("§aDodano do puli nagród skrzynki-eventu: " + hand.getType() + " x" + hand.getAmount());
            return true;
        }
        if (action.equals("clear")) {
            plugin.getEnvoy().clearRewards();
            sender.sendMessage("§aWyczyszczono pulę nagród skrzynki-eventu.");
            return true;
        }
        if (action.equals("list")) {
            List<ItemStack> rewards = plugin.getEnvoy().rewards();
            sender.sendMessage("§ePula nagród skrzynki-eventu (" + rewards.size() + "):");
            for (ItemStack item : rewards) {
                sender.sendMessage("§7- §f" + item.getType() + " x" + item.getAmount());
            }
            return true;
        }
        sender.sendMessage("§cUżycie: /bpvp event envoyitem add|clear|list");
        return true;
    }

    private boolean handleLeaderboard(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("setlocation")) {
            sender.sendMessage("§cUżycie: /bpvp leaderboard setlocation <kills|coins|killstreak>");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        String type = args[2].toLowerCase();
        if (!type.equals("kills") && !type.equals("coins") && !type.equals("killstreak")) {
            sender.sendMessage("§cDostępne tablice: kills, coins, killstreak.");
            return true;
        }
        plugin.getLeaderboards().setLocation(type, ((Player) sender).getLocation());
        sender.sendMessage("§aUstawiono tablicę '" + type + "' w tym miejscu.");
        return true;
    }

    private static int parseIntOr(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static double parseDoubleOr(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
