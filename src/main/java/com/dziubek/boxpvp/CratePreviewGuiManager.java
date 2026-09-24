package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only podgląd puli nagród skrzyni wraz z dokładnymi procentami szans - nie modyfikuje
 * przechowywanych danych, tylko wyświetla kopie przedmiotów z dopisaną szansą w lore.
 */
public class CratePreviewGuiManager {

    private final BoxPvpPlugin plugin;

    public CratePreviewGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String crateName) {
        List<CrateReward> rewards = plugin.getCrates().getRewards(crateName);

        int size = Math.min(54, Math.max(9, ((rewards.size() + 8) / 9) * 9));
        boolean srebna = size == 9 && "Srebna".equalsIgnoreCase(crateName);
        String title = srebna ? Branding.SREBNA_PREVIEW_TITLE : Branding.accent("Podgląd:") + " §f" + crateName;
        Inventory inv = Bukkit.createInventory(new CratePreviewGuiHolder(crateName), size, title);

        int slot = 0;
        for (CrateReward reward : rewards) {
            if (slot >= size) {
                break;
            }
            inv.setItem(slot, buildDisplayItem(reward));
            slot++;
        }

        if (!srebna) {
            GuiDecor.fillEmpty(inv);
        }
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    private ItemStack buildDisplayItem(CrateReward reward) {
        ItemStack display = reward.item().clone();
        ItemMeta meta = display.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("§7Szansa: §f" + trimPercent(reward.chance()) + "%");
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        return display;
    }

    private static String trimPercent(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }
}
