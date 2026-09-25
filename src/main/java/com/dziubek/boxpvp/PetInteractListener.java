package com.dziubek.boxpvp;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

/** Odtwarza aktywnego peta gracza po wejściu na serwer, chowa go po wyjściu. */
public class PetInteractListener implements Listener {

    private final BoxPvpPlugin plugin;
    private final NamespacedKey petAnchorTag;
    private final NamespacedKey crateAnchorTag;

    public PetInteractListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        this.petAnchorTag = new NamespacedKey(plugin, PetDisplayManager.ANCHOR_TAG_KEY);
        this.crateAnchorTag = new NamespacedKey(plugin, CrateModelDisplayManager.ANCHOR_TAG_KEY);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPetDisplays().despawn(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPetManager().restoreActive(event.getPlayer());
    }

    /**
     * Nasze znaczniki (ArmorStand) - peta ORAZ modelu 3D skrzyni - MUSZĄ się zawsze zespawnować,
     * nawet w strefie z zablokowanym spawnowaniem mobów (np. flaga WorldGuard mob-spawning/DENY
     * na spawnie/lobby) - inne pluginy/flagi mogą anulować CreatureSpawnEvent myśląc, że to
     * naturalny mob. Odpalane na MONITOR (po wszystkich innych pluginach), żeby na pewno
     * nadpisać ich decyzję.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnchorSpawn(CreatureSpawnEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        var pdc = event.getEntity().getPersistentDataContainer();
        if (pdc.has(petAnchorTag, PersistentDataType.BYTE) || pdc.has(crateAnchorTag, PersistentDataType.BYTE)) {
            event.setCancelled(false);
        }
    }
}
