package com.dziubek.boxpvp;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class CrateGuiListener implements Listener {

    private final BoxPvpPlugin plugin;

    public CrateGuiListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof CrateRollGuiHolder) {
            // GUI animacji losowania - gracz nic stąd nie może wyjąć, nagrodę i tak dostaje na koniec
            event.setCancelled(true);
            return;
        }

        if (event.getInventory().getHolder() instanceof CrateOpenChoiceGuiHolder) {
            handleOpenChoiceClick(event);
            return;
        }

        if (event.getInventory().getHolder() instanceof CrateConfigGuiHolder) {
            handleConfigClick(event);
            return;
        }

        if (event.getInventory().getHolder() instanceof CratePreviewGuiHolder) {
            // czysto informacyjne GUI - nic nie da się stąd zabrać
            event.setCancelled(true);
        }
    }

    private void handleOpenChoiceClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot != CrateOpenChoiceGuiManager.ANIMATED_SLOT && slot != CrateOpenChoiceGuiManager.INSTANT_SLOT) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        CrateOpenChoiceGuiHolder holder = (CrateOpenChoiceGuiHolder) event.getInventory().getHolder();

        if (slot == CrateOpenChoiceGuiManager.ANIMATED_SLOT) {
            CrateRollAnimation.play(plugin, player, holder.getCrateName(), holder.getRewards(), holder.getCrateBlockLocation());
        } else {
            player.closeInventory();
            CrateRollAnimation.playInstant(plugin, player, holder.getCrateName(), holder.getRewards(), holder.getCrateBlockLocation());
        }
    }

    private void handleConfigClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.RIGHT || !(event.getWhoClicked() instanceof Player)) {
            return; // lewy klik/przeciąganie zostaje bez zmian - normalne stawianie przedmiotów
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getInventory().getSize()) {
            return; // klik poza konfigurowaną skrzynią (np. we własnym ekwipunku admina)
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }

        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        CrateConfigGuiHolder holder = (CrateConfigGuiHolder) event.getInventory().getHolder();

        CrateRewardSession session = plugin.getCrateRewardSessions().getSession(player.getUniqueId());
        if (session == null) {
            session = new CrateRewardSession(holder.getCrateName(), event.getInventory());
            plugin.getCrateRewardSessions().startSession(player.getUniqueId(), session);
        }

        session.awaitingChatForSlot = event.getRawSlot();
        session.suppressNextClose = true;
        player.closeInventory();
        player.sendMessage("§eWpisz na czacie procent szansy dla tego przedmiotu (np. 12.5), §f'auto'§e by wrócić do automatu, albo §f'anuluj'§e:");
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CrateConfigGuiHolder)) {
            return;
        }
        CrateConfigGuiHolder holder = (CrateConfigGuiHolder) event.getInventory().getHolder();
        HumanEntity human = event.getPlayer();

        CrateRewardSession session = plugin.getCrateRewardSessions().getSession(human.getUniqueId());
        if (session != null && session.suppressNextClose) {
            return; // to zamknięcie wywołaliśmy sami (prompt na czacie) - nie zapisuj jeszcze
        }

        plugin.getCrates().saveRewards(holder.getCrateName(), event.getInventory().getContents());
        plugin.getCrateRewardSessions().clearSession(human.getUniqueId());

        human.sendMessage("§aZapisano konfigurację skrzyni '" + holder.getCrateName() + "'.");
    }
}
