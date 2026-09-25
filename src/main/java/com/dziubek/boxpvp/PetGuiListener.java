package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Set;

public class PetGuiListener implements Listener {

    private final BoxPvpPlugin plugin;

    public PetGuiListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof PetGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 9) {
            return;
        }

        if (slot == PetGuiManager.PUT_AWAY_SLOT) {
            plugin.getPetManager().setActive(player, null);
            player.sendMessage(Branding.chatPrefix() + "§7Schowałeś swojego peta.");
            player.closeInventory();
            return;
        }

        List<String> species = PetModelRegistry.SPECIES;
        if (slot >= species.size()) {
            return;
        }
        String chosen = species.get(slot);
        Set<String> owned = plugin.getPetManager().getOwned(player.getUniqueId());
        if (!owned.contains(chosen)) {
            return;
        }

        plugin.getPetManager().setActive(player, chosen);
        PetSpecies.Info info = PetSpecies.of(chosen);
        player.sendMessage(Branding.chatPrefix() + "§aAktywny pet: " + (info != null ? info.coloredName() : chosen));
        player.closeInventory();
    }
}
