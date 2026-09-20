package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bardzo prosty, TYLKO-flagujący "anty-cheat" - nikogo nie banuje ani nie kicka, jedynie loguje
 * podejrzane zachowania (zbyt duży zasięg ataku, "latanie" w survivalu bez wytłumaczenia) adminom
 * online i do konsoli, do ręcznej weryfikacji. Fałszywe alarmy są możliwe (lag, riptide trident,
 * knockback) - stąd tylko flagowanie, nie automatyczna kara.
 */
public class CheatWatchManager {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private static final double REACH_LIMIT = 4.5;
    private static final int REACH_VIOLATIONS_THRESHOLD = 3;
    private static final long REACH_WINDOW_MS = 10_000L;
    private static final long REACH_ALERT_COOLDOWN_MS = 30_000L;

    private static final long FLY_CHECK_TICKS = 20L;
    private static final int FLY_SECONDS_THRESHOLD = 5;
    private static final long FLY_ALERT_INTERVAL_MS = 10_000L;

    private final BoxPvpPlugin plugin;
    private final Map<UUID, List<Long>> reachHits = new HashMap<>();
    private final Map<UUID, Long> lastReachAlertAt = new HashMap<>();
    private final Map<UUID, Integer> airborneSeconds = new HashMap<>();
    private final Map<UUID, Long> lastFlyAlertAt = new HashMap<>();

    public CheatWatchManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickFlyCheck, FLY_CHECK_TICKS, FLY_CHECK_TICKS);
    }

    /** Wołane przy każdym trafieniu gracz-w-gracza (dystans między oczami napastnika a ofiary). */
    public void onPlayerHit(Player attacker, double distance) {
        if (distance <= REACH_LIMIT) {
            return;
        }
        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        List<Long> hits = reachHits.computeIfAbsent(uuid, k -> new ArrayList<>());
        hits.add(now);
        hits.removeIf(t -> now - t > REACH_WINDOW_MS);

        if (hits.size() < REACH_VIOLATIONS_THRESHOLD) {
            return;
        }
        Long lastAlert = lastReachAlertAt.get(uuid);
        if (lastAlert != null && now - lastAlert < REACH_ALERT_COOLDOWN_MS) {
            return;
        }
        lastReachAlertAt.put(uuid, now);
        alert("§4[AntyCheat] §f" + attacker.getName() + " §7- możliwy REACH §7(dystans: §c"
                + String.format("%.2f", distance) + "§7, limit: " + REACH_LIMIT + ")");
    }

    private void tickFlyCheck() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean excluded = player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                    || player.isFlying() || player.getAllowFlight() || player.isGliding()
                    || player.isInsideVehicle() || player.isSwimming()
                    || player.getLocation().getBlock().isLiquid() || player.getEyeLocation().getBlock().isLiquid()
                    || player.hasPotionEffect(PotionEffectType.LEVITATION)
                    || player.hasPotionEffect(PotionEffectType.SLOW_FALLING);

            if (excluded || player.isOnGround()) {
                airborneSeconds.put(uuid, 0);
                continue;
            }

            int seconds = airborneSeconds.merge(uuid, 1, Integer::sum);
            if (seconds < FLY_SECONDS_THRESHOLD) {
                continue;
            }
            long now = System.currentTimeMillis();
            Long lastAlert = lastFlyAlertAt.get(uuid);
            if (lastAlert != null && now - lastAlert < FLY_ALERT_INTERVAL_MS) {
                continue;
            }
            lastFlyAlertAt.put(uuid, now);
            alert("§4[AntyCheat] §f" + player.getName() + " §7- możliwy FLY §7(unosi się §c" + seconds + "s §7bez ziemi)");
        }
    }

    private void alert(String coloredMessage) {
        plugin.getLogger().warning(ChatColor.stripColor(coloredMessage));
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (admin.hasPermission(ADMIN_PERMISSION)) {
                admin.sendMessage(Branding.chatPrefix() + coloredMessage);
            }
        }
    }
}
