package com.dziubek.boxpvp;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * EKSPERYMENT: tekst "przyklejony" do ekranu gracza (nie do świata) - zamiast bossbara/actionbara
 * (stałe miejsca w vanilla UI) używa niewidzialnych dla innych graczy encji TextDisplay,
 * repozycjonowanych co kilka ticków tak, żeby zawsze wisiały w tym samym miejscu WIDOKU gracza
 * (obliczane z wektora patrzenia kamery), niezależnie gdzie gracz patrzy/stoi - dokładnie ta
 * sama sztuczka co w pluginach typu BetterHud/HUDEngine, tylko własna implementacja.
 *
 * Pozycja to przybliżenie (nie znamy realnego FOV/proporcji ekranu klienta - to ustawienie samego
 * gracza, niedostępne po stronie serwera) - dlatego offsety są PUBLICZNE i modyfikowalne w locie
 * przez /hudoffset, żeby dało się je dokalibrować wizualnie bez przebudowywania jara za każdym
 * razem.
 */
public class ScreenAnchorTestManager {

    private static final long UPDATE_INTERVAL_TICKS = 2L;

    /** right = w prawo (+) / w lewo (-), up = w górę (+) / w dół (-), dist = ile bloków przed kamerą. */
    public double line1Right = 1.6, line1Up = 1.3, line1Dist = 3.0;
    public double line2Right = 1.6, line2Up = -1.6, line2Dist = 3.0;

    private final BoxPvpPlugin plugin;
    private final Map<UUID, TextDisplay> line1 = new HashMap<>();
    private final Map<UUID, TextDisplay> line2 = new HashMap<>();

    public ScreenAnchorTestManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            spawn(player);
        }
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, UPDATE_INTERVAL_TICKS, UPDATE_INTERVAL_TICKS);
    }

    public void spawn(Player player) {
        despawn(player);
        line1.put(player.getUniqueId(), create(player, "§e§lTEST 1", line1Right, line1Up, line1Dist));
        line2.put(player.getUniqueId(), create(player, "§b§lTEST 2", line2Right, line2Up, line2Dist));
    }

    public void despawn(Player player) {
        TextDisplay a = line1.remove(player.getUniqueId());
        if (a != null) {
            a.remove();
        }
        TextDisplay b = line2.remove(player.getUniqueId());
        if (b != null) {
            b.remove();
        }
    }

    private TextDisplay create(Player player, String legacyText, double right, double up, double dist) {
        Location loc = anchorPoint(player, right, up, dist);
        TextDisplay display = player.getWorld().spawn(loc, TextDisplay.class, td -> {
            td.setText(legacyText);
            td.setBillboard(Display.Billboard.CENTER);
            td.setBackgroundColor(Color.fromARGB(0));
            td.setSeeThrough(true);
            td.setShadowed(false);
            td.setPersistent(false);
            td.setInvulnerable(true);
            td.setVisibleByDefault(false);
        });
        player.showEntity(plugin, display);
        return display;
    }

    private void updateAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            TextDisplay a = line1.get(player.getUniqueId());
            TextDisplay b = line2.get(player.getUniqueId());
            if (a == null || b == null) {
                spawn(player);
                continue;
            }
            a.teleport(anchorPoint(player, line1Right, line1Up, line1Dist));
            b.teleport(anchorPoint(player, line2Right, line2Up, line2Dist));
        }
    }

    private Location anchorPoint(Player player, double right, double up, double dist) {
        Location eye = player.getEyeLocation();
        Vector forward = eye.getDirection().normalize();
        Vector worldUp = new Vector(0, 1, 0);
        Vector rightVec = forward.clone().crossProduct(worldUp);
        if (rightVec.lengthSquared() < 1.0E-6) {
            rightVec = new Vector(1, 0, 0);
        }
        rightVec.normalize();
        Vector upVec = rightVec.clone().crossProduct(forward).normalize();

        return eye.clone()
                .add(forward.clone().multiply(dist))
                .add(rightVec.multiply(right))
                .add(upVec.multiply(up));
    }
}
