package com.dziubek.boxpvp;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;

import java.util.List;

/**
 * Miękka integracja z DecentHolograms - jeśli plugin nie jest zainstalowany,
 * wszystkie metody po prostu nic nie robią (isAvailable() == false).
 */
public class DecentHologramsHook {

    private final BoxPvpPlugin plugin;
    private final boolean available;

    public DecentHologramsHook(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        this.available = plugin.getServer().getPluginManager().getPlugin("DecentHolograms") != null;
        if (available) {
            plugin.getLogger().info("Wykryto DecentHolograms - integracja aktywna (/infoholo).");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void createInfoHologram(String id, Location location, List<String> lines) {
        if (!available) {
            return;
        }
        Hologram existing = DHAPI.getHologram(id);
        if (existing != null) {
            existing.delete();
        }
        // "true" = hologram TRWAŁY - zapisuje się do pliku i przeżywa restart serwera.
        // Bez tego flaga DecentHolograms tworzy hologram tylko na czas działania pluginu!
        DHAPI.createHologram(id, location, true, lines);
    }

    public void removeHologram(String id) {
        if (!available) {
            return;
        }
        Hologram existing = DHAPI.getHologram(id);
        if (existing != null) {
            existing.delete();
        }
    }
}
