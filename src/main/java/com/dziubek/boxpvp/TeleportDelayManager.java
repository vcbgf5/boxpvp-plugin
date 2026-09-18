package com.dziubek.boxpvp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wszystkie teleporty (spawn, tpa, home) przechodzą przez to - gracz musi postać
 * N sekund bez ruchu (domyślnie 5), inaczej teleport się anuluje. Anuluje się też
 * automatycznie gdy gracz wejdzie w walkę w trakcie odliczania. Przez cały czas
 * odliczania nad głową gracza wiruje kompas, a dookoła stóp kręci się pierścień
 * cząsteczek; start i lądowanie kończą się błyskiem.
 */
public class TeleportDelayManager {

    private final BoxPvpPlugin plugin;
    private final Map<UUID, Pending> pending = new HashMap<>();

    public TeleportDelayManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public void cancel(UUID uuid) {
        Pending p = pending.remove(uuid);
        if (p == null) {
            return;
        }
        p.countdownTask.cancel();
        p.visualTask.cancel();
        if (p.display.isValid()) {
            p.display.remove();
        }
    }

    public void start(Player player, Location destination, String label) {
        cancel(player.getUniqueId());

        int delaySeconds = plugin.getConfig().getInt("teleport-delay-seconds", 5);
        Location startLoc = player.getLocation();

        player.sendMessage("§eTeleportacja (" + label + ") za " + delaySeconds + "s. Nie ruszaj się!");

        ItemDisplay display = spawnCompass(player);

        BukkitRunnable countdownRunnable = new BukkitRunnable() {
            int ticksLeft = delaySeconds * 20;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    TeleportDelayManager.this.cancel(player.getUniqueId());
                    return;
                }

                if (plugin.getCombatManager().isTagged(player.getUniqueId())) {
                    player.sendMessage("§cTeleportacja anulowana - jesteś w walce!");
                    TeleportDelayManager.this.cancel(player.getUniqueId());
                    return;
                }

                if (hasMoved(player.getLocation(), startLoc)) {
                    player.sendMessage("§cTeleportacja anulowana - poruszyłeś się!");
                    TeleportDelayManager.this.cancel(player.getUniqueId());
                    return;
                }

                ticksLeft -= 20;

                if (ticksLeft <= 0) {
                    spawnFlash(player.getLocation());
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.teleport(destination);
                    spawnFlash(destination);
                    player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    player.sendMessage("§aTeleportowano!");
                    TeleportDelayManager.this.cancel(player.getUniqueId());
                    return;
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                        new TextComponent("§e" + label + " za " + (ticksLeft / 20) + "s... §7(nie ruszaj się)"));
            }
        };

        BukkitTask countdownTask = countdownRunnable.runTaskTimer(plugin, 20L, 20L);
        BukkitTask visualTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> tickVisual(player, display), 0L, 2L);

        pending.put(player.getUniqueId(), new Pending(countdownTask, visualTask, display));
    }

    private ItemDisplay spawnCompass(Player player) {
        Location at = player.getLocation().add(0, 2.2, 0);
        return player.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setItemStack(new ItemStack(Material.COMPASS));
            e.setBillboard(Display.Billboard.FIXED);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
        });
    }

    private void tickVisual(Player player, ItemDisplay display) {
        if (!player.isOnline()) {
            return;
        }
        Location base = player.getLocation();
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis();

        double baseAngle = (now % 1200) / 1200.0 * Math.PI * 2;
        int points = 8;
        for (int i = 0; i < points; i++) {
            double angle = baseAngle + (Math.PI * 2 / points) * i;
            double x = base.getX() + 0.9 * Math.cos(angle);
            double z = base.getZ() + 0.9 * Math.sin(angle);
            world.spawnParticle(Particle.PORTAL, x, base.getY() + 0.1, z, 1, 0, 0, 0, 0);
        }

        if (display.isValid()) {
            display.teleport(base.clone().add(0, 2.2, 0));
            float spinAngle = (float) ((now % 1500) / 1500.0 * Math.PI * 2);
            Transformation transform = new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new Quaternionf(new AxisAngle4f(spinAngle, 0f, 1f, 0f)),
                    new Vector3f(0.5f, 0.5f, 0.5f),
                    new Quaternionf()
            );
            display.setInterpolationDelay(0);
            display.setInterpolationDuration(2);
            display.setTransformation(transform);
        }
    }

    private void spawnFlash(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        world.spawnParticle(Particle.END_ROD, location.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.1);
    }

    private boolean hasMoved(Location a, Location b) {
        return a.getBlockX() != b.getBlockX()
                || a.getBlockY() != b.getBlockY()
                || a.getBlockZ() != b.getBlockZ();
    }

    private static final class Pending {
        final BukkitTask countdownTask;
        final BukkitTask visualTask;
        final ItemDisplay display;

        Pending(BukkitTask countdownTask, BukkitTask visualTask, ItemDisplay display) {
            this.countdownTask = countdownTask;
            this.visualTask = visualTask;
            this.display = display;
        }
    }
}
