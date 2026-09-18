package com.dziubek.boxpvp;

import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class KitCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public KitCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage("§cUżycie: /kit <nazwa> §7lub §c/kit create <nazwa>");
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (!player.hasPermission("boxpvp.kit.admin")) {
                player.sendMessage("§cNie masz uprawnień do tworzenia kitów.");
                return true;
            }
            if (args.length < 2) {
                player.sendMessage("§cUżycie: /kit create <nazwa>");
                return true;
            }
            String name = args[1];
            plugin.getKits().saveKit(name, player.getInventory().getContents());
            player.sendMessage("§aZapisano kit '" + name + "' z zawartości Twojego ekwipunku.");
            return true;
        }

        if (args[0].equalsIgnoreCase("migrate")) {
            if (!player.hasPermission("boxpvp.kit.admin")) {
                player.sendMessage("§cNie masz uprawnień.");
                return true;
            }
            int migrated = plugin.getKits().migrateOldFormat();
            player.sendMessage(migrated > 0
                    ? "§aZmigrowano " + migrated + " starych kitów do nowego formatu!"
                    : "§eNie znaleziono starych kitów do migracji.");
            return true;
        }

        String name = args[0];
        if (!plugin.getKits().exists(name)) {
            player.sendMessage("§cKit '" + name + "' nie istnieje.");
            return true;
        }

        if (!plugin.getKits().hasAccess(name, player)) {
            player.sendMessage("§cNie masz dostępu do tego kitu! Kup go w §f/sklep §club poproś o uprawnienie §fkit." + name);
            return true;
        }

        if (plugin.getKits().isOnCooldown(name, player.getUniqueId())) {
            long left = plugin.getKits().secondsLeft(name, player.getUniqueId());
            player.sendMessage("§cMusisz poczekać jeszcze " + left + "s zanim znowu odbierzesz ten kit.");
            return true;
        }

        List<ItemStack> kitItems = plugin.getKits().getItems(name);
        for (ItemStack item : kitItems) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item.clone());
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }

        plugin.getKits().markUsed(name, player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        if (!kitItems.isEmpty()) {
            RewardRevealEffect.play(plugin, player, kitItems.get(0));
        }
        player.sendMessage("§aOtrzymujesz kit '" + name + "'!");
        return true;
    }
}
