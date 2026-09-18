package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GeneratorTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("wand", "create", "remove", "list", "villager");
    private static final List<String> VILLAGER_ACTIONS = List.of("create", "remove");

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

        if (sub.equals("remove") && args.length == 2) {
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

        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
