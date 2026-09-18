package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Efekty odtwarzane przy otwieraniu fizycznej skrzyni w świecie.
 */
public enum CrateEffect {

    FLAME("Płomienie", Particle.FLAME, Sound.BLOCK_FIRE_AMBIENT),
    MAGIA("Magia", Particle.WITCH, Sound.ENTITY_WITCH_AMBIENT),
    KONFETTI("Konfetti", Particle.HAPPY_VILLAGER, Sound.ENTITY_VILLAGER_YES),
    TOTEM("Totem", Particle.TOTEM_OF_UNDYING, Sound.ITEM_TOTEM_USE),
    GWIAZDY("Gwiazdy", Particle.END_ROD, Sound.BLOCK_BEACON_ACTIVATE),
    PORTAL("Portal", Particle.PORTAL, Sound.BLOCK_PORTAL_AMBIENT),
    FAJERWERKI("Fajerwerki", Particle.FIREWORK, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH),
    SPIRALA("Spirala", Particle.END_ROD, Sound.BLOCK_AMETHYST_BLOCK_CHIME);

    private final String displayName;
    private final Particle particle;
    private final Sound sound;

    CrateEffect(String displayName, Particle particle, Sound sound) {
        this.displayName = displayName;
        this.particle = particle;
        this.sound = sound;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CrateEffect fromString(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String listNames() {
        StringBuilder sb = new StringBuilder();
        for (CrateEffect effect : values()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(effect.name());
        }
        return sb.toString();
    }

    public void play(BoxPvpPlugin plugin, Location blockLocation) {
        Location center = blockLocation.clone().add(0.5, 1.0, 0.5);
        World world = center.getWorld();
        if (world == null) {
            return;
        }

        if (this == SPIRALA) {
            playSpiral(plugin, center);
            return;
        }

        world.spawnParticle(particle, center, 40, 0.4, 0.5, 0.4, 0.02);
        world.playSound(center, sound, 1.0f, 1.0f);
    }

    /**
     * Rozbudowana, cicha animacja widoczna cały czas dookoła i nad blokiem, gdy nikt akurat nie
     * otwiera skrzyni: dwa pierścienie na różnych wysokościach kręcące się w przeciwne strony
     * (efekt "podwójnej spirali") plus cząsteczki opadające z góry na skrzynię w kółko od nowa.
     * Wszystko liczone z czasu systemowego, więc animacja jest płynna niezależnie jak często
     * wywołujemy tę metodę, i wszystkie skrzynie animują się zsynchronizowanie.
     */
    public void playIdle(Location blockLocation) {
        Location base = blockLocation.clone().add(0.5, 0.0, 0.5);
        World world = base.getWorld();
        if (world == null) {
            return;
        }
        Particle idleParticle = (this == SPIRALA) ? Particle.END_ROD : particle;
        long now = System.currentTimeMillis();

        // dwa pierscienie, rozna wysokosc/promien/predkosc, kreca sie w przeciwne strony
        spawnRing(world, base, idleParticle, now, 0.85, 0.9, 5, 4000, false);
        spawnRing(world, base, idleParticle, now, 0.55, 0.3, 4, 3000, true);

        // czasteczki opadajace z gory na skrzynie, zapetlone
        int fallers = 4;
        double fallHeight = 1.6;
        for (int i = 0; i < fallers; i++) {
            double phase = ((now + i * 900L) % 1600L) / 1600.0;
            double y = fallHeight - phase * fallHeight;
            double angle = Math.toRadians(i * (360.0 / fallers));
            double radius = 0.35;
            double x = base.getX() + radius * Math.cos(angle);
            double z = base.getZ() + radius * Math.sin(angle);
            world.spawnParticle(idleParticle, x, base.getY() + y, z, 1, 0, 0, 0, 0);
        }
    }

    private void spawnRing(World world, Location base, Particle idleParticle, long now,
                            double radius, double height, int points, long periodMs, boolean reverse) {
        double baseAngle = (now % periodMs) / (double) periodMs * Math.PI * 2;
        if (reverse) {
            baseAngle = -baseAngle;
        }
        for (int i = 0; i < points; i++) {
            double angle = baseAngle + (Math.PI * 2 / points) * i;
            double x = base.getX() + radius * Math.cos(angle);
            double z = base.getZ() + radius * Math.sin(angle);
            world.spawnParticle(idleParticle, x, base.getY() + height, z, 1, 0, 0, 0, 0);
        }
    }

    private void playSpiral(BoxPvpPlugin plugin, Location center) {
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        world.playSound(center, sound, 1.0f, 1.2f);

        new BukkitRunnable() {
            double angle = 0;
            double height = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ > 40 || center.getWorld() == null) {
                    cancel();
                    return;
                }

                angle += Math.toRadians(28);
                height += 0.05;
                double radius = Math.max(0.1, 0.8 - (height * 0.5));

                double x = center.getX() + radius * Math.cos(angle);
                double z = center.getZ() + radius * Math.sin(angle);
                double y = center.getY() + height;

                center.getWorld().spawnParticle(Particle.END_ROD, x, y, z, 1, 0, 0, 0, 0);

                if (ticks % 5 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f + (ticks * 0.01f));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
