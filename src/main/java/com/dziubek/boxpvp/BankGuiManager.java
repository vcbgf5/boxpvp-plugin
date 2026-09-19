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
 * GUI Kantoru - jeden slot na każdy skonfigurowany nominał (CurrencyManager), posortowane
 * rosnąco po wartości. LPM/Shift+LPM = kupno (monety -> przedmiot), PPM = sprzedaż całego
 * posiadanego stosu danego materiału (przedmiot -> monety). Obsługa kliknięć w BankListener.
 */
public class BankGuiManager {

    public static final int FIRST_SLOT = 10;
    public static final int MAX_DENOMINATIONS = 7;

    private final BoxPvpPlugin plugin;

    public BankGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new BankGuiHolder(), 27, Branding.accent("Kantor"));

        List<Material> ordered = plugin.getCurrency().orderedMaterials();
        int slot = FIRST_SLOT;
        for (Material material : ordered) {
            if (slot >= FIRST_SLOT + MAX_DENOMINATIONS) {
                break;
            }
            inv.setItem(slot, buildDisplayItem(material, plugin.getCurrency().getPrice(material)));
            slot++;
        }

        GuiDecor.fillEmpty(inv);
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    private ItemStack buildDisplayItem(Material material, double price) {
        ItemStack display = new ItemStack(material);
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§l" + formatName(material) + " §7- §f" + formatMoney(price) + " monet");
            List<String> lore = new ArrayList<>();
            lore.add("§7Wartość: §f" + formatMoney(price) + " monet za sztukę");
            lore.add(" ");
            lore.add("§aLPM §7- kup 1 sztukę");
            lore.add("§aShift+LPM §7- kup tyle, ile stać Cię (do 64)");
            lore.add("§cPPM §7- sprzedaj WSZYSTKIE swoje " + formatName(material) + " z ekwipunku");
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    static String formatName(Material material) {
        String[] parts = material.name().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.charAt(0)).append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    static String formatMoney(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }
}
