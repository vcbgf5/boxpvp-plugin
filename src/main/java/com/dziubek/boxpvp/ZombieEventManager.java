package com.dziubek.boxpvp;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Bukkit;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * "Zombiak-event" - silny zombie spadający z nieba w wyznaczonym obszarze eventów (ten sam co
 * skrzynki-event, patrz EventManager). Nie odnosi obrażeń od upadku ani nie płonie w słońcu,
 * nie może wejść na region Spawn01 (tak jak gracz w walce), atakuje kontrolowanym tempem
 * (10 dmg co 10s, zamiast wanilijnego) i znika sam po 5 minutach, jeśli nikt go nie zabije.
 * Zabity przez gracza - oddaje nagrodę z własnej puli.
 */
public class ZombieEventManager {

    private static final String TAG = "bpvp_event_zombie";
    private static final double MAX_HEALTH = 60.0;
    private static final double ATTACK_DAMAGE = 10.0;
    private static final long ATTACK_COOLDOWN_MS = 10_000L;
    private static final double ATTACK_RANGE = 2.2;
    private static final double FALL_START_OFFSET = 14.0;
    private static final long LIFETIME_TICKS = 20L * 60 * 5;
    private static final long TICK_INTERVAL = 10L;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey ownerTag;
    private final List<ItemStack> rewardPool = new ArrayList<>();
    private final Random random = new Random();
    private final Map<UUID, TrackedZombie> tracked = new HashMap<>();

    public ZombieEventManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "zombieevent.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć zombieevent.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.ownerTag = new NamespacedKey(plugin, "bpvp_event_zombie_owner");
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

