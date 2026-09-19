package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * PPM na Kantorze albo realizuje trzymany czek (BankManager.redeemCheck), albo otwiera GUI
 * wymiany. Kliknięcia w GUI: LPM = kup 1, Shift+LPM = kup tyle ile stać (do 64), środkowy klik =
 * kup dowolną ilość wpisaną na czacie, PPM = sprzedaj wszystkie posiadane sztuki, Shift+PPM =
 * sprzedaj dowolną ilość wpisaną na czacie. Cała matematyka/transakcje żyją w BankManager, żeby
 * dzielić je z BankChatListener (wpisana na czacie ilość/kwota).
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
        Player player = event.getPlayer();

        ItemStack held = player.getInventory().getItemInMainHand();
        Double checkAmount = plugin.getBanks().getCheckAmount(held);
        if (checkAmount != null) {
            plugin.getBanks().redeemCheck(player, held, checkAmount);
            return;
        }
        plugin.getBankGui().open(player);
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

        if (slot == BankGuiManager.CHECK_SLOT) {
            plugin.getBanks().awaitCheckAmount(player);
            return;
        }

        List<Material> ordered = plugin.getCurrency().orderedMaterials();
        int index = slot - BankGuiManager.FIRST_SLOT;
        if (index < 0 || index >= ordered.size() || index >= BankGuiManager.MAX_DENOMINATIONS) {
            return;
        }
        Material material = ordered.get(index);

        if (event.getClick() == ClickType.MIDDLE) {
            plugin.getBanks().awaitBuyAmount(player, material);
        } else if (event.isRightClick() && event.isShiftClick()) {
            plugin.getBanks().awaitSellAmount(player, material);
        } else if (event.isRightClick()) {
            plugin.getBanks().sell(player, material, plugin.getBanks().countOwned(player, material));
        } else if (event.isShiftClick()) {
            buyMax(player, material);
        } else {
            plugin.getBanks().buy(player, material, 1);
        }
    }

    private void buyMax(Player player, Material material) {
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cKantor jest niedostępny - brak podłączonego systemu ekonomii.");
            return;
        }
        double price = plugin.getCurrency().getPrice(material);
        double balance = plugin.getEconomy().getBalance(player);
        int amount = Math.min(64, (int) (balance / price));
        if (amount <= 0) {
            player.sendMessage("§cNie stać Cię na ani jedną sztukę (" + BankGuiManager.formatMoney(price) + " monet).");
            return;
        }
        plugin.getBanks().buy(player, material, amount);
    }
}
