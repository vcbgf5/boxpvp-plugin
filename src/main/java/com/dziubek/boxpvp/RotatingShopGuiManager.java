package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RotatingShopGuiManager {

    static final int[] OFFER_SLOTS = {11, 13, 15};
    private static final int TIMER_SLOT = 22;

    private final BoxPvpPlugin plugin;

    public RotatingShopGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new RotatingShopGuiHolder(), 27, Branding.accent("★ Oferta specjalna"));
        List<RotatingShopManager.RotationEntry> rotation = plugin.getRotatingShop().getCurrentRotation();

        for (int i = 0; i < OFFER_SLOTS.length && i < rotation.size(); i++) {
            RotatingShopManager.RotationEntry entry = rotation.get(i);
            ItemStack display = entry.icon.clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§7Cena bazowa: §f" + String.format("%.2f", entry.basePrice) + "$");
            lore.add("§c-" + entry.discountPercent + "%!");
            lore.add("§aCena: §f" + String.format("%.2f", entry.finalPrice()) + "$");
            lore.add("§eKliknij, by kupić!");
            meta.setLore(lore);
            display.setItemMeta(meta);
            inv.setItem(OFFER_SLOTS[i], display);
        }

        ItemStack timer = new ItemStack(Material.CLOCK);
        ItemMeta timerMeta = timer.getItemMeta();
        timerMeta.setDisplayName(Branding.accent("Następna rotacja"));
        timerMeta.setLore(List.of("§7Za: §f" + formatDuration(plugin.getRotatingShop().getMillisUntilNextRotation())));
        timer.setItemMeta(timerMeta);
        inv.setItem(TIMER_SLOT, timer);

        GuiDecor.fillEmpty(inv);
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    private static String formatDuration(long millis) {
        long totalMinutes = millis / 60_000L;
        return (totalMinutes / 60) + "h " + (totalMinutes % 60) + "min";
    }
}
