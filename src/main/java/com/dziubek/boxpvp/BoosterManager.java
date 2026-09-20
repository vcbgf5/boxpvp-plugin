package com.dziubek.boxpvp;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Boostery kupowane w /sklep (typ BOOST) albo dane przez admina (/bpvp booster give|remove) -
 * osobisty, CZASOWY mnożnik zarobków (auto-sprzedaż + nagrody za zabójstwa/serie), niezależny od
 * globalnego eventu monet i mnożnika prestiżu - wszystkie trzy mnożą się razem w
 * EventManager#totalMultiplier.
 * Boostery SIĘ STAKUJĄ - kupienie/dostanie kolejnego, gdy poprzedni jest jeszcze aktywny, mnoży
 * mnożniki przez siebie (x2 + x2 = x4) i DODAJE czas do tego, co zostało (nie nadpisuje).
 */
public class BoosterManager {

    private final Map<UUID, Double> multiplier = new HashMap<>();
    private final Map<UUID, Long> expiresAt = new HashMap<>();

    public void give(Player player, double boosterMultiplier, int minutes) {
        UUID uuid = player.getUniqueId();
        double stackedMultiplier = getMultiplier(uuid) * boosterMultiplier;
        long baseExpiry = Math.max(System.currentTimeMillis(), expiresAt.getOrDefault(uuid, 0L));
        long newExpiry = baseExpiry + minutes * 60_000L;

        multiplier.put(uuid, stackedMultiplier);
        expiresAt.put(uuid, newExpiry);

        long minutesLeft = Math.max(0, (newExpiry - System.currentTimeMillis()) / 60_000L);
        player.sendMessage(Branding.chatPrefix() + "§a§lBooster aktywny! §7Łączny mnożnik zarobków §fx" + trim(stackedMultiplier)
                + " §7przez §f" + minutesLeft + " min §7(łącznie).");
    }

    /** Zdejmuje aktywny booster (admin) - zwraca false, jeśli gracz nie miał żadnego. */
    public boolean remove(UUID uuid) {
        boolean had = getMultiplier(uuid) > 1.0;
        multiplier.remove(uuid);
        expiresAt.remove(uuid);
        return had;
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
