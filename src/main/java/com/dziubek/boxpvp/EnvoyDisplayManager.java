package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * "Skrzynka z nieba" (envoy) - beczka (ItemDisplay) spadająca z góry na losowe miejsce
 * w wyznaczonym obszarze. Gdy dotknie ziemi, zatrzymuje się i stoi na niej nieruchomo (jak
 * postawiony blok) - dopiero wtedy można ją otworzyć PPM, co oddaje losowe przedmioty z puli.
 */
public class EnvoyDisplayManager {

    private static final String TAG = "bpvp_envoy";
    private static final double FALL_START_OFFSET = 30.0;
    private static final long FALL_DURATION_MS = 3000;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey ownerTag;
    private final List<ItemStack> rewardPool = new ArrayList<>();
    private final Random random = new Random();

    private ItemDisplay activeCrate;
    private boolean landed;

    public EnvoyDisplayManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "envoy.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć envoy.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.ownerTag = new NamespacedKey(plugin, "bpvp_envoy_owner");
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

    /**
     * Usuwa "osierocone" encje eventu sprzed restartu - tą, którą ten manager aktualnie
     * żywo zarządza (trwająca animacja/leżąca skrzynka), zostaje nietknięta.
     */
    public void purgeOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!entity.getScoreboardTags().contains(TAG) || entity.equals(activeCrate)) {
                    continue;
                }
                entity.remove();
            }
        }
    }

    public void spawnFallingCrate(Location landAt) {
        if (activeCrate != null && activeCrate.isValid()) {
            return; // już leci/leży jedna skrzynka - nie duplikuj
        }
        World world = landAt.getWorld();
        landed = false;
        Location groundAnchor = landAt.clone().add(0.5, 0, 0.5);
        Location spawnAt = groundAnchor.clone().add(0, FALL_START_OFFSET, 0);

        activeCrate = world.spawn(spawnAt, ItemDisplay.class, e -> {
            e.setBillboard(Display.Billboard.FIXED);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setItemStack(new ItemStack(Material.BARREL));
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, "crate");
            e.addScoreboardTag(TAG);
        });

        Bukkit.broadcastMessage("§c§l☁ Skrzynka-event §7spada z nieba! Znajdź ją zanim wyląduje!");
        world.playSound(groundAnchor, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.6f);

        animateFall(groundAnchor, System.currentTimeMillis());
    }

    private void animateFall(Location groundAnchor, long start) {
        if (activeCrate == null || !activeCrate.isValid()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - start;
        double t = Math.min(1.0, elapsed / (double) FALL_DURATION_MS);
        double eased = CameraUtil.easeOutCubic(t);
        double heightOffset = (1.0 - eased) * FALL_START_OFFSET;

        Location crateAt = groundAnchor.clone().add(0, heightOffset, 0);
        float spin = (float) ((System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI * 2);
        applyTransform(activeCrate, spin, 1.0f);
        activeCrate.teleport(crateAt);

        if (t >= 1.0) {
            onLanded(groundAnchor);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animateFall(groundAnchor, start), 1L);
    }

    private void applyTransform(ItemDisplay display, float angle, float scale) {
        Transformation transform = new Transformation(
                new Vector3f(0f, 0f, 0f),
                new Quaternionf(new AxisAngle4f(angle, 0f, 1f, 0f)),
                new Vector3f(scale, scale, scale),
                new Quaternionf()
        );
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(2);
        display.setTransformation(transform);
    }

    /**
     * Skrzynka dotyka ziemi i zostaje w tym miejscu nieruchomo - "stawia się" na ziemi jak
     * postawiony blok, bez dalszego bujania/kręcenia się.
     */
    private void onLanded(Location groundAnchor) {
        landed = true;
        activeCrate.teleport(groundAnchor);
        applyTransform(activeCrate, 0f, 1.0f);

        World world = groundAnchor.getWorld();
        world.spawnParticle(Particle.EXPLOSION, groundAnchor, 1);
        world.spawnParticle(Particle.CLOUD, groundAnchor, 40, 0.6, 0.3, 0.6, 0.05);
        world.playSound(groundAnchor, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
        Bukkit.broadcastMessage("§c§l☁ Skrzynka-event §7wylądowała! Kliknij ją PPM, żeby otworzyć.");
    }

    /**
     * Wywoływane przez EnvoyListener przy PPM na dowolnej encji - zwraca true, jeśli to była
     * ta skrzynka i kliknięcie zostało obsłużone (event powinien zostać anulowany).
     */
    public boolean tryOpen(Player player, Entity clicked) {
        if (!landed || activeCrate == null || !clicked.equals(activeCrate)) {
            return false;
        }
        openFor(player);
        return true;
    }

    private void openFor(Player player) {
        if (rewardPool.isEmpty()) {
            player.sendMessage("§cSkrzynka-event jest pusta (admin nie dodał jeszcze nagród: /bpvp event envoyitem add).");
            return;
        }
        int count = 1 + random.nextInt(3);
        ItemStack lastReward = null;
        for (int i = 0; i < count; i++) {
            ItemStack reward = rewardPool.get(random.nextInt(rewardPool.size())).clone();
            lastReward = reward;
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), item);
            }
        }
        player.sendMessage("§a§lOtworzyłeś skrzynkę-event!");
        if (lastReward != null) {
            RewardRevealEffect.playLight(plugin, player, lastReward);
        }
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.4, 0.5, 0.4, 0.3);
        despawn();
    }

    private void despawn() {
        if (activeCrate != null && activeCrate.isValid()) {
            activeCrate.remove();
        }
        activeCrate = null;
        landed = false;
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
            plugin.getLogger().warning("Nie udało się zapisać envoy.yml: " + e.getMessage());
        }
    }
}
