package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

public class CrateRewardChatListener implements Listener {

    private final BoxPvpPlugin plugin;

    public CrateRewardChatListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        CrateRewardSession session = plugin.getCrateRewardSessions().getSession(player.getUniqueId());
        if (session == null || session.awaitingChatForSlot == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> handleInput(player, session, message));
    }

    private void handleInput(Player player, CrateRewardSession session, String message) {
        int slot = session.awaitingChatForSlot;

        if (message.equalsIgnoreCase("anuluj")) {
            session.awaitingChatForSlot = null;
            player.sendMessage("§7Anulowano wpisywanie, wracasz do konfiguracji skrzyni.");
            reopen(player, session);
            return;
        }

        if (message.equalsIgnoreCase("auto") || message.equalsIgnoreCase("reset")) {
            ItemStack current = session.inventory.getItem(slot);
            if (current != null) {
                session.inventory.setItem(slot, plugin.getCrates().clearChanceTag(current));
            }
            session.awaitingChatForSlot = null;
            player.sendMessage("§aTen przedmiot wróci do automatycznego wypełniania szansy.");
            reopen(player, session);
            return;
        }

        double chance;
        try {
            chance = Double.parseDouble(message.replace(",", ".").replace("%", "").trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§cTo nie jest liczba. Wpisz procent (np. 12.5), 'auto' albo 'anuluj':");
            return;
        }

        if (chance < 0 || chance > 100) {
            player.sendMessage("§cSzansa musi być pomiędzy 0 a 100. Spróbuj ponownie:");
            return;
        }

        ItemStack current = session.inventory.getItem(slot);
        if (current == null || current.getType().isAir()) {
            player.sendMessage("§cTen slot jest już pusty - anulowano.");
            session.awaitingChatForSlot = null;
            reopen(player, session);
            return;
        }

        session.inventory.setItem(slot, plugin.getCrates().applyChanceTag(current, chance));
        session.awaitingChatForSlot = null;
        player.sendMessage("§aUstawiono szansę §f" + chance + "%§a dla tego przedmiotu.");
        reopen(player, session);
    }

    private void reopen(Player player, CrateRewardSession session) {
        session.suppressNextClose = false;
        player.openInventory(session.inventory);
    }
}
