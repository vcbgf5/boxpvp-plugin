package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

        boolean publicSub = sub.equals("list") || sub.equals("sellprice") || sub.equals("craftblock");
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
            case "movehologram":
                return handleMoveHologram(sender, args);
            case "craftblock":
                return handleCraftBlock(sender, args);
            case "giveset":
                return handleGiveSet(sender, args);
            case "teleportto":
                return handleTeleportTo(sender, args);
            case "duel":
                return handleDuelSetup(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Branding.accent("--- /bpvp ---"));
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
        sender.sendMessage("§c/bpvp event mega §7- ręcznie zrzuca rzadszą MEGA skrzynkę-event (większa, lepszy loot)");
        sender.sendMessage("§c/bpvp event envoyitem add|clear|list §7- pula nagród skrzynki-eventu (add = trzymany przedmiot)");
        sender.sendMessage("§c/bpvp event megaitem add|clear|list §7- pula nagród MEGA skrzynki-eventu");
        sender.sendMessage("§c/bpvp event zombie §7- ręcznie zrzuca zombie-event w losowe miejsce obszaru");
        sender.sendMessage("§c/bpvp event zombieitem add|clear|list §7- pula nagród za zabicie zombie-eventu");
        sender.sendMessage("§c/bpvp event megazombie §7- zrzuca wielką skrzynkę, z której po 10s wyjdzie Giant (mega-zombie)");
        sender.sendMessage("§c/bpvp event megazombieitem add|clear|list §7- pula nagród za zabicie mega-zombie");
        sender.sendMessage("§c/bpvp event hillzone1 §7/ §c hillzone2 §7- wyznacza strefę Króla Wzgórza (dwa przeciwległe rogi)");
        sender.sendMessage("§c/bpvp event hill <minuty> §7- startuje Króla Wzgórza (najwięcej sekund w strefie wygrywa)");
        sender.sendMessage("§c/bpvp leaderboard setlocation <kills|coins|killstreak|envoy> §7- stawia tablicę tu gdzie stoisz");
        sender.sendMessage("§c/bpvp movehologram <nazwa> §7- przestawia hologram generatora w to miejsce gdzie stoisz");
        sender.sendMessage("§c/bpvp craftblock add|remove <materiał> §7- blokuje/odblokowuje crafting materiału (np. NETHERITE_BLOCK)");
        sender.sendMessage("§c/bpvp craftblock list §7- lista zablokowanych materiałów");
        sender.sendMessage("§c/bpvp giveset <poziom 1-10> §7- daje pełny zestaw PvP (zbroja + miecz/kilof/siekiera/łopata)");
        sender.sendMessage("§c/bpvp teleportto normal|mega|megazombie §7- teleportuje Cię tam, gdzie ostatnio spadła dana skrzynka");
        sender.sendMessage("§c/bpvp duel setworld <świat> §7- ustawia świat-szablon areny pojedynków i przenosi Cię tam");
        sender.sendMessage("§c/bpvp duel setpos §7- ustawia pozycję startową areny w miejscu gdzie stoisz (musisz być w świecie-szablonie)");
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
                sender.sendMessage(Branding.accent("--- Ceny auto-sprzedaży ---"));
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

    private boolean handleCraftBlock(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp craftblock add <materiał>|remove <materiał>|list");
            return true;
        }
        String action = args[1].toLowerCase();
        if (!action.equals("list") && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień do zarządzania blokadami craftingu.");
            return true;
        }

        switch (action) {
            case "add": {
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /bpvp craftblock add <materiał>");
                    return true;
                }
                Material material = Material.matchMaterial(args[2].toUpperCase());
                if (material == null) {
                    sender.sendMessage("§cNieznany materiał: '" + args[2] + "'.");
                    return true;
                }
                boolean added = plugin.getCraftBlocks().add(material);
                sender.sendMessage(added ? "§aZablokowano crafting " + material + "." : "§cTen materiał jest już zablokowany.");
                return true;
            }
            case "remove": {
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /bpvp craftblock remove <materiał>");
                    return true;
                }
                Material material = Material.matchMaterial(args[2].toUpperCase());
                boolean removed = material != null && plugin.getCraftBlocks().remove(material);
                sender.sendMessage(removed ? "§aOdblokowano crafting." : "§cTen materiał nie jest zablokowany.");
                return true;
            }
            case "list": {
                List<Material> blocked = plugin.getCraftBlocks().all();
                if (blocked.isEmpty()) {
                    sender.sendMessage("§eZablokowane materiały: §fbrak");
                    return true;
                }
                sender.sendMessage(Branding.accent("--- Zablokowany crafting ---"));
                for (Material material : blocked) {
                    sender.sendMessage("§7- §f" + material);
                }
                return true;
            }
            default:
                sender.sendMessage("§cUżycie: /bpvp craftblock add <materiał>|remove <materiał>|list");
                return true;
        }
    }

    private boolean handleEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendEventUsage(sender);
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
                boolean started = plugin.getEvents().spawnEnvoy(false);
                sender.sendMessage(started ? "§aSkrzynka-event spada z nieba w losowe miejsce wyznaczonego obszaru!"
                        : "§cNajpierw wyznacz obszar: /bpvp event setzone1 i /bpvp event setzone2 (dwa przeciwległe rogi).");
                return true;
            }
            case "mega": {
                boolean started = plugin.getEvents().spawnEnvoy(true);
                sender.sendMessage(started ? "§5MEGA skrzynka-event spada z nieba w losowe miejsce wyznaczonego obszaru!"
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
            case "megaitem":
                return handleMegaItem(sender, args);
            case "zombie": {
                boolean started = plugin.getEvents().spawnZombieEvent();
                sender.sendMessage(started ? "§aZombie-event spada z nieba w losowe miejsce wyznaczonego obszaru!"
                        : "§cNajpierw wyznacz obszar: /bpvp event setzone1 i /bpvp event setzone2 (dwa przeciwległe rogi).");
                return true;
            }
            case "zombieitem":
                return handleZombieItem(sender, args);
            case "megazombie": {
                if (plugin.getGiantEvent().isEventActive()) {
                    sender.sendMessage("§cMega-zombie już trwa - poczekaj, aż zniknie albo zostanie zabity.");
                    return true;
                }
                boolean started = plugin.getEvents().spawnMegaZombieEvent();
                sender.sendMessage(started ? "§4WIELKA SKRZYNKA spada z nieba w losowe miejsce wyznaczonego obszaru - wyjdzie z niej Giant!"
                        : "§cNajpierw wyznacz obszar: /bpvp event setzone1 i /bpvp event setzone2 (dwa przeciwległe rogi).");
                return true;
            }
            case "megazombieitem":
                return handleMegaZombieItem(sender, args);
            case "hillzone1":
            case "hillzone2": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Tej komendy może użyć tylko gracz.");
                    return true;
                }
                int corner = action.equals("hillzone1") ? 1 : 2;
                plugin.getHillEvent().setCorner(corner, ((Player) sender).getLocation());
                sender.sendMessage("§aUstawiono róg " + corner + " strefy Króla Wzgórza w tym miejscu.");
                return true;
            }
            case "hill": {
                if (plugin.getHillEvent().isActive()) {
                    sender.sendMessage("§cKról Wzgórza już trwa.");
                    return true;
                }
                int minutes = args.length >= 3 ? parseIntOr(args[2], 3) : 3;
                boolean started = plugin.getHillEvent().start(minutes);
                sender.sendMessage(started ? "§aWystartował Król Wzgórza na " + minutes + " min!"
                        : "§cNajpierw wyznacz strefę: /bpvp event hillzone1 i /bpvp event hillzone2 (dwa przeciwległe rogi).");
                return true;
            }
            default:
                sendEventUsage(sender);
                return true;
        }
    }

    private void sendEventUsage(CommandSender sender) {
        sender.sendMessage("§cUżycie: /bpvp event start <minuty> [mnożnik] | envoy | mega | zombie | megazombie | setzone1 | setzone2 "
                + "| envoyitem add|clear|list | megaitem add|clear|list | zombieitem add|clear|list | megazombieitem add|clear|list "
                + "| hill <minuty> | hillzone1 | hillzone2");
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

    private boolean handleMegaItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /bpvp event megaitem add|clear|list");
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
                sender.sendMessage("§cTrzymaj w ręce przedmiot, który chcesz dodać do puli nagród mega.");
                return true;
            }
            plugin.getEnvoy().addMegaReward(hand);
            sender.sendMessage("§aDodano do puli nagród MEGA skrzynki-eventu: " + hand.getType() + " x" + hand.getAmount());
            return true;
        }
        if (action.equals("clear")) {
            plugin.getEnvoy().clearMegaRewards();
            sender.sendMessage("§aWyczyszczono pulę nagród MEGA skrzynki-eventu.");
            return true;
        }
        if (action.equals("list")) {
            List<ItemStack> rewards = plugin.getEnvoy().megaRewards();
            sender.sendMessage("§ePula nagród MEGA skrzynki-eventu (" + rewards.size() + "):");
            for (ItemStack item : rewards) {
                sender.sendMessage("§7- §f" + item.getType() + " x" + item.getAmount());
            }
            return true;
        }
        sender.sendMessage("§cUżycie: /bpvp event megaitem add|clear|list");
        return true;
    }

    private boolean handleZombieItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /bpvp event zombieitem add|clear|list");
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
                sender.sendMessage("§cTrzymaj w ręce przedmiot, który chcesz dodać do puli nagród za zombie-event.");
                return true;
            }
            plugin.getZombieEvent().addReward(hand);
            sender.sendMessage("§aDodano do puli nagród za zombie-event: " + hand.getType() + " x" + hand.getAmount());
            return true;
        }
        if (action.equals("clear")) {
            plugin.getZombieEvent().clearRewards();
            sender.sendMessage("§aWyczyszczono pulę nagród za zombie-event.");
            return true;
        }
        if (action.equals("list")) {
            List<ItemStack> rewards = plugin.getZombieEvent().rewards();
            sender.sendMessage("§ePula nagród za zombie-event (" + rewards.size() + "):");
            for (ItemStack item : rewards) {
                sender.sendMessage("§7- §f" + item.getType() + " x" + item.getAmount());
            }
            return true;
        }
        sender.sendMessage("§cUżycie: /bpvp event zombieitem add|clear|list");
        return true;
    }

    private boolean handleMegaZombieItem(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /bpvp event megazombieitem add|clear|list");
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
                sender.sendMessage("§cTrzymaj w ręce przedmiot, który chcesz dodać do puli nagród za mega-zombie.");
                return true;
            }
            plugin.getGiantEvent().addReward(hand);
            sender.sendMessage("§aDodano do puli nagród za mega-zombie: " + hand.getType() + " x" + hand.getAmount());
            return true;
        }
        if (action.equals("clear")) {
            plugin.getGiantEvent().clearRewards();
            sender.sendMessage("§aWyczyszczono pulę nagród za mega-zombie.");
            return true;
        }
        if (action.equals("list")) {
            List<ItemStack> rewards = plugin.getGiantEvent().rewards();
            sender.sendMessage("§ePula nagród za mega-zombie (" + rewards.size() + "):");
            for (ItemStack item : rewards) {
                sender.sendMessage("§7- §f" + item.getType() + " x" + item.getAmount());
            }
            return true;
        }
        sender.sendMessage("§cUżycie: /bpvp event megazombieitem add|clear|list");
        return true;
    }

    private boolean handleMoveHologram(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp movehologram <nazwa>");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        boolean moved = plugin.getGenerators().setHologramLocation(args[1], ((Player) sender).getLocation());
        sender.sendMessage(moved ? "§aPrzestawiono hologram generatora '" + args[1] + "' w to miejsce."
                : "§cNie znaleziono generatora '" + args[1] + "'.");
        return true;
    }

    private boolean handleLeaderboard(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("setlocation")) {
            sender.sendMessage("§cUżycie: /bpvp leaderboard setlocation <kills|coins|killstreak|envoy>");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        String type = args[2].toLowerCase();
        if (!type.equals("kills") && !type.equals("coins") && !type.equals("killstreak") && !type.equals("envoy")) {
            sender.sendMessage("§cDostępne tablice: kills, coins, killstreak, envoy.");
            return true;
        }
        plugin.getLeaderboards().setLocation(type, ((Player) sender).getLocation());
        sender.sendMessage("§aUstawiono tablicę '" + type + "' w tym miejscu.");
        return true;
    }

    private boolean handleGiveSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp giveset <poziom 1-10>");
            return true;
        }
        int level;
        try {
            level = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cPoziom musi być liczbą (1-10).");
            return true;
        }
        if (!GearSetManager.isValidLevel(level)) {
            sender.sendMessage("§cPoziom musi być w zakresie 1-10.");
            return true;
        }
        GearSetManager.giveSet((Player) sender, level);
        sender.sendMessage("§aOtrzymujesz zestaw PvP - Poziom " + level + " (zbroja + miecz, kilof, siekiera, łopata).");
        return true;
    }

    private boolean handleDuelSetup(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp duel setworld <świat>|setpos");
            return true;
        }
        Player player = (Player) sender;
        String action = args[1].toLowerCase();

        if (action.equals("setworld")) {
            if (args.length < 3) {
                sender.sendMessage("§cUżycie: /bpvp duel setworld <świat>");
                return true;
            }
            World world = plugin.getDuels().setTemplateWorld(args[2]);
            if (world == null) {
                sender.sendMessage("§cNie udało się wczytać/utworzyć świata '" + args[2] + "'.");
                return true;
            }
            player.teleport(world.getSpawnLocation());
            sender.sendMessage("§aUstawiono świat-szablon areny na '" + args[2] + "' i przeniesiono Cię tam. "
                    + "Stań w miejscu startowym areny i wpisz §f/bpvp duel setpos§a.");
            return true;
        }
        if (action.equals("setpos")) {
            String templateWorld = plugin.getDuels().getTemplateWorldName();
            if (templateWorld == null) {
                sender.sendMessage("§cNajpierw ustaw świat-szablon: /bpvp duel setworld <świat>.");
                return true;
            }
            if (!templateWorld.equals(player.getWorld().getName())) {
                sender.sendMessage("§cMusisz stać w świecie-szablonie areny ('" + templateWorld + "'), żeby ustawić pozycję.");
                return true;
            }
            plugin.getDuels().setArenaPosition(player.getLocation());
            sender.sendMessage("§aUstawiono pozycję startową areny pojedynków w tym miejscu.");
            return true;
        }
        sender.sendMessage("§cUżycie: /bpvp duel setworld <świat>|setpos");
        return true;
    }

    private boolean handleTeleportTo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp teleportto normal|mega|megazombie");
            return true;
        }
        String type = args[1].toLowerCase();
        Location target;
        String label;
        switch (type) {
            case "normal":
                target = plugin.getEnvoy().getLastLocation(false);
                label = "Skrzynka-event";
                break;
            case "mega":
                target = plugin.getEnvoy().getLastLocation(true);
                label = "MEGA skrzynka-event";
                break;
            case "megazombie":
                target = plugin.getGiantEvent().getLastLocation();
                label = "Wielka skrzynka (mega-zombie)";
                break;
            default:
                sender.sendMessage("§cUżycie: /bpvp teleportto normal|mega|megazombie");
                return true;
        }
        if (target == null || target.getWorld() == null) {
            sender.sendMessage("§cJeszcze żadna taka skrzynka nie spadła.");
            return true;
        }
        ((Player) sender).teleport(target.clone().add(0, 1, 0));
        sender.sendMessage("§aTeleportowano do miejsca ostatniej: " + label + ".");
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
