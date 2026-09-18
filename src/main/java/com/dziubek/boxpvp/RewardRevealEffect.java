package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Jednorazowy "reveal" nagrody nad głową gracza - ten sam pomysł co przy otwieraniu skrzyni
 * (spadający, wirujący przedmiot + wymuszona kamera + wybuch na finał), ale bez trwałego
 * displaya: encja żyje tylko na czas animacji, a potem znika. Używane przez /daily i /kit.
 */
public class RewardRevealEffect {

    private static final double HEIGHT_ABOVE_HEAD = 2.3;
    private static final double DROP_START_OFFSET = 2.5;
    private static final long DROP_DURATION_MS = 700;
    private static final long SPIN_PERIOD_MS = 500;
    private static final int FREEZE_TICKS = 30;
    private static final float DISPLAY_SCALE = 1.1f;

    private RewardRevealEffect() {
    }

    /**
     * Pełna wersja z zamrożeniem gracza i wymuszoną kamerą - dla /daily i /kit.
     */
    public static void play(BoxPvpPlugin plugin, Player player, ItemStack item) {
        if (!player.isOnline() || item == null) {
            return;
        }
        Location anchor = player.getLocation();
        ItemDisplay display = spawnDisplay(player.getWorld(), anchor.clone().add(0, HEIGHT_ABOVE_HEAD, 0), item);

        long start = System.currentTimeMillis();
        animate(plugin, player, anchor, display, start, FREEZE_TICKS);
    }

    /**
     * Lżejsza wersja bez zamrożenia gracza/kamery - dla /sklep (zakupy mogą być częste,
     * przymusowa "cutscenka" za każdym razem byłaby uciążliwa).
     */
    public static void playLight(BoxPvpPlugin plugin, Player player, ItemStack item) {
        if (!player.isOnline() || item == null) {
            return;
        }
        Location spawnAt = player.getLocation().add(0, HEIGHT_ABOVE_HEAD, 0);
        ItemDisplay display = spawnDisplay(player.getWorld(), spawnAt, item);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        spinOnly(plugin, display, System.currentTimeMillis(), 20);
    }

    private static ItemDisplay spawnDisplay(World world, Location at, ItemStack item) {
        return world.spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(item.clone());
            e.setBillboard(Display.Billboard.FIXED);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
        });
    }

    private static void animate(BoxPvpPlugin plugin, Player player, Location anchor, ItemDisplay display,
                                 long start, int ticksLeft) {
        if (!player.isOnline() || !display.isValid()) {
            if (display.isValid()) {
                display.remove();
            }
            return;
        }

        long elapsed = System.currentTimeMillis() - start;
        Location target = renderFrame(display, anchor, elapsed);
        CameraUtil.forceLookAt(player, anchor, target);

        if (ticksLeft <= 0) {
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, target, 60, 0.4, 0.4, 0.4, 0.4);
            player.playSound(target, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
            display.remove();
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> animate(plugin, player, anchor, display, start, ticksLeft - 1), 1L);
    }

    private static void spinOnly(BoxPvpPlugin plugin, ItemDisplay display, long start, int ticksLeft) {
        if (!display.isValid()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - start;
        float angle = (float) ((elapsed % SPIN_PERIOD_MS) / (double) SPIN_PERIOD_MS * Math.PI * 2);
        float scale = DISPLAY_SCALE + 0.15f * (float) Math.sin(elapsed / 150.0);

        Transformation transform = new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(new AxisAngle4f(angle, 0f, 1f, 0f)),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        );
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTransformation(transform);

        if (ticksLeft <= 0) {
            display.remove();
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> spinOnly(plugin, display, start, ticksLeft - 1), 1L);
    }

    /**
     * Ustawia Transformation displaya na tę klatkę (bujanie + opadanie z góry na start) i
     * zwraca dokładną, aktualną pozycję renderowania - żeby kamera mogła realnie za nim podążać.
     */
    private static Location renderFrame(ItemDisplay display, Location anchor, long elapsedMs) {
        float dropOffset = 0f;
        if (elapsedMs < DROP_DURATION_MS) {
            double t = Math.min(1.0, elapsedMs / (double) DROP_DURATION_MS);
            dropOffset = (float) ((1.0 - CameraUtil.easeOutCubic(t)) * DROP_START_OFFSET);
        }
        float bob = (float) (Math.sin(elapsedMs / 400.0) * 0.05);
        float angle = (float) ((elapsedMs % SPIN_PERIOD_MS) / (double) SPIN_PERIOD_MS * Math.PI * 2);

        Transformation transform = new Transformation(
                new Vector3f(0f, bob, 0f),
                new Quaternionf(new AxisAngle4f(angle, 0f, 1f, 0f)),
                new Vector3f(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE),
                new Quaternionf()
        );
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTransformation(transform);

        return anchor.clone().add(0, HEIGHT_ABOVE_HEAD + bob + dropOffset, 0);
    }
}
