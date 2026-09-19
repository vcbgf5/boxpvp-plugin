package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BankTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("create", "remove", "list", "price");
    private static final List<String> PRICE_ACTIONS = List.of("set", "remove", "list");

    private final BoxPvpPlugin plugin;

    public BankTabCompleter(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("remove") && args.length == 2) {
            return filter(plugin.getBanks().names(), args[1]);
        }

        if (sub.equals("price")) {
            if (args.length == 2) {
                return filter(PRICE_ACTIONS, args[1]);
            }
            if (args.length == 3 && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("remove"))) {
                return filterMaterials(args[2]);
            }
        }

        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
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
