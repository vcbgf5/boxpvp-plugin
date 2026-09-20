package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Proste narzędzia moderacyjne - vanish (niewidzialność dla zwykłych graczy), freeze (zero ruchu,
 * do "złapania na gorącym uczynku") i cooldown dla ogólnych zgłoszeń /report (w odróżnieniu od
 * /reportduel, które dotyczy wyłącznie trwającego pojedynku).
 */
public class ModerationManager {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";
    private static final long REPORT_COOLDOWN_MS = 30_000L;

    private final BoxPvpPlugin plugin;
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> frozen = new HashSet<>();
    private final Map<UUID, Long> lastReportAt = new HashMap<>();

    public ModerationManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isVanished(UUID uuid) {
        return vanished.contains(uuid);
    }

    public boolean isFrozen(UUID uuid) {
        return frozen.contains(uuid);
    }

    /** Przełącza vanish i od razu chowa/pokazuje gracza wszystkim online nie-adminom. Zwraca nowy stan. */
    public boolean toggleVanish(Player player) {
        UUID uuid = player.getUniqueId();
        if (vanished.remove(uuid)) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                other.showPlayer(plugin, player);
            }
            return false;
        }
        vanished.add(uuid);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.hasPermission(ADMIN_PERMISSION) && !other.getUniqueId().equals(uuid)) {
                other.hidePlayer(plugin, player);
            }
        }
        return true;
    }

    /** Dopasowuje widoczność dla gracza, który właśnie dołączył - w obie strony (widz + ewentualnie sam vanished). */
    public void applyVisibilityOnJoin(Player joined) {
        for (UUID uuid : vanished) {
            if (uuid.equals(joined.getUniqueId())) {
                continue;
            }
            Player vanishedPlayer = Bukkit.getPlayer(uuid);
            if (vanishedPlayer != null && !joined.hasPermission(ADMIN_PERMISSION)) {
                joined.hidePlayer(plugin, vanishedPlayer);
            }
        }
        if (vanished.contains(joined.getUniqueId())) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.hasPermission(ADMIN_PERMISSION) && !other.getUniqueId().equals(joined.getUniqueId())) {
                    other.hidePlayer(plugin, joined);
                }
            }
        }
    }

    /**
     * Przełącza freeze na danym graczu. Zwraca nowy stan. Ustawia też allowFlight - bez tego
     * wanilijny serwer po dłuższym staniu w miejscu bez spadania (blokada ruchu nadpisuje pozycję
     * co tick) wyrzuca gracza komunikatem "Flying is not enabled on tym serwerze".
     */
    public boolean toggleFreeze(Player target) {
        UUID uuid = target.getUniqueId();
        if (frozen.remove(uuid)) {
            target.setAllowFlight(false);
            target.setFlying(false);
            target.sendMessage("§aZostałeś odmrożony.");
            return false;
        }
        frozen.add(uuid);
        target.setAllowFlight(true);
        target.setFlying(false);
        target.sendMessage("§c§lZOSTAŁEŚ ZAMROŻONY §7przez administrację. Nie ruszaj się.");
        return true;
    }

    public boolean canReport(UUID uuid) {
        Long last = lastReportAt.get(uuid);
        return last == null || System.currentTimeMillis() - last >= REPORT_COOLDOWN_MS;
    }

    public void recordReport(UUID uuid) {
        lastReportAt.put(uuid, System.currentTimeMillis());
    }
}
