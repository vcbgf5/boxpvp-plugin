package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Obsługuje różdżkę generatora (zaznaczanie pos1/pos2 LPM/PPM).
 */
public class GeneratorListener implements Listener {

    private final BoxPvpPlugin plugin;

    public GeneratorListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || !plugin.getGenerators().isWand(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.getGenerators().setPos1(player, event.getClickedBlock().getLocation());
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.getGenerators().setPos2(player, event.getClickedBlock().getLocation());
        }
    }
}
