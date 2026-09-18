package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Cyklicznie odtwarza subtelny efekt idle przy każdej fizycznej skrzyni, która ma go ustawionego
 * (/crate setidleeffect) - widoczny dopóki nikt jej nie otwiera.
 */
public class CrateIdleEffectTask extends BukkitRunnable {

    private final BoxPvpPlugin plugin;

    public CrateIdleEffectTask(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (String name : plugin.getCrates().names()) {
            CrateEffect idle = plugin.getCrates().getIdleEffect(name);
            if (idle == null) {
                continue;
            }
            for (Location location : plugin.getCrates().getAllLocations(name)) {
                idle.playIdle(location);
            }
        }
    }
}
