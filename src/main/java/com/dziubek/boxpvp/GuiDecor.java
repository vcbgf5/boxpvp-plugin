package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Wspólna dekoracja GUI - jeden spójny styl we wszystkich menu pluginu zamiast surowych,
 * pustych slotów. fillEmpty() domalowuje szklany panel WYŁĄCZNIE tam, gdzie slot jest pusty,
 * więc jest bezpieczne do wywołania na końcu budowania dowolnego inventory bez ryzyka
 * nadpisania już umieszczonej zawartości.
 */
public class GuiDecor {

    private static final Material PANE_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;

    private GuiDecor() {
    }

    public static void fillEmpty(Inventory inv) {
        ItemStack pane = pane();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, pane);
            }
        }
    }

    /**
     * Krótki dźwięk otwarcia menu - spójny dla wszystkich "skrzyniopodobnych" GUI.
     */
    public static void playOpenSound(Player player) {
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.0f);
    }

    private static ItemStack pane() {
        ItemStack pane = new ItemStack(PANE_MATERIAL);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
