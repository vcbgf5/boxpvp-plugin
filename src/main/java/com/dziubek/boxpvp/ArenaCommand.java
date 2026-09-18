package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class ArenaCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public ArenaCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        // "join", "leave" i "list" moze kazdy, reszta to admin
        boolean publicSub = sub.equals("join") || sub.equals("leave") || sub.equals("list");
        if (!publicSub && !sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("§cNie masz uprawnień do zarządzania Box PvP.");
            return true;
        }

        switch (sub) {
            case "create":
                return handleCreate(sender, args);
            case "setlobby":
                return handleSetLobby(sender, args);
            case "addspawn":
                return handleAddSpawn(sender, args);
            case "clearspawns":
                return handleClearSpawns(sender, args);
            case "setkit":
                return handleSetKit(sender, args);
            case "start":
                return handleStart(sender, args);
            case "stop":
                return handleStop(sender, args);
            case "join":
                return handleJoin(sender, args);
            case "leave":
                return handleLeave(sender);
            case "generator":
                return handleGenerator(sender, args);
            case "list":
                return handleList(sender);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6§l--- /bpvp (Box PvP) ---");
        sender.sendMessage("§c/bpvp create <arena> §7- tworzy nową arenę");
        sender.sendMessage("§c/bpvp setlobby <arena> §7- ustawia lobby w Twojej pozycji");
        sender.sendMessage("§c/bpvp addspawn <arena> §7- dodaje punkt spawnu gracza w Twojej pozycji");
        sender.sendMessage("§c/bpvp clearspawns <arena> §7- czyści wszystkie punkty spawnu");
        sender.sendMessage("§c/bpvp setkit <arena> §7- kit startowy = Twój AKTUALNY ekwipunek");
        sender.sendMessage("§c/bpvp start <arena> §7- startuje rundę dla graczy w poczekalni (min. 2)");
        sender.sendMessage("§c/bpvp stop <arena> §7- przerywa trwającą rundę");
        sender.sendMessage("§c/bpvp join <arena> §7- dołącza do poczekalni areny");
        sender.sendMessage("§c/bpvp leave §7- opuszcza poczekalnię/rundę");
        sender.sendMessage("§c/bpvp list §7- lista aren");
        sender.sendMessage("§c/bpvp generator wand §7- daje różdżkę do zaznaczania obszaru");
        sender.sendMessage("§c/bpvp generator create <arena> <nazwa> <blok> <sekundy> §7- generator na zaznaczonym obszarze");
        sender.sendMessage("§c/bpvp generator remove <arena> <nazwa> §7- usuwa generator");
        sender.sendMessage("§c/bpvp generator list <arena> §7- lista generatorów areny");
    }

    private boolean handleCreate(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp create <arena>");
            return true;
        }
        String name = args[1];
        if (plugin.getArenas().exists(name)) {
            sender.sendMessage("§cArena '" + name + "' już istnieje.");
            return true;
        }
        plugin.getArenas().create(name);
        sender.sendMessage("§aUtworzono arenę '" + name + "'. Ustaw teraz /bpvp setlobby i /bpvp addspawn.");
        return true;
    }

    private boolean handleSetLobby(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp setlobby <arena>");
            return true;
        }
        String name = args[1];
        if (!requireArena(sender, name)) {
            return true;
        }
        Player player = (Player) sender;
        plugin.getArenas().setLobby(name, player.getLocation());
        sender.sendMessage("§aUstawiono lobby areny '" + name + "' w Twojej pozycji.");
        return true;
    }

    private boolean handleAddSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp addspawn <arena>");
            return true;
        }
        String name = args[1];
        if (!requireArena(sender, name)) {
            return true;
        }
        Player player = (Player) sender;
        int count = plugin.getArenas().addSpawn(name, player.getLocation());
        sender.sendMessage("§aDodano punkt spawnu #" + count + " dla areny '" + name + "'.");
        return true;
    }

    private boolean handleClearSpawns(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp clearspawns <arena>");
            return true;
        }
        String name = args[1];
        if (!requireArena(sender, name)) {
            return true;
        }
        plugin.getArenas().clearSpawns(name);
        sender.sendMessage("§aWyczyszczono punkty spawnu areny '" + name + "'.");
        return true;
    }

    /**
     * Kit startowy areny to po prostu aktualny ekwipunek admina w momencie wywołania
     * (bez osobnego systemu kitów - ten plugin jest samodzielny).
     */
    private boolean handleSetKit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp setkit <arena> §7(zapisuje Twój aktualny ekwipunek jako kit startowy)");
            return true;
        }
        String name = args[1];
        if (!requireArena(sender, name)) {
            return true;
        }
        Player player = (Player) sender;
        plugin.getArenas().setKit(name, player.getInventory().getContents());
        sender.sendMessage("§aZapisano Twój aktualny ekwipunek jako kit startowy areny '" + name + "'.");
        return true;
    }

    private boolean handleStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp start <arena>");
            return true;
        }
        String name = args[1];
        if (!requireArena(sender, name)) {
            return true;
        }
        plugin.getArenas().start(name, sender);
        return true;
    }

    private boolean handleStop(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp stop <arena>");
            return true;
        }
        String name = args[1];
        if (!requireArena(sender, name)) {
            return true;
        }
        plugin.getArenas().stop(name);
        sender.sendMessage("§aPrzerwano rundę na arenie '" + name + "'.");
        return true;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp join <arena>");
            return true;
        }
        String name = args[1];
        if (!requireArena(sender, name)) {
            return true;
        }
        plugin.getArenas().join((Player) sender, name);
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        plugin.getArenas().leave((Player) sender);
        sender.sendMessage("§aOpuściłeś Box PvP.");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> names = plugin.getArenas().names();
        if (names.isEmpty()) {
            sender.sendMessage("§eAreny: §fbrak");
            return true;
        }
        sender.sendMessage("§eAreny:");
        for (String name : names) {
            boolean running = plugin.getArenas().isRunning(name);
            sender.sendMessage("§7 - §f" + name + " §7(" + (running ? "§ctrwa runda" : "§awolna") + "§7)");
        }
        return true;
    }

    private boolean handleGenerator(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUżycie: /bpvp generator wand|create|remove|list");
            return true;
        }
        String action = args[1].toLowerCase();
        switch (action) {
            case "wand":
                return handleGeneratorWand(sender);
            case "create":
                return handleGeneratorCreate(sender, args);
            case "remove":
                return handleGeneratorRemove(sender, args);
            case "list":
                return handleGeneratorList(sender, args);
            default:
                sender.sendMessage("§cUżycie: /bpvp generator wand|create|remove|list");
                return true;
        }
    }

    private boolean handleGeneratorWand(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;
        player.getInventory().addItem(plugin.getGenerators().createWand());
        player.sendMessage("§aOtrzymujesz różdżkę generatora - LPM = pozycja 1, PPM = pozycja 2.");
        return true;
    }

    private boolean handleGeneratorCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        if (args.length < 6) {
            sender.sendMessage("§cUżycie: /bpvp generator create <arena> <nazwa> <blok> <sekundy>");
            return true;
        }
        String arenaName = args[2];
        if (!requireArena(sender, arenaName)) {
            return true;
        }
        String genName = args[3];
        Material material = Material.matchMaterial(args[4].toUpperCase());
        if (material == null || !material.isBlock()) {
            sender.sendMessage("§cNieznany blok: '" + args[4] + "'.");
            return true;
        }
        int intervalSeconds;
        try {
            intervalSeconds = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cLiczba sekund musi być liczbą.");
            return true;
        }
        if (intervalSeconds < 5) {
            sender.sendMessage("§cMinimalny interwał to 5 sekund.");
            return true;
        }

        boolean created = plugin.getGenerators().createGenerator((Player) sender, arenaName, genName, material, intervalSeconds);
        if (created) {
            sender.sendMessage("§aUtworzono generator '" + genName + "' (" + material + ", co " + intervalSeconds + "s) na arenie '" + arenaName + "'.");
        }
        return true;
    }

    private boolean handleGeneratorRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage("§cUżycie: /bpvp generator remove <arena> <nazwa>");
            return true;
        }
        boolean removed = plugin.getGenerators().removeGenerator(args[2], args[3]);
        sender.sendMessage(removed ? "§aUsunięto generator '" + args[3] + "'." : "§cNie znaleziono takiego generatora.");
        return true;
    }

    private boolean handleGeneratorList(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cUżycie: /bpvp generator list <arena>");
            return true;
        }
        List<String> names = plugin.getGenerators().namesFor(args[2]);
        if (names.isEmpty()) {
            sender.sendMessage("§eGeneratory areny '" + args[2] + "': §fbrak");
            return true;
        }
        sender.sendMessage("§eGeneratory areny '" + args[2] + "': §f" + String.join(", ", names));
        return true;
    }

    private boolean requireArena(CommandSender sender, String name) {
        if (!plugin.getArenas().exists(name)) {
            sender.sendMessage("§cArena '" + name + "' nie istnieje (najpierw /bpvp create " + name + ").");
            return false;
        }
        return true;
    }
}
