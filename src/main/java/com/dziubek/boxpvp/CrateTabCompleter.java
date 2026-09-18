package com.dziubek.boxpvp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CrateTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "create", "givekey", "bind", "unbind", "sethologram",
            "seteffect", "setidleeffect", "setprivate", "setfreecooldown", "setdisplayheight",
            "purgedisplays", "preview", "list"
    );
    private static final List<String> BOOLEANS = List.of("true", "false");
    private static final List<String> NEEDS_CRATE_NAME = List.of(
            "givekey", "bind", "sethologram", "seteffect", "setidleeffect",
            "setprivate", "setfreecooldown", "preview"
    );

    private final BoxPvpPlugin plugin;

    public CrateTabCompleter(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2 && NEEDS_CRATE_NAME.contains(sub)) {
            return filter(plugin.getCrates().names(), args[1]);
        }

        if (args.length == 3) {
            if (sub.equals("seteffect")) {
                return filter(effectNames(false), args[2]);
            }
            if (sub.equals("setidleeffect")) {
                return filter(effectNames(true), args[2]);
            }
            if (sub.equals("setprivate")) {
                return filter(BOOLEANS, args[2]);
            }
        }

        return new ArrayList<>();
    }

    private List<String> effectNames(boolean withNone) {
        List<String> names = new ArrayList<>();
        for (CrateEffect effect : CrateEffect.values()) {
            names.add(effect.name());
        }
        if (withNone) {
            names.add("none");
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
