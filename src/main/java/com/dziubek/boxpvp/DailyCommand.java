package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class DailyCommand implements CommandExecutor {

    private final BoxPvpPlugin plugin;

    public DailyCommand(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Tej komendy może użyć tylko gracz.");
            return true;
        }
        Player player = (Player) sender;

        if (args.length >= 2 && args[0].equalsIgnoreCase("setday")) {
            if (!player.hasPermission("boxpvp.daily.admin")) {
                player.sendMessage("§cNie masz uprawnień do konfiguracji nagród daily.");
                return true;
            }
            int day;
            try {
                day = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cNumer dnia musi być liczbą.");
                return true;
            }
            if (day < 1 || day > 7) {
                player.sendMessage("§cDzień musi być od 1 do 7.");
                return true;
            }

            Inventory inv = Bukkit.createInventory(new DailyConfigGuiHolder(day), 9, Branding.accent("Nagroda - Dzień " + day));
            ItemStack existing = plugin.getDaily().getDayReward(day);
            if (existing != null) {
                inv.setItem(4, existing);
            }
            GuiDecor.fillEmpty(inv);
            player.openInventory(inv);
            player.sendMessage("§aUmieść nagrodę w środkowym slocie i zamknij ekwipunek, aby zapisać.");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("seteffect")) {
            if (!player.hasPermission("boxpvp.daily.admin")) {
                player.sendMessage("§cNie masz uprawnień do konfiguracji nagród daily.");
                return true;
            }
            CrateEffect effect = CrateEffect.fromString(args[1]);
            if (effect == null) {
                player.sendMessage("§cNieznany efekt. Dostępne: " + CrateEffect.listNames());
                return true;
            }
            plugin.getDaily().setEffect(effect);
            player.sendMessage("§aUstawiono efekt odbioru daily: '" + effect.getDisplayName() + "'.");
            return true;
        }

        if (!plugin.getDaily().canClaimToday(player.getUniqueId())) {
            player.sendMessage("§cJuż odebrałeś dzisiejszą nagrodę. Wróć jutro!");
            return true;
        }

        int cycleDay = plugin.getDaily().getNextCycleDay(player.getUniqueId());
        ItemStack reward = plugin.getDaily().getDayReward(cycleDay);

        if (reward == null) {
            player.sendMessage("§cNagroda za dzień " + cycleDay + " nie jest jeszcze skonfigurowana. Poproś admina o /daily setday " + cycleDay + ".");
            return true;
        }

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward.clone());
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }

        plugin.getDaily().markClaimed(player.getUniqueId(), cycleDay);
        plugin.getStats().recordDailyClaim(player.getUniqueId(), player.getName());
        plugin.getDaily().getEffect().play(plugin, player.getLocation());
        RewardRevealEffect.play(plugin, player, reward);
        player.sendMessage("§aOdebrano nagrodę za dzień §f" + cycleDay + "§a/7 z rzędu!");

        if (cycleDay == 7) {
            TitleUtil.show(player, "§a§l7/7!", "§fStreak ukończony - gratulacje!");
        }
        return true;
    }
}
