package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PetTabCompleter implements TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("npc", "givekey");
    private static final List<String> NPC_ACTIONS = List.of("create", "remove");

    private final BoxPvpPlugin plugin;

    public PetTabCompleter(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if (sub.equals("npc")) {
                return filter(NPC_ACTIONS, args[1]);
            }
            if (sub.equals("givekey")) {
                return filter(onlinePlayerNames(), args[1]);
            }
        }
        if (args.length == 3 && sub.equals("npc") && args[1].equalsIgnoreCase("remove")) {
            return filter(plugin.getPetShelters().names(), args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
