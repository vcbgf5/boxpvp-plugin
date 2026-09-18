package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ArenaTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "setlobby", "addspawn", "clearspawns", "setkit",
            "start", "stop", "join", "leave", "generator", "list"
    );
    private static final List<String> NEEDS_ARENA_NAME = List.of(
            "setlobby", "addspawn", "clearspawns", "setkit", "start", "stop", "join"
    );
    private static final List<String> GENERATOR_ACTIONS = List.of("wand", "create", "remove", "list");

    private final BoxPvpPlugin plugin;

    public ArenaTabCompleter(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("generator")) {
            if (args.length == 2) {
                return filter(GENERATOR_ACTIONS, args[1]);
            }
            if (args.length == 3 && !args[1].equalsIgnoreCase("wand")) {
                return filter(plugin.getArenas().names(), args[2]);
            }
            return new ArrayList<>();
        }

        if (args.length == 2 && NEEDS_ARENA_NAME.contains(sub)) {
            return filter(plugin.getArenas().names(), args[1]);
        }

        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
