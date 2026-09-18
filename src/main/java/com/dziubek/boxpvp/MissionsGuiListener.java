package com.dziubek.boxpvp;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class MissionsGuiListener implements Listener {

    private final BoxPvpPlugin plugin;

    public MissionsGuiListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MissionsGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        MissionsGuiHolder holder = (MissionsGuiHolder) event.getInventory().getHolder();
        MissionManager.Definition def = holder.definitionAt(event.getRawSlot());
        if (def == null) {
            return;
        }

        boolean claimed = plugin.getMissions().claim(player, def);
        if (claimed) {
            player.sendMessage("§aOdebrano nagrodę za misję: §f" + def.displayName() + "§a!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.4f);
            plugin.getMissionsGui().open(player);
        }
    }
}
