package com.dziubek.boxpvp;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class ShopGuiListener implements Listener {

    private final BoxPvpPlugin plugin;

    public ShopGuiListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof ShopMainGuiHolder) {
            handleMainClick(event);
            return;
        }
        if (event.getInventory().getHolder() instanceof ShopCategoryGuiHolder) {
            handleCategoryClick(event);
        }
    }

    private void handleMainClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getCurrentItem() == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        List<String> categories = plugin.getShop().getCategories();
        int slot = event.getRawSlot();

        if (slot < 0 || slot >= categories.size()) {
            return;
        }
        plugin.getShopGui().openCategory(player, categories.get(slot));
    }

    private void handleCategoryClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player) || event.getCurrentItem() == null) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ShopCategoryGuiHolder holder = (ShopCategoryGuiHolder) event.getInventory().getHolder();

        Map<Integer, ShopManager.ShopItemData> items = plugin.getShop().getItems(holder.getCategoryId());
        int slot = event.getRawSlot();

        int i = 0;
        ShopManager.ShopItemData clicked = null;
        for (Map.Entry<Integer, ShopManager.ShopItemData> entry : items.entrySet()) {
            if (i == slot) {
                clicked = entry.getValue();
                break;
            }
            i++;
        }
        if (clicked == null) {
            return;
        }

        purchase(player, clicked);
    }

    private void purchase(Player player, ShopManager.ShopItemData item) {
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cSklep jest niedostępny - brak podłączonego systemu ekonomii (Vault + EssentialsX).");
            return;
        }

        if (item.type == ShopItemType.KIT && plugin.getKits().getItems(item.kitName).isEmpty()) {
            player.sendMessage("§cTen kit nie ma jeszcze skonfigurowanej zawartości - zgłoś to administracji.");
            return;
        }

        double balance = plugin.getEconomy().getBalance(player);
        if (balance < item.price) {
            player.sendMessage("§cNie masz wystarczająco środków! Potrzebujesz §f" + item.price + "§c, masz §f" + String.format("%.2f", balance) + "§c.");
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, item.price);
        plugin.getStats().recordMoneySpent(player.getUniqueId(), player.getName(), item.price);
        playPurchaseEffect(player);

        if (item.type == ShopItemType.KIT) {
            List<ItemStack> kitItems = plugin.getKits().getItems(item.kitName);
            for (ItemStack kitItem : kitItems) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(kitItem.clone());
                for (ItemStack extra : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), extra);
                }
            }
            if (!kitItems.isEmpty()) {
                RewardRevealEffect.playLight(plugin, player, kitItems.get(0));
            }
            player.sendMessage("§aKupiono! Otrzymujesz kit '" + item.kitName + "'.");
        } else {
            for (String cmd : item.commands) {
                String parsed = cmd.replace("%player%", player.getName());
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), parsed);
            }
            player.sendMessage("§aZakupiono! Pobrano §f" + item.price + "§a z konta.");
        }

        player.closeInventory();
    }

    /** "Fontanna" monet nad graczem przy każdym udanym zakupie w /sklep. */
    private void playPurchaseEffect(Player player) {
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.5, 0), 20, 0.4, 0.5, 0.4, 0);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.4f);
    }
}
