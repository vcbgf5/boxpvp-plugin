package com.dziubek.boxpvp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public class EnvoyListener implements Listener {

    private final BoxPvpPlugin plugin;

    public EnvoyListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // ignorujemy duplikat zdarzenia dla off-hand
        }
        if (plugin.getEnvoy().tryOpen(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
