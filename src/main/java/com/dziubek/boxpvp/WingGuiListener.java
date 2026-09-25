package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Set;

public class WingGuiListener implements Listener {

    private final BoxPvpPlugin plugin;

    public WingGuiListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof WingGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 18) {
            return;
        }

        if (slot == WingGuiManager.TAKE_OFF_SLOT) {
            plugin.getWingManager().setActive(player, null);
            player.sendMessage(Branding.chatPrefix() + "§7Zdjąłeś skrzydła.");
            player.closeInventory();
            return;
        }

        List<String> species = WingSpecies.ORDER;
        if (slot >= species.size()) {
            return;
        }
        String chosen = species.get(slot);
        Set<String> owned = plugin.getWingManager().getOwned(player.getUniqueId());
        if (!owned.contains(chosen)) {
            return;
        }

        plugin.getWingManager().setActive(player, chosen);
        WingSpecies.Info info = WingSpecies.of(chosen);
        player.sendMessage(Branding.chatPrefix() + "§aZałożone skrzydła: " + (info != null ? info.displayName() : chosen) + " §fSkrzydła");
        player.closeInventory();
    }
}
