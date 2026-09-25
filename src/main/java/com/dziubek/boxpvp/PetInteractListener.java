package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;

/** PPM na własnym aktywnym pecie -> krótka animacja "pet" (przytulenie/reakcja). */
public class PetInteractListener implements Listener {

    private final BoxPvpPlugin plugin;

    public PetInteractListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        UUID ownerId = plugin.getPetDisplays().ownerOfPetEntity(event.getRightClicked().getUniqueId());
        if (ownerId == null || !ownerId.equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        plugin.getPetDisplays().playInteractAnimation(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPetDisplays().despawn(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPetManager().restoreActive(event.getPlayer());
    }
}
