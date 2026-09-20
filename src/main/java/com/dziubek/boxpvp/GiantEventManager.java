package com.dziubek.boxpvp;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Giant;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Mega-zombie: wielka skrzynka spada z nieba, po wylądowaniu "otwiera się" przez 10 sekund
 * (bossbar-odliczanie jak przy skrzynce-event), a potem wychodzi z niej Giant - własny mini-boss
 * z boss barem HP. Giant w wanilii nie ma ŻADNEGO zachowania (Mojang zostawił go bez celów AI),
 * więc cały ruch (chodzenie w stronę najbliższego gracza) idzie przez Pathfinder sterowany
 * ręcznie co kilka klatek - to jest ta "własna AI" pluginu. Atak, granica Spawn01, obrażenia od
 * upadku/słońca - identyczny wzorzec co ZombieEventManager.
 */
public class GiantEventManager {

    private static final String TAG = "bpvp_giant_event";
    private static final String MINION_TAG = "bpvp_giant_minion";
    private static final double MAX_HEALTH = 1000.0;
    private static final double MOVE_SPEED = 1.0;
    private static final double DETECT_RANGE = 64.0;
    private static final double HOP_VELOCITY = 0.45;
    private static final long RETARGET_INTERVAL_TICKS = 20L;
    private static final long LIFETIME_TICKS = 20L * 60 * 8;
    private static final long TICK_INTERVAL = 5L;

    /** Po każdym ataku Giant stoi bez ruchu i nie zaczyna kolejnego - okno, w którym łatwo go bić. */
    private static final long ATTACK_REST_MS = 2_000L;

    /** Poniżej tego % HP Giant wpada w szał: szybszy ruch i krótsze cooldowny ataków. */
    private static final double RAGE_HP_THRESHOLD = 0.30;
    private static final double RAGE_SPEED_MULTIPLIER = 1.6;
    private static final double RAGE_COOLDOWN_MULTIPLIER = 0.6;

    private static final double THROW_DAMAGE = 8.0;
    private static final long THROW_COOLDOWN_MS = 3_000L;
    private static final double THROW_RANGE = 20.0;
    private static final double THROW_SPEED = 1.7;
    private static final double THROW_HIT_RADIUS = 1.3;
    private static final long THROW_LIFETIME_TICKS = 20L * 2;
    private static final long THROW_WINDUP_TICKS = 8L;

    private static final long WAVE_COOLDOWN_MS = 8_000L;
    private static final double WAVE_TRIGGER_RANGE = 40.0;
    private static final double WAVE_MAX_RADIUS = 40.0;
    private static final long WAVE_EXPAND_TICKS = 50L;
    private static final double WAVE_BAND = 1.5;
    private static final double WAVE_DAMAGE = 10.0;
    private static final double WAVE_LAUNCH_VELOCITY = 1.2;
    private static final int WAVE_PARTICLE_POINTS = 48;

    private static final long BOMBARD_INTERVAL_MS = 10_000L;
    private static final int BOMBARD_COUNT = 8;
    private static final double BOMBARD_RADIUS = 29.0;
    private static final long BOMBARD_TELEGRAPH_TICKS = 20L;
    private static final double BOMBARD_FALL_HEIGHT = 15.0;
    private static final long BOMBARD_FALL_DURATION_MS = 700L;
    private static final double BOMBARD_DAMAGE = 10.0;
    private static final double BOMBARD_HIT_RADIUS = 1.5;

    private static final long CHARGE_COOLDOWN_MS = 15_000L;
    private static final double CHARGE_TRIGGER_RANGE = 25.0;
    private static final double CHARGE_MIN_RANGE = 6.0;
    private static final long CHARGE_WINDUP_TICKS = 15L;
    private static final long CHARGE_DURATION_TICKS = 16L;
    private static final double CHARGE_SPEED = 1.6;
    private static final double CHARGE_DAMAGE = 12.0;
    private static final double CHARGE_HIT_RADIUS = 1.8;
    private static final double CHARGE_KNOCKBACK = 1.1;

    private static final long ROAR_COOLDOWN_MS = 20_000L;
    private static final double ROAR_RANGE = 15.0;
    private static final int ROAR_SLOWNESS_TICKS = 20 * 4;
    private static final int ROAR_BLINDNESS_TICKS = 20 * 2;

    private static final long SUMMON_COOLDOWN_MS = 25_000L;
    private static final long SUMMON_ACTIVE_MS = 800L;
    private static final long MINION_LIFETIME_TICKS = 20L * 45;

    private static final double CRATE_FALL_START_OFFSET = 60.0;
    private static final long CRATE_FALL_DURATION_MS = 5_000L;
    private static final double CRATE_LABEL_HEIGHT_OFFSET = 2.4;
    private static final double CRATE_GROUND_CHECK_DISTANCE = 2.0;
    private static final long OPENING_TICKS = 20L * 30;
    private static final float CRATE_SCALE = 3.0f;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey ownerTag;
    private final List<ItemStack> rewardPool = new ArrayList<>();
    private final Random random = new Random();
    private final Map<UUID, TrackedGiant> tracked = new HashMap<>();
    private volatile boolean eventActive = false;
    private Location lastLocation;

