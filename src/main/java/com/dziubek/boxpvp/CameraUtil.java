package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Wspólna matematyka do "cutscenkowych" efektów: przymusowe skierowanie kamery gracza
 * na punkt w świecie oraz łagodne zwalnianie (ease-out) używane przy animacjach opadania.
 * Używane przez skrzynie i inne "reveal" efekty (daily, kity), żeby nie duplikować wzorów.
 */
public final class CameraUtil {

    private CameraUtil() {
    }

    /**
     * Teleportuje gracza na "standingAt" z yaw/pitch obróconym w stronę "target" - efektywnie
     * zamraża pozycję (powtarzane co tick z tym samym standingAt) i wymusza kierunek patrzenia.
     */
    public static void forceLookAt(Player player, Location standingAt, Location target) {
        Location eye = standingAt.clone().add(0, player.getEyeHeight(), 0);
        double dx = target.getX() - eye.getX();
        double dy = target.getY() - eye.getY();
        double dz = target.getZ() - eye.getZ();
        double distanceXZ = Math.sqrt(dx * dx + dz * dz);

        Location forced = standingAt.clone();
        forced.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        forced.setPitch((float) Math.toDegrees(-Math.atan2(dy, distanceXZ)));
        player.teleport(forced);
    }

    /**
     * Szybki start, płynne zwolnienie pod koniec (t w zakresie 0-1).
     */
    public static double easeOutCubic(double t) {
        double f = t - 1.0;
        return f * f * f + 1.0;
    }
}
