package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;

/**
 * Gotowe zestawy PvP (zbroja + miecz/siekiera/włócznia/kilof/łopata) na 10 poziomów - /bpvp
 * giveset <poziom> ubiera gracza w pancerz i wrzuca narzędzia do ekwipunku. Poziom rośnie
 * materiałem (drewno/skóra -> żelazo -> diament -> netheryt) i siłą enczantów.
 */
public final class GearSetManager {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 10;

    private static final String[] ARMOR_PREFIX = {
            "LEATHER", "LEATHER", "CHAINMAIL", "IRON", "IRON",
            "DIAMOND", "DIAMOND", "DIAMOND", "NETHERITE", "NETHERITE"
    };
    private static final String[] TOOL_PREFIX = {
            "WOODEN", "STONE", "IRON", "IRON", "IRON",
            "DIAMOND", "DIAMOND", "DIAMOND", "NETHERITE", "NETHERITE"
    };
    /**
     * Osobna progresja materiału dla broni (miecz, siekiera, włócznia) - resource pack
     * "Fabulous Enchanted 3D" ma swiecacy model 3D dla kazdego z tych 7 materialow (w tym
     * miedz/zloto, ktorych nie ma w TOOL_PREFIX uzywanym dla kilofa/lopaty).
     */
    private static final String[] WEAPON_PREFIX = {
            "WOODEN", "STONE", "COPPER", "IRON", "IRON",
            "GOLDEN", "DIAMOND", "DIAMOND", "NETHERITE", "NETHERITE"
    };
    private static final int[] PROTECTION = {0, 1, 1, 2, 2, 3, 3, 4, 4, 4};
    private static final int[] SHARPNESS = {0, 1, 1, 2, 3, 3, 4, 4, 5, 5};
    private static final int[] EFFICIENCY = {0, 1, 2, 2, 3, 3, 4, 4, 5, 5};
    private static final int[] UNBREAKING = {0, 0, 1, 1, 2, 2, 3, 3, 3, 3};
    private static final int[] FORTUNE = {0, 0, 0, 0, 0, 0, 1, 2, 2, 3};
    private static final int[] LOOTING = {0, 0, 0, 0, 0, 0, 1, 2, 2, 3};
    private static final int MENDING_FROM_LEVEL = 6;

    private GearSetManager() {
    }

    public static boolean isValidLevel(int level) {
        return level >= MIN_LEVEL && level <= MAX_LEVEL;
    }

    public static void giveSet(Player player, int level) {
        int i = level - 1;
        String armorPrefix = ARMOR_PREFIX[i];
        String toolPrefix = TOOL_PREFIX[i];

        player.getInventory().setHelmet(armorPiece(armorPrefix, "HELMET", "Hełm", level, i));
        player.getInventory().setChestplate(armorPiece(armorPrefix, "CHESTPLATE", "Napierśnik", level, i));
        player.getInventory().setLeggings(armorPiece(armorPrefix, "LEGGINGS", "Spodnie", level, i));
        player.getInventory().setBoots(armorPiece(armorPrefix, "BOOTS", "Buty", level, i));

        player.getInventory().addItem(
                sword(WEAPON_PREFIX[i], level, i),
                spear(WEAPON_PREFIX[i], level, i),
                pickaxe(toolPrefix, level, i),
                axe(WEAPON_PREFIX[i], level, i),
                shovel(toolPrefix, level, i)
        );
    }

    /** /bpvp giveset <poziom> weapon - tylko bronie (miecz, siekiera, włócznia, bez zbroi/kilofa/łopaty). */
    public static void giveWeapon(Player player, int level) {
        int i = level - 1;
        player.getInventory().addItem(
                sword(WEAPON_PREFIX[i], level, i),
                spear(WEAPON_PREFIX[i], level, i),
                axe(WEAPON_PREFIX[i], level, i)
        );
    }

