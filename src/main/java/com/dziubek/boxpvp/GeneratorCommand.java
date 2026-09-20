package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
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
            case "booster":
                return handleBooster(sender, args);
            case "rotshop":
                return handleRotShop(sender, args);
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
        sender.sendMessage("§c/bpvp event lmszone1 §7/ §c lmszone2 §7- wyznacza strefę Ostatniego Ocalałego (dwa przeciwległe rogi)");
        sender.sendMessage("§c/bpvp event lms §7- otwiera zapisy (/lms join) na Ostatniego Ocalałego");
        sender.sendMessage("§c/bpvp leaderboard setlocation <kills|coins|killstreak|elo|duelwins|envoy> §7- stawia tablicę tu gdzie stoisz");
        sender.sendMessage("§c/bpvp movehologram <nazwa> §7- przestawia hologram generatora w to miejsce gdzie stoisz");
        sender.sendMessage("§c/bpvp craftblock add|remove <materiał> §7- blokuje/odblokowuje crafting materiału (np. NETHERITE_BLOCK)");
        sender.sendMessage("§c/bpvp craftblock list §7- lista zablokowanych materiałów");
        sender.sendMessage("§c/bpvp giveset <poziom 1-10> §7- daje pełny zestaw PvP (zbroja + miecz/kilof/siekiera/łopata)");
        sender.sendMessage("§c/bpvp teleportto normal|mega|megazombie §7- teleportuje Cię tam, gdzie ostatnio spadła dana skrzynka");
        sender.sendMessage("§c/bpvp duel setworld <świat> §7- ustawia świat-szablon areny pojedynków i przenosi Cię tam");
        sender.sendMessage("§c/bpvp duel setpos1 §7/ §c setpos2 §7- ustawia OSOBNE pozycje startowe dla gracza 1 i 2 (musisz być w świecie-szablonie)");
        sender.sendMessage("§c/bpvp duel gototemplateworld §7- wraca do świata-szablonu areny (np. żeby coś dobudować)");
        sender.sendMessage("§c/bpvp booster give <gracz> <mnożnik> <minuty> §7- daje osobisty czasowy booster zarobków "
                + "(stakuje się z już aktywnym - mnożniki mnożą się, czas się dodaje)");
        sender.sendMessage("§c/bpvp booster remove <gracz> §7- zdejmuje aktywny booster gracza");
        sender.sendMessage("§c/bpvp rotshop add <cena> §7- dodaje trzymany przedmiot do puli rotującego sklepu (/rotshop)");
        sender.sendMessage("§c/bpvp rotshop remove <numer> §7- usuwa przedmiot z puli (numer z /bpvp rotshop list)");
        sender.sendMessage("§c/bpvp rotshop list §7- lista puli rotującego sklepu");
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
            case "lmszone1":
            case "lmszone2": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Tej komendy może użyć tylko gracz.");
                    return true;
                }
                int corner = action.equals("lmszone1") ? 1 : 2;
                plugin.getLms().setCorner(corner, ((Player) sender).getLocation());
                sender.sendMessage("§aUstawiono róg " + corner + " strefy Ostatniego Ocalałego w tym miejscu.");
                return true;
            }
            case "lms": {
                if (plugin.getLms().isSignupOpen() || plugin.getLms().isRunning()) {
                    sender.sendMessage("§cOstatni ocalały już trwa albo zapisy są otwarte.");
                    return true;
                }
                boolean opened = plugin.getLms().openSignup();
                sender.sendMessage(opened ? "§aOtworzono zapisy na Ostatniego Ocalałego!"
                        : "§cNajpierw wyznacz strefę: /bpvp event lmszone1 i /bpvp event lmszone2 (dwa przeciwległe rogi).");
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
                + "| hill <minuty> | hillzone1 | hillzone2 | lms | lmszone1 | lmszone2");
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
            sender.sendMessage("§cUżycie: /bpvp leaderboard setlocation <kills|coins|killstreak|elo|duelwins|envoy>");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        String type = args[2].toLowerCase();
        if (!type.equals("kills") && !type.equals("coins") && !type.equals("killstreak") && !type.equals("elo")
                && !type.equals("duelwins") && !type.equals("envoy")) {
            sender.sendMessage("§cDostępne tablice: kills, coins, killstreak, elo, duelwins, envoy.");
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
            sender.sendMessage("§cUżycie: /bpvp duel setworld <świat>|setpos1|setpos2|gototemplateworld");
            return true;
        }
        Player player = (Player) sender;
        String action = args[1].toLowerCase();

        if (action.equals("gototemplateworld")) {
            String templateWorldName = plugin.getDuels().getTemplateWorldName();
            if (templateWorldName == null) {
                sender.sendMessage("§cNajpierw ustaw świat-szablon: /bpvp duel setworld <świat>.");
                return true;
            }
            World world = Bukkit.getWorld(templateWorldName);
            if (world == null) {
                sender.sendMessage("§cŚwiat-szablon '" + templateWorldName + "' nie jest wczytany.");
                return true;
            }
            Location arenaPos = plugin.getDuels().getArenaPosition();
            player.teleport(arenaPos != null ? arenaPos : world.getSpawnLocation());
            sender.sendMessage("§aPrzeniesiono Cię do świata-szablonu areny ('" + templateWorldName + "'). "
                    + "Możesz tu budować/edytować - zmiany będą kopiowane do każdego nowego pojedynku.");
            return true;
        }

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
                    + "Stań w miejscu startowym 1. gracza i wpisz §f/bpvp duel setpos1§a, potem 2. gracza i §f/bpvp duel setpos2§a.");
            return true;
        }
        if (action.equals("setpos1") || action.equals("setpos2")) {
            String templateWorld = plugin.getDuels().getTemplateWorldName();
            if (templateWorld == null) {
                sender.sendMessage("§cNajpierw ustaw świat-szablon: /bpvp duel setworld <świat>.");
                return true;
            }
            if (!templateWorld.equals(player.getWorld().getName())) {
                sender.sendMessage("§cMusisz stać w świecie-szablonie areny ('" + templateWorld + "'), żeby ustawić pozycję.");
                return true;
            }
            int slot = action.equals("setpos1") ? 1 : 2;
            plugin.getDuels().setArenaPosition(slot, player.getLocation());
            sender.sendMessage("§aUstawiono pozycję startową " + slot + ". gracza w tym miejscu.");
            return true;
        }
        sender.sendMessage("§cUżycie: /bpvp duel setworld <świat>|setpos1|setpos2|gototemplateworld");
        return true;
    }

    private boolean handleBooster(CommandSender sender, String[] args) {
        if (args.length < 3 || !(args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("remove"))) {
            sender.sendMessage("§cUżycie: /bpvp booster give <gracz> <mnożnik> <minuty> §7lub§c /bpvp booster remove <gracz>");
            return true;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cGracz '" + args[2] + "' nie jest online.");
            return true;
        }

        if (args[1].equalsIgnoreCase("remove")) {
            boolean removed = plugin.getBoosters().remove(target.getUniqueId());
            if (removed) {
                sender.sendMessage("§aZdjęto booster gracza " + target.getName() + ".");
                target.sendMessage(Branding.chatPrefix() + "§eTwój booster zarobków został zdjęty przez administrację.");
            } else {
                sender.sendMessage("§cTen gracz nie ma aktywnego boostera.");
            }
            return true;
        }

        if (args.length < 5) {
            sender.sendMessage("§cUżycie: /bpvp booster give <gracz> <mnożnik> <minuty>");
            return true;
        }
        double multiplier;
        int minutes;
        try {
            multiplier = Double.parseDouble(args[3]);
            minutes = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cMnożnik i minuty muszą być liczbami.");
            return true;
        }
        plugin.getBoosters().give(target, multiplier, minutes);
        return true;
    }

    private boolean handleRotShop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp rotshop add <cena>|remove <numer>|list");
            return true;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "add": {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Tej komendy może użyć tylko gracz.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /bpvp rotshop add <cena bazowa>");
                    return true;
                }
                Player player = (Player) sender;
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) {
                    sender.sendMessage("§cTrzymaj w ręce przedmiot, który chcesz dodać do puli.");
                    return true;
                }
                double price;
                try {
                    price = Double.parseDouble(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cCena musi być liczbą.");
                    return true;
                }
                plugin.getRotatingShop().addToPool(hand, price);
                sender.sendMessage("§aDodano do puli rotującego sklepu: " + hand.getType() + " za " + price + "$ (bazowo).");
                return true;
            }
            case "remove": {
                if (args.length < 3) {
                    sender.sendMessage("§cUżycie: /bpvp rotshop remove <numer>");
                    return true;
                }
                int index;
                try {
                    index = Integer.parseInt(args[2]) - 1;
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNumer musi być liczbą.");
                    return true;
                }
                boolean removed = plugin.getRotatingShop().removeFromPool(index);
                sender.sendMessage(removed ? "§aUsunięto z puli." : "§cNie znaleziono takiego numeru.");
                return true;
            }
            case "list": {
                List<RotatingShopManager.PoolEntry> pool = plugin.getRotatingShop().getPool();
                if (pool.isEmpty()) {
                    sender.sendMessage("§ePula rotującego sklepu: §fbrak");
                    return true;
                }
                sender.sendMessage(Branding.accent("--- Pula rotującego sklepu ---"));
                for (int i = 0; i < pool.size(); i++) {
                    RotatingShopManager.PoolEntry entry = pool.get(i);
                    sender.sendMessage("§7#" + (i + 1) + " §f" + entry.icon.getType() + " §7- §a" + entry.basePrice + "$");
                }
                return true;
            }
            default:
                sender.sendMessage("§cUżycie: /bpvp rotshop add <cena>|remove <numer>|list");
                return true;
        }
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
