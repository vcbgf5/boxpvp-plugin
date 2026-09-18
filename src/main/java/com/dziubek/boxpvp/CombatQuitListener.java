package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class CombatQuitListener implements Listener {

    private final BoxPvpPlugin plugin;

    public CombatQuitListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getCombatManager().isTagged(player.getUniqueId())) {
            return;
        }

        Location location = player.getLocation();
        PlayerInventory inv = player.getInventory();

        // upuszczamy cały ekwipunek (przedmioty + zbroja + offhand), tak jakby gracz zginął
        for (ItemStack item : inv.getContents()) {
            dropIfPresent(location, item);
        }
        for (ItemStack item : inv.getArmorContents()) {
            dropIfPresent(location, item);
        }
        dropIfPresent(location, inv.getItemInOffHand());

        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(null);

        player.setHealth(0.0);

        plugin.getCombatManager().clear(player.getUniqueId());

        String message = plugin.msg("quit-punished", "&c{player} wyszedł podczas walki i stracił przedmioty!")
                .replace("{player}", player.getName());
        plugin.getServer().broadcastMessage(Branding.chatPrefix() + message);
    }

    private void dropIfPresent(Location location, ItemStack item) {
        if (item != null && item.getType() != org.bukkit.Material.AIR) {
            location.getWorld().dropItemNaturally(location, item);
        }
    }
}
