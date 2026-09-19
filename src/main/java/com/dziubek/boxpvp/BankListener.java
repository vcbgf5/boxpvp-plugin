package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/**
 * PPM na Kantorze zawsze otwiera GUI wymiany (bez warunku shift - w przeciwieństwie do
 * TraderListener, bo nie ma tu osobnego GUI edycji admina). Kliknięcia w GUI: LPM = kup 1,
 * Shift+LPM = kup tyle ile stać (do 64), PPM = sprzedaj wszystkie posiadane sztuki danego
 * materiału. Cała matematyka idzie przez Vault (plugin.getEconomy()), bez wanilijnego handlu.
 */
public class BankListener implements Listener {

    private final BoxPvpPlugin plugin;

    public BankListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // ignorujemy duplikat zdarzenia dla off-hand
        }
        BankManager.BankData bd = plugin.getBanks().getByEntity(event.getRightClicked());
        if (bd == null) {
            return;
        }
        event.setCancelled(true);
        plugin.getBankGui().open(event.getPlayer());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BankGuiHolder)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) {
            return; // klik we własnym ekwipunku gracza - normalne
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        List<Material> ordered = plugin.getCurrency().orderedMaterials();
        int index = slot - BankGuiManager.FIRST_SLOT;
        if (index < 0 || index >= ordered.size() || index >= BankGuiManager.MAX_DENOMINATIONS) {
            return;
        }
        Material material = ordered.get(index);
        double price = plugin.getCurrency().getPrice(material);

        if (event.isRightClick()) {
            sell(player, material, price);
        } else {
            buy(player, material, price, event.isShiftClick());
        }
    }

    private void buy(Player player, Material material, double price, boolean shift) {
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cKantor jest niedostępny - brak podłączonego systemu ekonomii.");
            return;
        }
        double balance = plugin.getEconomy().getBalance(player);
        int amount = shift ? Math.min(64, (int) (balance / price)) : 1;
        double cost = amount * price;
        if (amount <= 0 || balance < cost) {
            player.sendMessage("§cNie masz wystarczająco środków! Potrzebujesz §f" + BankGuiManager.formatMoney(price)
                    + "§c, masz §f" + BankGuiManager.formatMoney(balance) + "§c.");
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, cost);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, amount));
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        player.sendMessage("§aKupiono §f" + amount + "x " + BankGuiManager.formatName(material)
                + " §aza §f" + BankGuiManager.formatMoney(cost) + " §amonet.");
    }

    private void sell(Player player, Material material, double price) {
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cKantor jest niedostępny - brak podłączonego systemu ekonomii.");
            return;
        }
        ItemStack[] contents = player.getInventory().getContents();
        int count = 0;
        for (ItemStack item : contents) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        if (count <= 0) {
            player.sendMessage("§cNie masz żadnego " + BankGuiManager.formatName(material) + " do sprzedania.");
            return;
        }
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material) {
                player.getInventory().setItem(i, null);
            }
        }

        double payout = count * price;
        plugin.getEconomy().depositPlayer(player, payout);
        player.sendMessage("§aSprzedano §f" + count + "x " + BankGuiManager.formatName(material)
                + " §aza §f" + BankGuiManager.formatMoney(payout) + " §amonet.");
    }
}
