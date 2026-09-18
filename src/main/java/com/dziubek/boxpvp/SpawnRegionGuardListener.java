package com.dziubek.boxpvp;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class SpawnRegionGuardListener implements Listener {

    private final BoxPvpPlugin plugin;

    public SpawnRegionGuardListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        check(event.getPlayer(), event.getTo(), () -> event.setCancelled(true));
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        // pozwalamy na teleporty spowodowane przez inne pluginy (np. /spawn) tylko gdy gracz NIE jest w walce -
        // check() i tak to obsłuży, ale blokujemy też np. teleport-pluginy/enderpearl w trakcie walki
        check(event.getPlayer(), event.getTo(), () -> event.setCancelled(true));
    }

    private void check(Player player, Location to, Runnable cancelAction) {
        if (to == null) {
            return;
        }
        if (!plugin.getCombatManager().isTagged(player.getUniqueId())) {
            return;
        }

        RegionManager regions = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(to.getWorld()));
        if (regions == null) {
            return;
        }

        ProtectedRegion region = regions.getRegion(plugin.getProtectedRegionName());
        if (region == null) {
            return;
        }

        BlockVector3 pos = BlockVector3.at(to.getX(), to.getY(), to.getZ());
        if (region.contains(pos)) {
            cancelAction.run();
            player.sendMessage(plugin.msg("region-blocked", "&cNie możesz wejść na spawn podczas walki!"));
        }
    }
}
