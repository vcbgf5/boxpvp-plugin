package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public class DuelListener implements Listener {

    private final BoxPvpPlugin plugin;

    public DuelListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!plugin.getDuels().hasActiveDuel(victim.getUniqueId())) {
            return;
        }
        UUID opponent = plugin.getDuels().getOpponent(victim.getUniqueId());
        event.getDrops().clear();
        event.setDroppedExp(0);
        plugin.getDuels().finish(opponent, victim.getUniqueId(), true);

        // Pomijamy zwykły ekran "Zginąłeś" z przyciskiem - przegrany od razu (bez czekania na
        // klik) staje się duchem widzem, patrz onRespawn.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (victim.isOnline() && victim.isDead()) {
                victim.spigot().respawn();
            }
        });
    }

    /**
     * Ekwipunek/HP/lokację przegranego przywracamy dopiero TU (nie w PlayerDeathEvent) - nie da
     * się bezpiecznie teleportować/nadpisywać ekwipunku jeszcze martwej postaci. Zamiast od razu
     * przywracać, gracz najpierw staje się duchem (spectator) na 10s - patrz startGhostPhase.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        DuelManager.PendingRestore pending = plugin.getDuels().consumePendingRestore(player.getUniqueId());
        if (pending == null) {
            return;
        }
        if (pending.getGhostLocation() != null) {
            event.setRespawnLocation(pending.getGhostLocation());
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getDuels().startGhostPhase(player, pending));
    }

    /**
     * Podczas wejścia (spadanie z góry) i odliczania przed startem gracz jest "zamrożony" -
     * nie może się ruszyć, ale może się rozglądać (yaw/pitch przechodzą, pozycja nie).
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDuels().isFrozen(player.getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }
        event.setTo(new Location(to.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
    }

    /** Areny pojedynków są jednorazowymi kopiami - nikt nie powinien móc trwale ich zniszczyć. */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getDuels().isDuelWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** Głód na arenie jest zablokowany na stałe na połowie (patrz DuelManager#fullyHeal) - nikt nie musi jeść. */
    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getEntity();
        if (plugin.getDuels().isDuelWorld(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    /**
     * Siatka bezpieczeństwa - jeśli gracz dołącza do gry, a jego zapisana lokacja wskazuje na
     * arenę pojedynku (np. rozłączył się w trakcie duela, a świat zdążył się już skasować),
     * ląduje na normalnym spawnie zamiast w nieistniejącym/martwym świecie.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getDuels().isDuelWorld(player.getWorld()) || !plugin.hasSurvivalSpawn()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                player.teleport(plugin.getSurvivalSpawn());
                player.sendMessage("§eWróciłeś do gry w trakcie pojedynku - przeniesiono Cię na spawn.");
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getDuels().hasActiveDuel(player.getUniqueId())) {
            plugin.getDuels().forfeit(player);
            return;
        }
        // Jeśli akurat czeka na powrót (duch po przegranej ALBO świętowanie po wygranej,
        // jeszcze nie minęło 10s) - przywróć od razu, żeby jego dane zapisały się z prawdziwym
        // ekwipunkiem, a nie stanem sprzed przywrócenia.
        if (plugin.getDuels().returnFromCelebration(player)) {
            return;
        }
        plugin.getDuels().returnFromGhost(player);
    }
}
