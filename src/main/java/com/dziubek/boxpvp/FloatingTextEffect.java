package com.dziubek.boxpvp;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

/**
 * Wspólny "unoszący się tekst" (rośnie i znika) - +$ przy auto-sprzedaży, imię ofiary przy
 * zabójstwie, "KRYTYK!", ukończenie misji itd. Jedna encja, bez trwałego stanu.
 */
public final class FloatingTextEffect {

    private static final long DURATION_MS = 900L;
    private static final double RISE_HEIGHT = 1.1;

    private FloatingTextEffect() {
    }

    public static void show(BoxPvpPlugin plugin, Location at, String text) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        TextDisplay display = world.spawn(at.clone(), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setSeeThrough(true);
            e.setShadowed(true);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.setText(text);
        });
        animate(plugin, display, at.clone(), System.currentTimeMillis());
    }

    private static void animate(BoxPvpPlugin plugin, TextDisplay display, Location base, long start) {
        if (!display.isValid()) {
            return;
        }
        double t = Math.min(1.0, (System.currentTimeMillis() - start) / (double) DURATION_MS);
        display.teleport(base.clone().add(0, RISE_HEIGHT * t, 0));

        if (t >= 1.0) {
            display.remove();
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animate(plugin, display, base, start), 1L);
    }
}
