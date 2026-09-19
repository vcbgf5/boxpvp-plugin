package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI Rynku - do 45 ofert na stronę (rzędy 1-5), rząd 6 na nawigację stron. Klik LPM na ofercie
 * kupuje ją (MarketManager.buy); obsługa kliknięć w MarketListener.
 */
public class MarketGuiManager {

    private static final int PAGE_SIZE = 45;
    public static final int PREV_SLOT = 48;
    public static final int NEXT_SLOT = 50;

    private final BoxPvpPlugin plugin;

    public MarketGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int page) {
        List<MarketManager.Listing> all = plugin.getMarket().activeListings();
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) PAGE_SIZE));
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));

        Inventory inv = Bukkit.createInventory(new MarketGuiHolder(clampedPage, idsOnPage(all, clampedPage)), 54,
                Branding.accent("Rynek") + " §7(" + (clampedPage + 1) + "/" + totalPages + ")");

        int start = clampedPage * PAGE_SIZE;
        int end = Math.min(all.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, buildDisplayItem(all.get(i)));
        }

        if (clampedPage > 0) {
            inv.setItem(PREV_SLOT, navButton("§e« Poprzednia strona"));
        }
        if (clampedPage < totalPages - 1) {
            inv.setItem(NEXT_SLOT, navButton("§eNastępna strona »"));
        }

        GuiDecor.fillEmpty(inv);
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    private List<String> idsOnPage(List<MarketManager.Listing> all, int page) {
        int start = page * PAGE_SIZE;
        int end = Math.min(all.size(), start + PAGE_SIZE);
        List<String> ids = new ArrayList<>();
        for (int i = start; i < end; i++) {
            ids.add(all.get(i).id);
        }
        return ids;
    }

    private ItemStack buildDisplayItem(MarketManager.Listing listing) {
        ItemStack display = listing.item.clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(" ");
            lore.add("§7Sprzedawca: §f" + listing.sellerName);
            lore.add("§7Cena: §a" + BankGuiManager.formatMoney(listing.price) + " monet");
            lore.add("§7Pozostało: §f" + formatTimeLeft(listing.expiresAt));
            lore.add(" ");
            lore.add("§aLPM §7- kup");
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private ItemStack navButton(String name) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatTimeLeft(long expiresAt) {
        long millis = Math.max(0, expiresAt - System.currentTimeMillis());
        long hours = millis / (1000 * 60 * 60);
        if (hours >= 24) {
            return (hours / 24) + "d " + (hours % 24) + "h";
        }
        long minutes = (millis / (1000 * 60)) % 60;
        return hours + "h " + minutes + "min";
    }
}
