package com.dziubek.boxpvp;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/**
 * Encje Kantoru są nietrwałe, więc znikają przy wyładowaniu chunku - to odtwarza je z powrotem,
 * gdy chunk wraca (gracz podchodzi ponownie), zamiast czekać na restart serwera.
 */
public class BankChunkListener implements Listener {

    private final BoxPvpPlugin plugin;

    public BankChunkListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.getBanks().ensureSpawnedInChunk(event.getChunk());
    }
}
