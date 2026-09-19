package com.dziubek.boxpvp;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boostery kupowane w /sklep (pomysł #11) - osobisty, CZASOWY mnożnik zarobków (auto-sprzedaż
 * + nagrody za zabójstwa/serie), niezależny od globalnego eventu monet i mnożnika prestiżu -
 * wszystkie trzy mnożą się razem w EventManager#totalMultiplier.
 * Admin tworzy zwykły przedmiot typu COMMAND w /sklep z komendą np.
 * "bpvp booster give %player% 2 30" (x2 na 30 minut) - żadnych zmian w GUI sklepu nie trzeba.
 */
public class BoosterManager {

    private final Map<UUID, Double> multiplier = new HashMap<>();
    private final Map<UUID, Long> expiresAt = new HashMap<>();

    public void give(Player player, double boosterMultiplier, int minutes) {
        UUID uuid = player.getUniqueId();
        multiplier.put(uuid, boosterMultiplier);
        expiresAt.put(uuid, System.currentTimeMillis() + minutes * 60_000L);
        player.sendMessage(Branding.chatPrefix() + "§a§lBooster aktywny! §7Mnożnik zarobków §fx" + trim(boosterMultiplier)
                + " §7przez " + minutes + " min.");
    }

    /** 1.0, jeśli gracz nie ma aktywnego (albo wygasłego) boostera. */
    public double getMultiplier(UUID uuid) {
        Long expiry = expiresAt.get(uuid);
        if (expiry == null || System.currentTimeMillis() >= expiry) {
            return 1.0;
        }
        return multiplier.getOrDefault(uuid, 1.0);
    }

    public long getMillisRemaining(UUID uuid) {
        Long expiry = expiresAt.get(uuid);
        if (expiry == null) {
            return 0;
        }
        return Math.max(0, expiry - System.currentTimeMillis());
    }

    private static String trim(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }
}