    public GiantEventManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "giantevent.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć giantevent.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.ownerTag = new NamespacedKey(plugin, "bpvp_giant_event_owner");
        loadRewards();
    }

    public void addReward(ItemStack item) {
        rewardPool.add(item.clone());
        saveRewards();
    }

    public void clearRewards() {
        rewardPool.clear();
        saveRewards();
    }

    public List<ItemStack> rewards() {
        return rewardPool;
    }

    public boolean isTracked(Entity entity) {
        return tracked.containsKey(entity.getUniqueId());
    }

    /** Czy jakaś wielka skrzynka/Giant jest aktualnie w trakcie (spada, otwiera się, albo żyje). */
    public boolean isEventActive() {
        return eventActive;
    }

    /** Miejsce ostatniej wielkiej skrzynki/Gianta (do /bpvp teleportto) - null, jeśli jeszcze żadna nie spadła. */
    public Location getLastLocation() {
        return lastLocation == null ? null : lastLocation.clone();
    }

    /** Usuwa osierocone Giganty i sługi-zombie sprzed restartu (nie ma ich w świeżej mapie tracked). */
    public void purgeOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Giant.class)) {
                if (entity.getScoreboardTags().contains(TAG) && !tracked.containsKey(entity.getUniqueId())) {
                    entity.remove();
                }
            }
            for (Entity entity : world.getEntitiesByClass(Zombie.class)) {
                if (entity.getScoreboardTags().contains(MINION_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    // ================= Wielka skrzynka =================

    /** Zrzuca wielką skrzynkę w danym miejscu - po wylądowaniu i 10s otwierania wyjdzie z niej Giant. */
    public void dropCrate(Location landAt) {
        World world = landAt.getWorld();
        if (world == null) {
            return;
        }
        eventActive = true;
        Location groundAnchor = landAt.clone().add(0.5, 0, 0.5);
        lastLocation = groundAnchor.clone();
        Location spawnAt = groundAnchor.clone().add(0, CRATE_FALL_START_OFFSET, 0);

        ItemDisplay crate = world.spawn(spawnAt, ItemDisplay.class, e -> {
            e.setBillboard(Display.Billboard.FIXED);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setGlowing(true);
            e.setItemStack(new ItemStack(Material.CHISELED_DEEPSLATE));
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, "crate");
            e.addScoreboardTag(TAG);
        });
        TextDisplay label = world.spawn(spawnAt.clone().add(0, CRATE_LABEL_HEIGHT_OFFSET, 0), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setGlowing(true);
            e.setText("§4§l☠ WIELKA SKRZYNKA");
            e.setSeeThrough(false);
            e.setShadowed(false);
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, "label");
            e.addScoreboardTag(TAG);
        });

        Bukkit.broadcastMessage(Branding.chatPrefix() + "§4§l☠ WIELKA SKRZYNKA §7spada z nieba! Coś ogromnego z niej wyjdzie...");
        world.playSound(groundAnchor, Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.4f);

        animateCrateFall(crate, label, groundAnchor, System.currentTimeMillis());
    }

    private void animateCrateFall(ItemDisplay crate, TextDisplay label, Location groundAnchor, long start) {
        if (!crate.isValid()) {
            if (label.isValid()) {
                label.remove();
            }
            return;
        }
        long elapsed = System.currentTimeMillis() - start;
        double t = Math.min(1.0, elapsed / (double) CRATE_FALL_DURATION_MS);
        double eased = CameraUtil.easeOutCubic(t);
        double heightOffset = (1.0 - eased) * CRATE_FALL_START_OFFSET;

        Location crateAt = groundAnchor.clone().add(0, heightOffset, 0);
        World world = groundAnchor.getWorld();
        RayTraceResult hit = world.rayTraceBlocks(crateAt, new Vector(0, -1, 0), CRATE_GROUND_CHECK_DISTANCE,
                FluidCollisionMode.NEVER, true);
        boolean touchedGround = hit != null && hit.getHitBlock() != null;
        if (touchedGround) {
            crateAt.setY(hit.getHitPosition().getY());
        }

        float spin = (float) ((System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI * 2);
        applyCrateTransform(crate, spin);
        crate.teleport(crateAt);
        label.teleport(crateAt.clone().add(0, CRATE_LABEL_HEIGHT_OFFSET, 0));

        if (touchedGround || t >= 1.0) {
            onCrateLanded(crate, label, crateAt);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animateCrateFall(crate, label, groundAnchor, start), 1L);
    }

    private void applyCrateTransform(ItemDisplay crate, float angle) {
        Transformation transform = new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(new AxisAngle4f(angle, 0f, 1f, 0f)),
                new Vector3f(CRATE_SCALE, CRATE_SCALE, CRATE_SCALE),
                new Quaternionf()
        );
        crate.setInterpolationDelay(0);
        crate.setInterpolationDuration(2);
        crate.setTransformation(transform);
    }

    private void onCrateLanded(ItemDisplay crate, TextDisplay label, Location landedAt) {
        applyCrateTransform(crate, 0f);
        crate.teleport(landedAt);
        label.setText("§4§l☠ Skrzynka się otwiera...");

        World world = landedAt.getWorld();
        world.spawnParticle(Particle.EXPLOSION, landedAt, 1);
        world.playSound(landedAt, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
        Bukkit.broadcastMessage(Branding.chatPrefix() + "§4§l☠ Wielka skrzynka §7wylądowała - otworzy się za 30 sekund!");
        BossBarUtil.showTimed(plugin, "§4§l☠ Skrzynka się otwiera...", BarColor.RED, OPENING_TICKS);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> openCrateAndSpawnGiant(crate, label, landedAt), OPENING_TICKS);
    }

    private void openCrateAndSpawnGiant(ItemDisplay crate, TextDisplay label, Location landedAt) {
        if (crate.isValid()) {
            crate.remove();
        }
        if (label.isValid()) {
            label.remove();
        }
        World world = landedAt.getWorld();
        world.spawnParticle(Particle.EXPLOSION_EMITTER, landedAt, 1);
        world.playSound(landedAt, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.7f);
        spawnGiant(landedAt);
    }

    // ================= Giant =================

    private void spawnGiant(Location landAt) {
        World world = landAt.getWorld();
        Location groundAnchor = landAt.clone().add(0.5, 0, 0.5);

        Giant giant = world.spawn(groundAnchor, Giant.class, g -> {
            g.setPersistent(false);
            g.setRemoveWhenFarAway(false);
            g.setCustomName(Branding.accent("☠ Mega-Zombie"));
            g.setCustomNameVisible(true);
            AttributeInstance maxHealthAttr = g.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(MAX_HEALTH);
            }
            g.setHealth(MAX_HEALTH);
            g.getPersistentDataContainer().set(ownerTag, PersistentDataType.BYTE, (byte) 1);
            g.addScoreboardTag(TAG);
        });

        BossBar healthBar = Bukkit.createBossBar(bossBarTitle(giant, false), BarColor.RED, BarStyle.SEGMENTED_10);
        for (Player player : Bukkit.getOnlinePlayers()) {
            healthBar.addPlayer(player);
        }

        TrackedGiant tg = new TrackedGiant(giant, groundAnchor, healthBar);
        tg.lifetimeTask = plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> despawnByTimeout(giant.getUniqueId()), LIFETIME_TICKS);
        tracked.put(giant.getUniqueId(), tg);
        startTicking(tg);

        Bukkit.broadcastMessage(Branding.chatPrefix() + "§4§l☠ MEGA-ZOMBIE §7wyszedł ze skrzynki!");
        world.playSound(groundAnchor, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.5f);
    }

    private String bossBarTitle(Giant giant, boolean raging) {
        String ragePrefix = raging ? "§c§l⚡ WŚCIEKŁY ⚡ §r" : "";
        return ragePrefix + "§4§l☠ Mega-Zombie §7- §c" + Math.round(giant.getHealth()) + "§7/§c" + Math.round(giant.getMaxHealth()) + " ❤";
    }

    private void startTicking(TrackedGiant tg) {
        new BukkitRunnable() {
            long ticksSinceRetarget = RETARGET_INTERVAL_TICKS;

            @Override
            public void run() {
                Giant giant = tg.giant;
                if (giant == null || !giant.isValid() || giant.isDead()) {
                    cleanupTracked(giant != null ? giant.getUniqueId() : null);
                    cancel();
                    return;
                }
                try {
                    enforceSpawnBoundary(giant, tg);
                    updateRage(giant, tg);

                    boolean busy = System.currentTimeMillis() < tg.busyUntil;
                    if (!busy) {
                        // Giant stoi w miejscu podczas ataku i przerwy po nim (tg.busyUntil) - dopiero
                        // gdy minie, wraca do pościgu i szuka kolejnego ataku (jednego na turę).
                        ticksSinceRetarget += TICK_INTERVAL;
                        long retargetInterval = tg.raging ? RETARGET_INTERVAL_TICKS / 2 : RETARGET_INTERVAL_TICKS;
                        if (ticksSinceRetarget >= retargetInterval) {
                            ticksSinceRetarget = 0;
                            retarget(giant);
                        }
                        tryAttacks(giant, tg);
                    }
                    tg.healthBar.setTitle(bossBarTitle(giant, tg.raging));
                    tg.healthBar.setProgress(Math.max(0.0, Math.min(1.0, giant.getHealth() / giant.getMaxHealth())));
                } catch (Exception e) {
                    // Wyjątek tutaj (w powtarzalnym BukkitRunnable) zostałby po cichu i na stałe
                    // ubity przez scheduler bez sprzątania - eventActive zostałoby zablokowane
                    // na "true" na zawsze, blokując wszystkie przyszłe spawny mega-zombie. Łapiemy
                    // więc wszystko tutaj i sprzątamy normalnie zamiast pozwolić na softlock.
                    plugin.getLogger().warning("Błąd w tickowaniu mega-zombie, sprzątam encję: " + e);
                    cleanupTracked(giant.getUniqueId());
                    cancel();
                }
            }
        }.runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);
    }

    /** Wybiera JEDEN atak na turę (żaden nie startuje, dopóki poprzedni + przerwa po nim trwa). */
    private void tryAttacks(Giant giant, TrackedGiant tg) {
        long now = System.currentTimeMillis();
        if (trySummonAttack(giant, tg, now)) {
            return;
        }
        if (tryRoarAttack(giant, tg, now)) {
            return;
        }
        if (tryChargeAttack(giant, tg, now)) {
            return;
        }
        if (tryBombardAttack(giant, tg, now)) {
            return;
        }
        if (tryWaveAttack(giant, tg, now)) {
            return;
        }
        tryThrowAttack(giant, tg, now);
    }

    /** Poniżej RAGE_HP_THRESHOLD Giant raz na zawsze przechodzi w tryb szału (szybszy, krótsze cooldowny). */
    private void updateRage(Giant giant, TrackedGiant tg) {
        if (tg.raging || giant.getHealth() / giant.getMaxHealth() > RAGE_HP_THRESHOLD) {
            return;
        }
        tg.raging = true;
        AttributeInstance speedAttr = giant.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * RAGE_SPEED_MULTIPLIER);
        }
        World world = giant.getWorld();
        world.spawnParticle(Particle.ANGRY_VILLAGER, giant.getLocation().add(0, 2.5, 0), 20, 0.6, 0.6, 0.6, 0.0);
        world.playSound(giant.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.5f);
        Bukkit.broadcastMessage(Branding.chatPrefix() + "§c§l⚡ Mega-Zombie wpada w SZAŁ! §7Jest szybszy i atakuje częściej!");
    }

    private long cooldown(long base, TrackedGiant tg) {
        return tg.raging ? (long) (base * RAGE_COOLDOWN_MULTIPLIER) : base;
    }

    /**
     * Giant w wanilii nie ma żadnych celów AI (Mojang go zostawił bez zachowań), więc ruch w
     * stronę najbliższego gracza jest w całości sterowany przez plugin - Pathfinder wciąż
     * korzysta z wanilijnej nawigacji (omija przeszkody), ale to MY każemy mu iść, a nie jego
     * własna AI.
     */
    private void retarget(Giant giant) {
        // Szybszy ruch w szale idzie przez podniesioną bazę atrybutu MOVEMENT_SPEED (updateRage),
        // więc tu zawsze zostaje ten sam mnożnik - inaczej podbilibyśmy prędkość podwójnie.
        Player nearest = nearestPlayer(giant, DETECT_RANGE);
        if (nearest != null) {
            giant.getPathfinder().moveTo(nearest.getLocation(), MOVE_SPEED);
            if (giant.isOnGround()) {
                giant.setVelocity(giant.getVelocity().setY(HOP_VELOCITY));
            }
        }
    }

    private Player nearestPlayer(Giant giant, double maxDistance) {
        Player nearest = null;
        double nearestDist = maxDistance;
        for (Entity entity : giant.getNearbyEntities(maxDistance, maxDistance, maxDistance)) {
            if (!(entity instanceof Player)) {
                continue;
            }
            Player candidate = (Player) entity;
            if (candidate.getGameMode() == GameMode.CREATIVE || candidate.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double dist = candidate.getLocation().distance(giant.getLocation());
            if (dist <= nearestDist) {
                nearest = candidate;
                nearestDist = dist;
            }
        }
        return nearest;
    }

    /**
     * Nie pozwala Giantowi wejść na region Spawn01 - dokładnie tak jak zombie-event
     * (ZombieEventManager.enforceSpawnBoundary). Bez WorldGuard po prostu nic nie robi.
     */
    private void enforceSpawnBoundary(Giant giant, TrackedGiant tg) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        Location loc = giant.getLocation();
        RegionManager regions = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(loc.getWorld()));
        if (regions == null) {
            return;
        }
        ProtectedRegion region = regions.getRegion(plugin.getProtectedRegionName());
        if (region == null) {
            return;
        }
        BlockVector3 pos = BlockVector3.at(loc.getX(), loc.getY(), loc.getZ());
        if (region.contains(pos)) {
            giant.teleport(tg.safeAnchor);
        }
    }

    /**
     * Giant nie bije ręką - rzuca blokiem (ItemDisplay lecący po torze) w najbliższego gracza.
     * Obrażenia idą przez Player#damage (nie setHealth), żeby zbroja/odporności naprawdę
     * redukowały obrażenia - to jedyne źródło EntityDamageByEntityEvent z Gianta jako damagerem,
     * więc GiantEventListener już go nie anuluje (usunięte razem z tą zmianą).
     */
    private boolean tryThrowAttack(Giant giant, TrackedGiant tg, long now) {
        if (now - tg.lastAttackAt < cooldown(THROW_COOLDOWN_MS, tg)) {
            return false;
        }
        Player target = nearestPlayer(giant, THROW_RANGE);
        if (target == null) {
            return false;
        }
        tg.lastAttackAt = now;
        tg.throwTargetUuid = target.getUniqueId();
        tg.busyUntil = now + THROW_WINDUP_TICKS * 50L + THROW_LIFETIME_TICKS * 50L + ATTACK_REST_MS;
        World world = giant.getWorld();
        giant.lookAt(target.getEyeLocation());
        world.playSound(giant.getEyeLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.3f);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> executeThrow(giant, tg), THROW_WINDUP_TICKS);
        return true;
    }

    /**
     * Rzuca w gracza namierzonego na POCZĄTKU zamachu (tg.throwTargetUuid) - nie przełącza się na
     * kogoś, kto akurat podszedł bliżej w trakcie WINDUP - "celuje w gracza najbliższego" liczy się
     * raz, w momencie rozpoczęcia ataku. Jeśli namierzony gracz zniknął (wylogował/zmienił świat/
     * tryb) - dopiero wtedy szuka nowego najbliższego jako zapasowy cel.
     * Celuje w miejsce, w którym gracz BĘDZIE za tyle ticków, ile pocisk potrzebuje na dolecenie
     * (prosta ekstrapolacja z jego aktualnej prędkości) - trafia też w gracza biegnącego w bok,
     * a nie tylko stojącego w miejscu.
     */
    private void executeThrow(Giant giant, TrackedGiant tg) {
        if (!giant.isValid() || giant.isDead()) {
            return;
        }
        Player target = tg.throwTargetUuid != null ? Bukkit.getPlayer(tg.throwTargetUuid) : null;
        if (target == null || !target.isOnline() || target.getWorld() != giant.getWorld()
                || target.getGameMode() == GameMode.CREATIVE || target.getGameMode() == GameMode.SPECTATOR) {
            target = nearestPlayer(giant, THROW_RANGE);
        }
        if (target == null) {
            return;
        }
        World world = giant.getWorld();
        Location handAt = giant.getEyeLocation().subtract(0, 1.5, 0);
        double roughDistance = Math.max(1.0, handAt.distance(target.getEyeLocation()));
        double travelTicks = roughDistance / THROW_SPEED;
        Vector predictedTarget = target.getEyeLocation().toVector().add(target.getVelocity().multiply(travelTicks));
        Vector direction = predictedTarget.subtract(handAt.toVector());
        if (direction.lengthSquared() == 0) {
            return;
        }
        double distance = Math.max(1.0, direction.length());
        Vector velocity = direction.normalize().multiply(THROW_SPEED).setY(Math.min(0.6, distance / 20.0));

        ItemDisplay block = world.spawn(handAt, ItemDisplay.class, e -> {
            e.setBillboard(Display.Billboard.FIXED);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setItemStack(new ItemStack(Material.COBBLESTONE));
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, "throw");
            e.addScoreboardTag(TAG);
        });
        world.playSound(handAt, Sound.ENTITY_RAVAGER_ATTACK, 1.0f, 0.7f);
        animateThrow(giant, block, handAt.clone(), velocity, System.currentTimeMillis());
    }

    private void animateThrow(Giant giant, ItemDisplay block, Location position, Vector velocity, long start) {
        if (!block.isValid() || System.currentTimeMillis() - start > THROW_LIFETIME_TICKS * 50L) {
            if (block.isValid()) {
                block.remove();
            }
            return;
        }
        velocity.setY(velocity.getY() - 0.03);
        Location at = position.add(velocity);
        World world = block.getWorld();

        float spin = (float) ((System.currentTimeMillis() % 1000L) / 1000.0 * Math.PI * 2);
        Transformation transform = new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(new AxisAngle4f(spin, 1f, 1f, 0f)),
                new Vector3f(0.7f, 0.7f, 0.7f),
                new Quaternionf()
        );
        block.setInterpolationDelay(0);
        block.setInterpolationDuration(2);
        block.setTransformation(transform);
        block.teleport(at);

        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getEyeLocation().distance(at) <= THROW_HIT_RADIUS) {
                block.remove();
                player.damage(THROW_DAMAGE, giant);
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_HURT, 1.0f, 0.8f);
                Vector knockback = player.getLocation().toVector().subtract(at.toVector());
                if (knockback.lengthSquared() > 0) {
                    knockback.normalize().multiply(0.6).setY(0.3);
                    player.setVelocity(player.getVelocity().add(knockback));
                }
                world.spawnParticle(Particle.BLOCK, at, 15, 0.2, 0.2, 0.2, Material.COBBLESTONE.createBlockData());
                return;
            }
        }

        if (velocity.lengthSquared() > 0) {
            RayTraceResult hit = world.rayTraceBlocks(at, velocity.clone().normalize(), 0.5, FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitBlock() != null) {
                world.spawnParticle(Particle.BLOCK, at, 15, 0.2, 0.2, 0.2, Material.COBBLESTONE.createBlockData());
                world.playSound(at, Sound.BLOCK_STONE_BREAK, 0.8f, 0.9f);
                block.remove();
                return;
            }
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animateThrow(giant, block, position, velocity, start), 1L);
    }

    /**
     * Rozchodząca się fala do WAVE_MAX_RADIUS (40 bloków) - wyrzuca w powietrze i zadaje
     * obrażenia każdemu graczowi, którego "czoło fali" dosięgnie, CHYBA że gracz akurat jest w
     * powietrzu (skoczył) - unik przez wyskoczenie w porę.
     */
    private boolean tryWaveAttack(Giant giant, TrackedGiant tg, long now) {
        if (now - tg.lastWaveAt < cooldown(WAVE_COOLDOWN_MS, tg)) {
            return false;
        }
        if (nearestPlayer(giant, WAVE_TRIGGER_RANGE) == null) {
            return false;
        }
        tg.lastWaveAt = now;
        tg.busyUntil = now + WAVE_EXPAND_TICKS * 50L + ATTACK_REST_MS;
        animateWave(giant, giant.getLocation(), 0L, new HashSet<>());
        return true;
    }

    private void animateWave(Giant giant, Location center, long tick, Set<UUID> hitSoFar) {
        if (!giant.isValid() || giant.isDead()) {
            return;
        }
        World world = center.getWorld();
        double radius = WAVE_MAX_RADIUS * (tick / (double) WAVE_EXPAND_TICKS);
        for (int i = 0; i < WAVE_PARTICLE_POINTS; i++) {
            double angle = 2 * Math.PI * i / WAVE_PARTICLE_POINTS;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            world.spawnParticle(Particle.CRIT, x, center.getY() + 0.1, z, 1, 0, 0, 0, 0);
        }

        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (hitSoFar.contains(player.getUniqueId())) {
                continue;
            }
            double dist = player.getLocation().distance(center);
            if (Math.abs(dist - radius) > WAVE_BAND) {
                continue;
            }
            if (!player.isOnGround()) {
                continue; // gracz w powietrzu - unik, fala go nie dosięga
            }
            hitSoFar.add(player.getUniqueId());
            player.damage(WAVE_DAMAGE, giant);
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_HURT, 1.0f, 0.6f);
            player.setVelocity(player.getVelocity().setY(WAVE_LAUNCH_VELOCITY));
        }

        if (tick >= WAVE_EXPAND_TICKS) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animateWave(giant, center, tick + 1, hitSoFar), 1L);
    }

    /** Co BOMBARD_INTERVAL_MS (10s), jeśli ktoś jest blisko - kolce spadają z nieba niezależnie od innych ataków. */
    private boolean tryBombardAttack(Giant giant, TrackedGiant tg, long now) {
        if (now - tg.lastBombardAt < cooldown(BOMBARD_INTERVAL_MS, tg)) {
            return false;
        }
        if (nearestPlayer(giant, BOMBARD_RADIUS) == null) {
            return false;
        }
        tg.lastBombardAt = now;
        tg.busyUntil = now + BOMBARD_TELEGRAPH_TICKS * 50L + BOMBARD_FALL_DURATION_MS + ATTACK_REST_MS;
        startBombardment(giant, giant.getLocation());
        return true;
    }

    /**
     * Losuje BOMBARD_COUNT miejsc w promieniu do BOMBARD_RADIUS od danego środka, telegrafuje
     * każde (kolumna cząsteczek), a potem zrzuca na nie kolec z góry - każdy punkt jest niezależny.
     */
    private void startBombardment(Giant giant, Location center) {
        World world = center.getWorld();
        world.playSound(center, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 1.4f);
        for (int i = 0; i < BOMBARD_COUNT; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = random.nextDouble() * BOMBARD_RADIUS;
            Location point = center.clone().add(dist * Math.cos(angle), 0, dist * Math.sin(angle));
            telegraphBombard(giant, point, 0L);
        }
    }

    private void telegraphBombard(Giant giant, Location point, long tick) {
        if (!giant.isValid() || giant.isDead()) {
            return;
        }
        World world = point.getWorld();
        world.spawnParticle(Particle.FLAME, point.clone().add(0, 0.2, 0), 3, 0.2, 0.1, 0.2, 0.01);
        if (tick == 0) {
            world.playSound(point, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.6f);
        }

        if (tick >= BOMBARD_TELEGRAPH_TICKS) {
            dropBombardSpike(giant, point, System.currentTimeMillis());
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> telegraphBombard(giant, point, tick + 1), 1L);
    }

    private void dropBombardSpike(Giant giant, Location point, long start) {
        World world = point.getWorld();
        ItemDisplay spike = world.spawn(point.clone().add(0, BOMBARD_FALL_HEIGHT, 0), ItemDisplay.class, e -> {
            e.setBillboard(Display.Billboard.FIXED);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setItemStack(new ItemStack(Material.POINTED_DRIPSTONE));
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, "bombard");
            e.addScoreboardTag(TAG);
        });
        animateBombardFall(giant, spike, point, start);
    }

    private void animateBombardFall(Giant giant, ItemDisplay spike, Location landAt, long start) {
        if (!spike.isValid()) {
            return;
        }
        double t = Math.min(1.0, (System.currentTimeMillis() - start) / (double) BOMBARD_FALL_DURATION_MS);
        double heightOffset = (1.0 - t) * BOMBARD_FALL_HEIGHT;
        spike.teleport(landAt.clone().add(0, heightOffset, 0));

        if (t >= 1.0) {
            World world = landAt.getWorld();
            world.spawnParticle(Particle.EXPLOSION, landAt, 1);
            world.playSound(landAt, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
            for (Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                if (player.getLocation().distance(landAt) <= BOMBARD_HIT_RADIUS) {
                    player.damage(BOMBARD_DAMAGE, giant);
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_HURT, 1.0f, 0.7f);
                    Vector knockback = player.getLocation().toVector().subtract(landAt.toVector());
                    if (knockback.lengthSquared() > 0) {
                        knockback.normalize().multiply(0.5).setY(0.3);
                        player.setVelocity(player.getVelocity().add(knockback));
                    }
                }
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (spike.isValid()) {
                    spike.remove();
                }
            }, 20L);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animateBombardFall(giant, spike, landAt, start), 1L);
    }

    /**
     * Szarża - po krótkim telegrafie (stoi, ryczy) Giant pędzi po ziemi w stronę celu i zbija
     * każdego gracza na drodze. Tylko gdy cel jest daleko (CHARGE_MIN_RANGE..CHARGE_TRIGGER_RANGE) -
     * na krótki dystans to zwykły pościg wystarczy.
     */
    private boolean tryChargeAttack(Giant giant, TrackedGiant tg, long now) {
        if (now - tg.lastChargeAt < cooldown(CHARGE_COOLDOWN_MS, tg)) {
            return false;
        }
        Player target = nearestPlayer(giant, CHARGE_TRIGGER_RANGE);
        if (target == null || target.getLocation().distance(giant.getLocation()) < CHARGE_MIN_RANGE) {
            return false;
        }
        tg.lastChargeAt = now;
        tg.busyUntil = now + CHARGE_WINDUP_TICKS * 50L + CHARGE_DURATION_TICKS * 50L + ATTACK_REST_MS;
        World world = giant.getWorld();
        world.spawnParticle(Particle.CRIT, giant.getLocation().add(0, 1, 0), 10, 0.4, 0.6, 0.4, 0);
        world.playSound(giant.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.6f);
        telegraphCharge(giant, 0L);
        return true;
    }

    private void telegraphCharge(Giant giant, long tick) {
        if (!giant.isValid() || giant.isDead()) {
            return;
        }
        giant.getWorld().spawnParticle(Particle.CRIT, giant.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0);
        if (tick >= CHARGE_WINDUP_TICKS) {
            Player target = nearestPlayer(giant, CHARGE_TRIGGER_RANGE * 2);
            Vector direction = target != null
                    ? target.getLocation().toVector().subtract(giant.getLocation().toVector())
                    : giant.getLocation().getDirection();
            direction.setY(0);
            if (direction.lengthSquared() == 0) {
                return;
            }
            giant.getWorld().playSound(giant.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1.5f, 0.6f);
            executeCharge(giant, direction.normalize(), new HashSet<>(), 0L);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> telegraphCharge(giant, tick + 1), 1L);
    }

    private void executeCharge(Giant giant, Vector direction, Set<UUID> hitDuring, long tick) {
        if (!giant.isValid() || giant.isDead() || tick >= CHARGE_DURATION_TICKS) {
            return;
        }
        Vector push = direction.clone().multiply(CHARGE_SPEED);
        giant.setVelocity(new Vector(push.getX(), giant.getVelocity().getY(), push.getZ()));
        World world = giant.getWorld();
        world.spawnParticle(Particle.CLOUD, giant.getLocation(), 6, 0.4, 0.1, 0.4, 0.01);

        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (hitDuring.contains(player.getUniqueId())) {
                continue;
            }
            if (player.getLocation().distance(giant.getLocation()) > CHARGE_HIT_RADIUS) {
                continue;
            }
            hitDuring.add(player.getUniqueId());
            player.damage(CHARGE_DAMAGE, giant);
            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_HURT, 1.0f, 0.6f);
            Vector knockback = player.getLocation().toVector().subtract(giant.getLocation().toVector());
            if (knockback.lengthSquared() > 0) {
                knockback.normalize().multiply(CHARGE_KNOCKBACK).setY(0.4);
                player.setVelocity(player.getVelocity().add(knockback));
            }
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> executeCharge(giant, direction, hitDuring, tick + 1), 1L);
    }

    /** Ogłuszający ryk - krótkozasięgowe AoE, spowalnia i oślepia graczy blisko Gianta. */
    private boolean tryRoarAttack(Giant giant, TrackedGiant tg, long now) {
        if (now - tg.lastRoarAt < cooldown(ROAR_COOLDOWN_MS, tg)) {
            return false;
        }
        if (nearestPlayer(giant, ROAR_RANGE) == null) {
            return false;
        }
        tg.lastRoarAt = now;
        tg.busyUntil = now + 600L + ATTACK_REST_MS;

        World world = giant.getWorld();
        Location center = giant.getLocation();
        world.playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.2f, 1.3f);
        for (int i = 0; i < WAVE_PARTICLE_POINTS / 2; i++) {
            double angle = 2 * Math.PI * i / (WAVE_PARTICLE_POINTS / 2);
            world.spawnParticle(Particle.CLOUD, center.getX() + ROAR_RANGE * Math.cos(angle), center.getY() + 1,
                    center.getZ() + ROAR_RANGE * Math.sin(angle), 1, 0, 0, 0, 0);
        }

        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (player.getLocation().distance(center) > ROAR_RANGE) {
                continue;
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ROAR_SLOWNESS_TICKS, 1));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, ROAR_BLINDNESS_TICKS, 0));
        }
        return true;
    }

    /** Przywołuje 2-3 zwykłe zombie jako wsparcie - normalna wanilijna AI, tylko czasowe. */
    private boolean trySummonAttack(Giant giant, TrackedGiant tg, long now) {
        if (now - tg.lastSummonAt < cooldown(SUMMON_COOLDOWN_MS, tg)) {
            return false;
        }
        if (nearestPlayer(giant, DETECT_RANGE) == null) {
            return false;
        }
        tg.lastSummonAt = now;
        tg.busyUntil = now + SUMMON_ACTIVE_MS + ATTACK_REST_MS;

        World world = giant.getWorld();
        world.playSound(giant.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 0.6f);
        world.spawnParticle(Particle.CLOUD, giant.getLocation().add(0, 1, 0), 40, 1, 1, 1, 0.05);

        int count = 2 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            Location spawnAt = giant.getLocation().clone().add(Math.cos(angle) * 2.0, 0, Math.sin(angle) * 2.0);
            Zombie minion = world.spawn(spawnAt, Zombie.class, z -> {
                z.setCustomName("§7Sługa Mega-Zombie");
                z.setCustomNameVisible(true);
                z.setPersistent(false);
                z.addScoreboardTag(MINION_TAG);
            });
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (minion.isValid()) {
                    minion.remove();
                }
            }, MINION_LIFETIME_TICKS);
        }
        return true;
    }

    public void onKilled(Giant giant) {
        TrackedGiant tg = tracked.remove(giant.getUniqueId());
        if (tg == null) {
            return;
        }
        eventActive = false;
        if (tg.lifetimeTask != null) {
            tg.lifetimeTask.cancel();
        }
        tg.healthBar.removeAll();

        Player killer = giant.getKiller();
        if (killer != null) {
            plugin.getStats().recordKill(killer.getUniqueId(), killer.getName());
            plugin.getKillstreaks().onKill(killer);
            giveReward(killer);
            Bukkit.broadcastMessage(Branding.chatPrefix() + "§a" + killer.getName() + " §7zabił(a) "
                    + Branding.accent("☠ Mega-Zombie") + "§7!");
        }
    }

    private void cleanupTracked(UUID uuid) {
        TrackedGiant tg = tracked.remove(uuid);
        if (tg == null) {
            return;
        }
        eventActive = false;
        if (tg.lifetimeTask != null) {
            tg.lifetimeTask.cancel();
        }
        tg.healthBar.removeAll();
    }

    private void despawnByTimeout(UUID uuid) {
        TrackedGiant tg = tracked.remove(uuid);
        if (tg == null) {
            return;
        }
        eventActive = false;
        if (tg.giant != null && tg.giant.isValid()) {
            tg.giant.remove();
        }
        tg.healthBar.removeAll();
        Bukkit.broadcastMessage(Branding.chatPrefix() + "§7☠ Mega-Zombie zniknął (czas minął).");
    }

    private void giveReward(Player player) {
        if (rewardPool.isEmpty()) {
            return;
        }
        ItemStack reward = rewardPool.get(random.nextInt(rewardPool.size())).clone();
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), item);
        }
        player.sendMessage("§aOtrzymujesz nagrodę za zabicie mega-zombie!");
    }

    private void loadRewards() {
        List<?> raw = data.getList("rewards");
        if (raw == null) {
            return;
        }
        for (Object obj : raw) {
            if (obj instanceof ItemStack) {
                rewardPool.add((ItemStack) obj);
            }
        }
    }

    private void saveRewards() {
        data.set("rewards", rewardPool);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać giantevent.yml: " + e.getMessage());
        }
    }

    private static final class TrackedGiant {
        final Giant giant;
        final Location safeAnchor;
        final BossBar healthBar;
        long lastAttackAt;
        long lastWaveAt;
        long lastBombardAt;
        long lastChargeAt;
        long lastRoarAt;
        long lastSummonAt;
        /** Do tego momentu Giant stoi w miejscu i nie zaczyna kolejnego ataku (atak + przerwa po nim). */
        long busyUntil;
        /** Gracz namierzony na początku zamachu do rzutu - rzut leci w NIEGO, nie w kogoś, kto akurat podszedł bliżej w trakcie zamachu. */
        UUID throwTargetUuid;
        boolean raging;
        BukkitTask lifetimeTask;

        TrackedGiant(Giant giant, Location safeAnchor, BossBar healthBar) {
            this.giant = giant;
            this.safeAnchor = safeAnchor;
            this.healthBar = healthBar;
        }
    }
}
