package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/** Daje graczowi "Wieczny Miecz" - custom model (netherite sword jako baza, animowana tekstura). */
public class GiveEternalSwordCommand implements CommandExecutor {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";
    private static final String DISPLAY_NAME = "§6§lWieczny Miecz";

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
        target.sendMessage("§e§lOtrzymałeś: " + DISPLAY_NAME);
        if (!target.equals(sender)) {
            sender.sendMessage("§aDano " + target.getName() + " miecz Wieczny Miecz.");
        }
        return true;
    }

    public static ItemStack createEternalSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.setDisplayName(DISPLAY_NAME);
        meta.setItemModel(new NamespacedKey("boxpvp", "eternal_sword"));

        List<String> lore = Arrays.asList(
                "§7Najlepszy miecz na całym serwerze!",
                "§7Wykuty z niekończącej się mocy."
        );
        meta.setLore(lore);

        sword.setItemMeta(meta);

        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        sword.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        sword.addUnsafeEnchantment(Enchantment.LOOTING, 3);
        sword.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
        sword.addUnsafeEnchantment(Enchantment.KNOCKBACK, 2);
        sword.addUnsafeEnchantment(Enchantment.SWEEPING_EDGE, 3);
        sword.addUnsafeEnchantment(Enchantment.MENDING, 1);

        return sword;
    }
}