    private static ItemStack armorPiece(String prefix, String piece, String polishName, int level, int i) {
        ItemStack item = new ItemStack(Material.valueOf(prefix + "_" + piece));
        applyEnchant(item, Enchantment.PROTECTION, PROTECTION[i]);
        applyEnchant(item, Enchantment.UNBREAKING, UNBREAKING[i]);
        applyMending(item, level);
        style(item, polishName, level);
        return item;
    }

    /**
     * Materiał rośnie razem z poziomem (WEAPON_PREFIX) - resource pack "Fabulous Enchanted 3D" ma
     * swiecacy model 3D dla kazdego z 7 materialow, wiec wyglad miecza faktycznie zmienia się co
     * poziom. Ostrość rośnie NIEZALEŻNIE 1-10 (ponad wanilijny limit 5 - addUnsafeEnchantment na
     * to pozwala) jako dodatkowy wskaźnik mocy obok materiału.
     */
    private static ItemStack sword(String prefix, int level, int i) {
        ItemStack item = new ItemStack(Material.valueOf(prefix + "_SWORD"));
        applyEnchant(item, Enchantment.SHARPNESS, level);
        applyEnchant(item, Enchantment.UNBREAKING, UNBREAKING[i]);
        applyEnchant(item, Enchantment.LOOTING, LOOTING[i]);
        applyMending(item, level);
        style(item, "Miecz", level);
        return item;
    }

    private static ItemStack pickaxe(String prefix, int level, int i) {
        ItemStack item = new ItemStack(Material.valueOf(prefix + "_PICKAXE"));
        applyEnchant(item, Enchantment.EFFICIENCY, EFFICIENCY[i]);
        applyEnchant(item, Enchantment.UNBREAKING, UNBREAKING[i]);
        applyEnchant(item, Enchantment.FORTUNE, FORTUNE[i]);
        applyMending(item, level);
        style(item, "Kilof", level);
        return item;
    }

    private static ItemStack axe(String prefix, int level, int i) {
        ItemStack item = new ItemStack(Material.valueOf(prefix + "_AXE"));
        applyEnchant(item, Enchantment.SHARPNESS, SHARPNESS[i]);
        applyEnchant(item, Enchantment.EFFICIENCY, EFFICIENCY[i]);
        applyEnchant(item, Enchantment.UNBREAKING, UNBREAKING[i]);
        applyMending(item, level);
        style(item, "Siekiera", level);
        return item;
    }

    /** Włócznia - tak samo jak miecz ma swiecacy model 3D dla kazdego z 7 materialow w resource packu. */
    private static ItemStack spear(String prefix, int level, int i) {
        ItemStack item = new ItemStack(Material.valueOf(prefix + "_SPEAR"));
        applyEnchant(item, Enchantment.SHARPNESS, level);
        applyEnchant(item, Enchantment.UNBREAKING, UNBREAKING[i]);
        applyEnchant(item, Enchantment.LOOTING, LOOTING[i]);
        applyMending(item, level);
        style(item, "Włócznia", level);
        return item;
    }

    private static ItemStack shovel(String prefix, int level, int i) {
        ItemStack item = new ItemStack(Material.valueOf(prefix + "_SHOVEL"));
        applyEnchant(item, Enchantment.EFFICIENCY, EFFICIENCY[i]);
        applyEnchant(item, Enchantment.UNBREAKING, UNBREAKING[i]);
        applyMending(item, level);
        style(item, "Łopata", level);
        return item;
    }

    private static void applyEnchant(ItemStack item, Enchantment enchant, int level) {
        if (level > 0) {
            item.addUnsafeEnchantment(enchant, level);
        }
    }

    private static void applyMending(ItemStack item, int level) {
        if (level >= MENDING_FROM_LEVEL) {
            item.addUnsafeEnchantment(Enchantment.MENDING, 1);
        }
    }

    private static void style(ItemStack item, String polishName, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.setDisplayName(Branding.accent(polishName) + " §7[§dPoziom " + level + "§7]");
        meta.setLore(Collections.singletonList("§7Zestaw PvP - Poziom §d" + level));
        item.setItemMeta(meta);
    }
}
