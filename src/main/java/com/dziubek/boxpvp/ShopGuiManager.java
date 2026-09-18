package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShopGuiManager {

    private final BoxPvpPlugin plugin;

    public ShopGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void openMain(Player player) {
        List<String> categories = plugin.getShop().getCategories();
        int size = Math.min(54, Math.max(9, ((categories.size() - 1) / 9 + 1) * 9));

        Inventory inv = Bukkit.createInventory(new ShopMainGuiHolder(), size, Branding.accent("Sklep"));

        int slot = 0;
        for (String category : categories) {
            if (slot >= size) {
                break;
            }
            Material icon = plugin.getShop().getCategoryIcon(category);
            String name = plugin.getShop().getCategoryDisplayName(category);

            ItemStack item = new ItemStack(icon);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));

            List<String> lore = new ArrayList<>();
            lore.add("§7Kliknij, aby zobaczyć przedmioty");
            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(slot, item);
            slot++;
        }

        GuiDecor.fillEmpty(inv);
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    public void openCategory(Player player, String category) {
        Map<Integer, ShopManager.ShopItemData> items = plugin.getShop().getItems(category);
        int size = Math.min(54, Math.max(9, ((items.size() - 1) / 9 + 1) * 9));

        String rawName = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                plugin.getShop().getCategoryDisplayName(category)));
        if (rawName != null && rawName.length() > 24) {
            rawName = rawName.substring(0, 24);
        }
        String title = Branding.accent(rawName == null ? category : rawName);

        Inventory inv = Bukkit.createInventory(new ShopCategoryGuiHolder(category), size, title);

        int slot = 0;
        for (Map.Entry<Integer, ShopManager.ShopItemData> entry : items.entrySet()) {
            if (slot >= size) {
                break;
            }
            ShopManager.ShopItemData itemData = entry.getValue();

            ItemStack display = itemData.icon.clone();
            ItemMeta meta = display.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(typeBadge(itemData.type));
            lore.add("§7Cena: §a" + itemData.price);
            lore.add("§eKliknij, aby kupić");
            meta.setLore(lore);
            display.setItemMeta(meta);

            inv.setItem(slot, display);
            slot++;
        }

        GuiDecor.fillEmpty(inv);
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    private String typeBadge(ShopItemType type) {
        switch (type) {
            case KIT:
                return "§d[KIT]";
            case CURRENCY:
                return "§6[WALUTA]";
            case COMMAND:
            default:
                return "§7[KOMENDA]";
        }
    }
}
