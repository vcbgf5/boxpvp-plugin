package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class GeneratorTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("wand", "create", "remove", "list", "villager",
            "sellprice", "event", "leaderboard", "movehologram", "craftblock", "giveset", "teleportto",
            "duel", "booster", "rotshop");
    private static final List<String> VILLAGER_ACTIONS = List.of("create", "remove");
    private static final List<String> SELLPRICE_ACTIONS = List.of("set", "remove", "list");
    private static final List<String> EVENT_ACTIONS = List.of("start", "envoy", "mega", "zombie", "megazombie", "setzone1", "setzone2",
            "envoyitem", "megaitem", "zombieitem", "megazombieitem", "hill", "hillzone1", "hillzone2",
            "lms", "lmszone1", "lmszone2");
    private static final List<String> ENVOYITEM_ACTIONS = List.of("add", "clear", "list");
    private static final List<String> LEADERBOARD_ACTIONS = List.of("setlocation");
    private static final List<String> LEADERBOARD_TYPES = List.of("kills", "coins", "killstreak", "elo", "duelwins", "envoy");
    private static final List<String> CRAFTBLOCK_ACTIONS = List.of("add", "remove", "list");
    private static final List<String> GIVESET_LEVELS = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
    private static final List<String> TELEPORTTO_TARGETS = List.of("normal", "mega", "megazombie");
    private static final List<String> DUEL_ACTIONS = List.of("setworld", "setpos1", "setpos2", "gototemplateworld");
    private static final List<String> BOOSTER_ACTIONS = List.of("give", "remove");
    private static final List<String> ROTSHOP_ACTIONS = List.of("add", "remove", "list");

    private final BoxPvpPlugin plugin;

    public GeneratorTabCompleter(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();

        if ((sub.equals("remove") || sub.equals("movehologram")) && args.length == 2) {
            return filter(plugin.getGenerators().names(), args[1]);
        }

        if (sub.equals("villager")) {
            if (args.length == 2) {
                return filter(VILLAGER_ACTIONS, args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
                return filter(plugin.getTraders().names(), args[2]);
            }
        }

        if (sub.equals("sellprice") && args.length == 2) {
            return filter(SELLPRICE_ACTIONS, args[1]);
        }

        if (sub.equals("event")) {
            if (args.length == 2) {
                return filter(EVENT_ACTIONS, args[1]);
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("envoyitem") || args[1].equalsIgnoreCase("megaitem")
                    || args[1].equalsIgnoreCase("zombieitem") || args[1].equalsIgnoreCase("megazombieitem"))) {
                return filter(ENVOYITEM_ACTIONS, args[2]);
            }
        }

        if (sub.equals("leaderboard")) {
            if (args.length == 2) {
                return filter(LEADERBOARD_ACTIONS, args[1]);
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("setlocation")) {
                return filter(LEADERBOARD_TYPES, args[2]);
            }
        }

        if (sub.equals("craftblock")) {
            if (args.length == 2) {
                return filter(CRAFTBLOCK_ACTIONS, args[1]);
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
                return filterMaterials(args[2]);
            }
        }

        if (sub.equals("giveset") && args.length == 2) {
            return filter(GIVESET_LEVELS, args[1]);
        }

        if (sub.equals("teleportto") && args.length == 2) {
            return filter(TELEPORTTO_TARGETS, args[1]);
        }

        if (sub.equals("duel") && args.length == 2) {
            return filter(DUEL_ACTIONS, args[1]);
        }

        if (sub.equals("booster")) {
            if (args.length == 2) {
                return filter(BOOSTER_ACTIONS, args[1]);
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("remove"))) {
                return filter(onlinePlayerNames(), args[2]);
            }
        }

        if (sub.equals("rotshop") && args.length == 2) {
            return filter(ROTSHOP_ACTIONS, args[1]);
        }

        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
    }

    private List<String> filterMaterials(String prefix) {
        String upper = prefix.toUpperCase();
        return Arrays.stream(Material.values())
                .map(Enum::name)
                .filter(name -> name.startsWith(upper))
                .limit(30)
                .collect(Collectors.toList());
    }
}