    /** Usuwa "osierocone" zombie-eventy sprzed restartu (nie ma ich w świeżej mapie tracked). */
    public void purgeOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Zombie.class)) {
                if (entity.getScoreboardTags().contains(TAG) && !tracked.containsKey(entity.getUniqueId())) {
                    entity.remove();
                }
            }
        }
    }

    public void spawnZombie(Location landAt) {
        World world = landAt.getWorld();
        if (world == null) {
            return;
        }
        Location groundAnchor = landAt.clone().add(0.5, 0, 0.5);
        Location spawnAt = groundAnchor.clone().add(0, FALL_START_OFFSET, 0);

        Zombie zombie = world.spawn(spawnAt, Zombie.class, z -> {
            z.setPersistent(false);
            z.setRemoveWhenFarAway(false);
            z.setCustomName(Branding.accent("☠ Zombie-Event"));
            z.setCustomNameVisible(true);
            AttributeInstance maxHealthAttr = z.getAttribute(Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                maxHealthAttr.setBaseValue(MAX_HEALTH);
            }
            z.setHealth(MAX_HEALTH);
            z.getPersistentDataContainer().set(ownerTag, PersistentDataType.BYTE, (byte) 1);
            z.addScoreboardTag(TAG);
            equipRandomGear(z);
        });

        TrackedZombie tz = new TrackedZombie(zombie, groundAnchor);
        tz.lifetimeTask = plugin.getServer().getScheduler()
                .runTaskLater(plugin, () -> despawnByTimeout(zombie.getUniqueId()), LIFETIME_TICKS);
        tracked.put(zombie.getUniqueId(), tz);
        startTicking(tz);

        Bukkit.broadcastMessage(Branding.chatPrefix() + "§c§l☠ Silny zombie §7spadł z nieba w obszarze eventów!");
        BossBarUtil.showTimed(plugin, "§c§l☠ ZOMBIE-EVENT! §7Zabij go, zanim ucieknie!", BarColor.RED, 15L * 20L);
        world.playSound(groundAnchor, Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.6f);
    }

    private static final Material[][] ARMOR_TIERS = {
            {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS},
            {Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS},
            {Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}
    };
    private static final Material[] WEAPONS = {Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD,
            Material.IRON_AXE, Material.DIAMOND_AXE};

    /**
     * Bukkit nie ekwipuje programowo zespawnowanych moby (w przeciwieniu do naturalnego spawnu
     * wanilijnego) - trzeba ręcznie dać losową zbroję (jedną z 3 tierów) i broń, z zerowym
     * dropchance, żeby po śmierci nic z tego nie wypadło.
     */
    private void equipRandomGear(Zombie zombie) {
        EntityEquipment equipment = zombie.getEquipment();
        if (equipment == null) {
            return;
        }
        Material[] tier = ARMOR_TIERS[random.nextInt(ARMOR_TIERS.length)];
        equipment.setHelmet(new ItemStack(tier[0]));
        equipment.setChestplate(new ItemStack(tier[1]));
        equipment.setLeggings(new ItemStack(tier[2]));
        equipment.setBoots(new ItemStack(tier[3]));
        equipment.setItemInMainHand(new ItemStack(WEAPONS[random.nextInt(WEAPONS.length)]));

        equipment.setHelmetDropChance(0f);
        equipment.setChestplateDropChance(0f);
        equipment.setLeggingsDropChance(0f);
        equipment.setBootsDropChance(0f);
        equipment.setItemInMainHandDropChance(0f);
    }

    private void startTicking(TrackedZombie tz) {
        new BukkitRunnable() {
            @Override
            public void run() {
                Zombie zombie = tz.zombie;
                if (zombie == null || !zombie.isValid() || zombie.isDead()) {
                    tracked.remove(zombie != null ? zombie.getUniqueId() : null);
                    cancel();
                    return;
                }
                enforceSpawnBoundary(zombie, tz);
                tryMeleeAttack(zombie, tz);
                if (!zombie.isOnGround()) {
                    zombie.getWorld().spawnParticle(Particle.FLAME, zombie.getLocation().add(0, 1, 0), 3, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }.runTaskTimer(plugin, TICK_INTERVAL, TICK_INTERVAL);
    }

    /**
     * Nie pozwala zombie-eventowi wejść na region Spawn01 - dokładnie tak jak gracz w walce
     * (SpawnRegionGuardListener). Bez WorldGuard po prostu nic nie robi.
     */
    private void enforceSpawnBoundary(Zombie zombie, TrackedZombie tz) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        Location loc = zombie.getLocation();
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
            zombie.teleport(tz.safeAnchor);
        }
    }

    /**
     * Kontrolowany atak - 10 obrażeń co 10 sekund na najbliższego gracza w zasięgu, niezależnie
     * od wanilijnego tempa ataku zombie (które jest anulowane w ZombieEventListener).
     */
    private void tryMeleeAttack(Zombie zombie, TrackedZombie tz) {
        long now = System.currentTimeMillis();
        if (now - tz.lastAttackAt < ATTACK_COOLDOWN_MS) {
            return;
        }

        Player nearest = null;
        double nearestDist = ATTACK_RANGE;
        for (Entity entity : zombie.getNearbyEntities(ATTACK_RANGE, ATTACK_RANGE, ATTACK_RANGE)) {
            if (!(entity instanceof Player)) {
                continue;
            }
            Player candidate = (Player) entity;
            if (candidate.getGameMode() == GameMode.CREATIVE || candidate.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            double dist = candidate.getLocation().distance(zombie.getLocation());
            if (dist <= nearestDist) {
                nearest = candidate;
                nearestDist = dist;
            }
        }
        if (nearest == null) {
            return;
        }

        tz.lastAttackAt = now;
        nearest.setHealth(Math.max(0.0, nearest.getHealth() - ATTACK_DAMAGE));
        nearest.playSound(nearest.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.8f);

        Vector knockback = nearest.getLocation().toVector().subtract(zombie.getLocation().toVector());
        if (knockback.lengthSquared() > 0) {
            knockback.normalize().multiply(0.4).setY(0.25);
            nearest.setVelocity(nearest.getVelocity().add(knockback));
        }
    }

    public void onKilled(Zombie zombie) {
        TrackedZombie tz = tracked.remove(zombie.getUniqueId());
        if (tz != null && tz.lifetimeTask != null) {
            tz.lifetimeTask.cancel();
        }
        Player killer = zombie.getKiller();
        if (killer != null) {
            giveReward(killer);
            Bukkit.broadcastMessage(Branding.chatPrefix() + "§a" + killer.getName() + " §7zabił(a) "
                    + Branding.accent("☠ Zombie-Event") + "§7!");
        }
    }

    private void despawnByTimeout(UUID uuid) {
        TrackedZombie tz = tracked.remove(uuid);
        if (tz == null) {
            return;
        }
        if (tz.zombie != null && tz.zombie.isValid()) {
            tz.zombie.remove();
        }
        Bukkit.broadcastMessage(Branding.chatPrefix() + "§7☠ Silny zombie zniknął (czas minął).");
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
        player.sendMessage("§aOtrzymujesz nagrodę za zabicie zombie-eventu!");
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
            plugin.getLogger().warning("Nie udało się zapisać zombieevent.yml: " + e.getMessage());
        }
    }

    private static final class TrackedZombie {
        final Zombie zombie;
        final Location safeAnchor;
        long lastAttackAt;
        BukkitTask lifetimeTask;

        TrackedZombie(Zombie zombie, Location safeAnchor) {
            this.zombie = zombie;
            this.safeAnchor = safeAnchor;
        }
    }
}
