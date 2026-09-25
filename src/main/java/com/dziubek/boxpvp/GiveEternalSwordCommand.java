package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Daje graczowi "Eternal Sword" - custom model (netherite sword jako baza, animowana tekstura). */
public class GiveEternalSwordCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage("§cNie masz uprawnień, aby dawać miecz innym graczom.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage("§cGracz " + args[0] + " nie jest online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            if (!sender.hasPermission(ADMIN_PERMISSION)) {
                sender.sendMessage("§cNie masz uprawnień.");
                return true;
            }
            target = player;
        } else {
            sender.sendMessage("§cPodaj gracza: /giveeternalsword <gracz>");
            return true;
        }

        target.getInventory().addItem(createEternalSword());
        target.sendMessage("§e§lOtrzymałeś: §6§lEternal Sword");
        if (!target.equals(sender)) {
            sender.sendMessage("§aDano " + target.getName() + " miecz Eternal Sword.");
        }
        return true;
    }

    public static ItemStack createEternalSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName("§6§lEternal Sword");
        meta.setItemModel(new NamespacedKey("boxpvp", "eternal_sword"));
        sword.setItemMeta(meta);
        return sword;
    }
}
