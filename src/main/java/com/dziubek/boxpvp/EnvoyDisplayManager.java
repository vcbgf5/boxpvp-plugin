package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
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
 * "Skrzynka z nieba" (envoy) - 10 sekund przed spadnięciem w danym miejscu pojawia się
 * ostrzeżenie (unosząca się strzałka + biały snop cząsteczek jak z beacona), a dopiero potem
 * spada beczka (ItemDisplay). Po dotknięciu ziemi zatrzymuje się nieruchomo i czeka na PPM -
 * przez niewidzialną encję Interaction, bo sam ItemDisplay nie ma hitboksu. Może lecieć kilka
 * naraz (np. 2 dropy co 10 minut - patrz EventManager). Rzadsza "mega" wersja (flaga `mega`
 * przewleczona przez cały pipeline) jest większa, ma fioletowe efekty i osobną, bogatszą pulę
 * nagród.
 */
public class EnvoyDisplayManager {

    private static final String TAG = "bpvp_envoy";
    private static final double FALL_START_OFFSET = 100.0;
    private static final long FALL_DURATION_MS = 10_000L;
    private static final long WARNING_TICKS = 20L * 10;
    private static final double WARNING_HEIGHT = 2.0;
    private static final double BEACON_BEAM_HEIGHT = 14.0;
    private static final double LABEL_HEIGHT_OFFSET = 1.9;
    private static final double GROUND_CHECK_DISTANCE = 2.0;
    private static final float NORMAL_SCALE = 1.0f;
    private static final float MEGA_SCALE = 1.6f;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey ownerTag;
    private final List<ItemStack> rewardPool = new ArrayList<>();
    private final List<ItemStack> megaRewardPool = new ArrayList<>();
    private final Random random = new Random();
    private final List<ActiveDrop> activeDrops = new ArrayList<>();

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
        loadPool("rewards", rewardPool);
        loadPool("mega-rewards", megaRewardPool);
    }

    public void addReward(ItemStack item) {
        rewardPool.add(item.clone());
        savePool("rewards", rewardPool);
    }

    public void clearRewards() {
        rewardPool.clear();
        savePool("rewards", rewardPool);
    }

    public List<ItemStack> rewards() {
        return rewardPool;
    }

    public void addMegaReward(ItemStack item) {
        megaRewardPool.add(item.clone());
        savePool("mega-rewards", megaRewardPool);
    }

    public void clearMegaRewards() {
        megaRewardPool.clear();
        savePool("mega-rewards", megaRewardPool);
    }

    public List<ItemStack> megaRewards() {
        return megaRewardPool;
    }

    /**
     * Usuwa "osierocone" encje eventu sprzed restartu - te, którymi ten manager aktualnie
     * żywo zarządza (leżące/spadające skrzynki), zostają nietknięte. Ostrzegawcza strzałka jest
     * krótkotrwała (10s) i celowo NIE jest tu chroniona - po restarcie po prostu znika.
     */
    public void purgeOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!entity.getScoreboardTags().contains(TAG) || isTracked(entity)) {
                    continue;
                }
                entity.remove();
            }
            for (Entity entity : world.getEntitiesByClass(Interaction.class)) {
                if (!entity.getScoreboardTags().contains(TAG) || isTracked(entity)) {
                    continue;
                }
                entity.remove();
            }
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (!entity.getScoreboardTags().contains(TAG) || isTracked(entity)) {
                    continue;
                }
                entity.remove();
            }
        }
    }

    private boolean isTracked(Entity entity) {
        for (ActiveDrop drop : activeDrops) {
            if (entity.equals(drop.crate) || entity.equals(drop.hitbox) || entity.equals(drop.label)) {
                return true;
            }
        }
        return false;
    }

    public void scheduleDrop(Location landAt) {
        scheduleDrop(landAt, false);
    }

    /**
     * Ostrzega 10 sekund wcześniej o miejscu lądowania (unosząca się strzałka + biały snop
     * cząsteczek jak z beacona), a dopiero potem uruchamia spadanie beczki w tym miejscu.
     */
    public void scheduleDrop(Location landAt, boolean mega) {
        World world = landAt.getWorld();
        if (world == null) {
            return;
        }
        Location groundAnchor = landAt.clone().add(0.5, 0, 0.5);

        ItemDisplay arrow = world.spawn(groundAnchor.clone().add(0, WARNING_HEIGHT, 0), ItemDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setItemStack(new ItemStack(Material.SPECTRAL_ARROW));
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, "warning");
            e.addScoreboardTag(TAG);
        });

        String label = mega ? Branding.accent("★ MEGA skrzynka-event!") : "§f§l☀ Skrzynka-event";
        Bukkit.broadcastMessage(Branding.chatPrefix() + label + " §7spadnie tutaj za 10 sekund!");
        BossBarUtil.showTimed(plugin, label + " §7ląduje...", mega ? BarColor.PURPLE : BarColor.WHITE, WARNING_TICKS);
        world.playSound(groundAnchor, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, mega ? 0.8f : 1.4f);

        animateWarning(groundAnchor, arrow, System.currentTimeMillis(), WARNING_TICKS, mega);
    }

    /**
     * Biały snop cząsteczek w miejscu ostrzeżenia (jak beam z beacona) + wirująca/bujająca się
     * strzałka nad ziemią, licząc w dół do momentu spadnięcia beczki.
     */
    private void animateWarning(Location groundAnchor, ItemDisplay arrow, long start, long ticksLeft, boolean mega) {
        if (!arrow.isValid()) {
            return;
        }
        long elapsed = System.currentTimeMillis() - start;
        float angle = (float) ((elapsed % 1200L) / 1200.0 * Math.PI * 2);
        float bob = (float) (Math.sin(elapsed / 250.0) * 0.15);

        Transformation transform = new Transformation(
                new Vector3f(0f, bob, 0f),
                new Quaternionf(new AxisAngle4f(angle, 0f, 1f, 0f)),
                new Vector3f(1.4f, 1.4f, 1.4f),
                new Quaternionf()
        );
        arrow.setInterpolationDelay(0);
        arrow.setInterpolationDuration(2);
        arrow.setTransformation(transform);

        World world = groundAnchor.getWorld();
        for (double y = 0; y < BEACON_BEAM_HEIGHT; y += 0.25) {
            if (mega) {
                world.spawnParticle(Particle.DUST, groundAnchor.getX(), groundAnchor.getY() + y, groundAnchor.getZ(), 3, 0.05, 0, 0.05, 0,
                        new Particle.DustOptions(Color.fromRGB(Branding.LIGHT_LAVENDER), 1.6f));
            } else {
                world.spawnParticle(Particle.END_ROD, groundAnchor.getX(), groundAnchor.getY() + y, groundAnchor.getZ(), 3, 0.05, 0, 0.05, 0.01);
            }
        }

        if (ticksLeft <= 0) {
            arrow.remove();
            beginFall(groundAnchor, mega);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animateWarning(groundAnchor, arrow, start, ticksLeft - 1, mega), 1L);
    }

    private void beginFall(Location groundAnchor, boolean mega) {
        World world = groundAnchor.getWorld();
        Location spawnAt = groundAnchor.clone().add(0, FALL_START_OFFSET, 0);

        ActiveDrop drop = new ActiveDrop();
        drop.mega = mega;
        drop.crate = world.spawn(spawnAt, ItemDisplay.class, e -> {
            e.setBillboard(Display.Billboard.FIXED);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setItemStack(new ItemStack(mega ? Material.SHULKER_BOX : Material.BARREL));
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, "crate");
            e.addScoreboardTag(TAG);
        });

        String labelText = mega ? Branding.accent("★ MEGA Skrzynka-event") : "§c§lSkrzynka-event";
        drop.label = world.spawn(spawnAt.clone().add(0, LABEL_HEIGHT_OFFSET, 0), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setText(labelText);
            e.setSeeThrough(false);
            e.setShadowed(false);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, "label");
            e.addScoreboardTag(TAG);
        });
        activeDrops.add(drop);

        String label = mega ? Branding.accent("★ MEGA skrzynka-event") : "§c§l☁ Skrzynka-event";
        Bukkit.broadcastMessage(Branding.chatPrefix() + label + " §7spada z nieba!");
        world.playSound(groundAnchor, mega ? Sound.ENTITY_WITHER_AMBIENT : Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 0.6f);

        animateFall(drop, groundAnchor, System.currentTimeMillis());
    }

    /**
     * Czysto czasowa animacja (ease-out do z góry wyliczonej "podłogi" strefy), jak zawsze -
     * ale co klatkę dorzuca krótki raycast prosto pod skrzynkę (max GROUND_CHECK_DISTANCE
     * bloków), żeby wylądować od razu, gdy naprawdę jest blisko bloku pod sobą, zamiast czekać
     * na koniec animacji i ewentualnie wjechać w nierówny teren. W przeciwieństwie do
     * wcześniejszego podejścia (porównanie z World#getHighestBlockYAt w danej kolumnie X/Z)
     * raycast patrzy tylko na to, co jest TERAZ dosłownie pod skrzynką, więc nie może wylądować
     * przedwcześnie na 1. klatce spadania z wysoka.
     */
    private void animateFall(ActiveDrop drop, Location groundAnchor, long start) {
        if (drop.crate == null || !drop.crate.isValid()) {
            activeDrops.remove(drop);
            return;
        }
        long elapsed = System.currentTimeMillis() - start;
        double t = Math.min(1.0, elapsed / (double) FALL_DURATION_MS);
        double eased = CameraUtil.easeOutCubic(t);
        double heightOffset = (1.0 - eased) * FALL_START_OFFSET;

        Location crateAt = groundAnchor.clone().add(0, heightOffset, 0);
        World world = groundAnchor.getWorld();
        RayTraceResult hit = world.rayTraceBlocks(crateAt, new Vector(0, -1, 0), GROUND_CHECK_DISTANCE,
                FluidCollisionMode.NEVER, true);
        boolean touchedGround = hit != null && hit.getHitBlock() != null;
        if (touchedGround) {
            crateAt.setY(hit.getHitPosition().getY());
        }

        float spin = (float) ((System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI * 2);
        applyTransform(drop.crate, spin, drop.mega ? MEGA_SCALE : NORMAL_SCALE);
        drop.crate.teleport(crateAt);
        if (drop.label != null && drop.label.isValid()) {
            drop.label.teleport(crateAt.clone().add(0, LABEL_HEIGHT_OFFSET, 0));
        }

        if (touchedGround || t >= 1.0) {
            onLanded(drop, crateAt);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animateFall(drop, groundAnchor, start), 1L);
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
     * postawiony blok, bez dalszego bujania/kręcenia się. Dopiero teraz dostawiamy niewidzialną
     * encję Interaction (ItemDisplay sam w sobie nie ma hitboksu, więc bez niej nie dałoby się
     * jej kliknąć).
     */
    private void onLanded(ActiveDrop drop, Location groundAnchor) {
        drop.landed = true;
        drop.crate.teleport(groundAnchor);
        applyTransform(drop.crate, 0f, drop.mega ? MEGA_SCALE : NORMAL_SCALE);
        if (drop.label != null && drop.label.isValid()) {
            drop.label.teleport(groundAnchor.clone().add(0, LABEL_HEIGHT_OFFSET, 0));
        }

        World world = groundAnchor.getWorld();
        drop.hitbox = world.spawn(groundAnchor, Interaction.class, e -> {
            e.setInteractionWidth(drop.mega ? 1.3f : 0.9f);
            e.setInteractionHeight(drop.mega ? 1.5f : 1.0f);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, "hitbox");
            e.addScoreboardTag(TAG);
        });

        if (drop.mega) {
            world.spawnParticle(Particle.EXPLOSION, groundAnchor, 2);
            world.spawnParticle(Particle.DUST, groundAnchor, 100, 0.8, 0.5, 0.8, 0.0,
                    new Particle.DustOptions(Color.fromRGB(Branding.DARK_PURPLE), 1.4f));
            world.spawnParticle(Particle.FLAME, groundAnchor, 40, 0.6, 0.3, 0.6, 0.03);
            world.playSound(groundAnchor, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
            Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent("★ MEGA skrzynka-event")
                    + " §7wylądowała! Kliknij ją PPM, żeby otworzyć.");
        } else {
            world.spawnParticle(Particle.EXPLOSION, groundAnchor, 1);
            world.spawnParticle(Particle.CLOUD, groundAnchor, 40, 0.6, 0.3, 0.6, 0.05);
            world.playSound(groundAnchor, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
            Bukkit.broadcastMessage(Branding.chatPrefix() + "§c§l☁ Skrzynka-event §7wylądowała! Kliknij ją PPM, żeby otworzyć.");
        }
    }

    /**
     * Wywoływane przez EnvoyListener przy PPM na dowolnej encji - zwraca true, jeśli to było
     * trafienie w hitbox którejś z aktualnie leżących skrzynek i kliknięcie zostało obsłużone
     * (event powinien zostać anulowany).
     */
    public boolean tryOpen(Player player, Entity clicked) {
        for (ActiveDrop drop : activeDrops) {
            if (drop.landed && clicked.equals(drop.hitbox)) {
                openFor(player, drop);
                return true;
            }
        }
        return false;
    }

    private void openFor(Player player, ActiveDrop drop) {
        List<ItemStack> pool = drop.mega ? megaRewardPool : rewardPool;
        if (pool.isEmpty()) {
            String hint = drop.mega ? "/bpvp event megaitem add" : "/bpvp event envoyitem add";
            player.sendMessage("§cSkrzynka-event jest pusta (admin nie dodał jeszcze nagród: " + hint + ").");
            return;
        }
        int count = drop.mega ? 2 + random.nextInt(3) : 1 + random.nextInt(3);
        ItemStack lastReward = null;
        for (int i = 0; i < count; i++) {
            ItemStack reward = pool.get(random.nextInt(pool.size())).clone();
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
        if (drop.mega) {
            Bukkit.broadcastMessage(Branding.chatPrefix() + "§e" + player.getName() + " §7otworzył(a) "
                    + Branding.accent("★ MEGA skrzynkę-event") + "§7!");
        }
        despawn(drop);
    }

    private void despawn(ActiveDrop drop) {
        if (drop.crate != null && drop.crate.isValid()) {
            drop.crate.remove();
        }
        if (drop.hitbox != null && drop.hitbox.isValid()) {
            drop.hitbox.remove();
        }
        if (drop.label != null && drop.label.isValid()) {
            drop.label.remove();
        }
        activeDrops.remove(drop);
    }

    private void loadPool(String key, List<ItemStack> pool) {
        List<?> raw = data.getList(key);
        if (raw == null) {
            return;
        }
        for (Object obj : raw) {
            if (obj instanceof ItemStack) {
                pool.add((ItemStack) obj);
            }
        }
    }

    private void savePool(String key, List<ItemStack> pool) {
        data.set(key, pool);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać envoy.yml: " + e.getMessage());
        }
    }

    private static final class ActiveDrop {
        ItemDisplay crate;
        Interaction hitbox;
        TextDisplay label;
        boolean landed;
        boolean mega;
    }
}
