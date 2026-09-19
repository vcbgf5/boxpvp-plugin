package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Jeśli wynik craftingu jest na liście zablokowanych materiałów (CraftBlockManager), podmienia
 * podgląd wyniku na kartkę z informacją - gracz nie dostanie prawdziwego przedmiotu, nawet jeśli
 * kliknie w wynik.
 */
public class CraftBlockListener implements Listener {

    private final BoxPvpPlugin plugin;

    public CraftBlockListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() == Material.AIR) {
            return;
        }
        if (!plugin.getCraftBlocks().isBlocked(result.getType())) {
            return;
        }
        event.getInventory().setResult(deniedItem());
    }

    private ItemStack deniedItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§cNie możesz skraftować tego bloku");
            item.setItemMeta(meta);
        }
        return item;
    }
}
