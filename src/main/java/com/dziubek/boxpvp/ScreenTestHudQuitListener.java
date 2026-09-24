package com.dziubek.boxpvp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class ScreenTestHudQuitListener implements Listener {

    private final BoxPvpPlugin plugin;

    public ScreenTestHudQuitListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getScreenTestHud().hide(event.getPlayer());
    }
}
