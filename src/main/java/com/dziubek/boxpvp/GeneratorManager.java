package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wolnostojące generatory bloków (nie należą do żadnej areny - ich tu po prostu nie ma).
 * Admin różdżką zaznacza pos1/pos2 (cały obszar), a co skonfigurowany interwał CAŁY obszar
 * zostaje na nowo wypełniony wybranym blokiem ("fill"). Gracze kopią wygenerowane bloki,
 * sprzedają je w /sklep za monety i kupują lepszy sprzęt. Hologram nad generatorem
 * (DecentHolograms) pokazuje odliczanie do kolejnego napełnienia.
 */
public class GeneratorManager {

    private static final long TICK_INTERVAL_TICKS = 20L;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey wandKey;

    private final Map<String, Generator> generators = new HashMap<>();
    private final Map<UUID, Location> pos1Selection = new HashMap<>();
    private final Map<UUID, Location> pos2Selection = new HashMap<>();

    public GeneratorManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "generators.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć generators.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.wandKey = new NamespacedKey(plugin, "bpvp_wand");
        loadAll();
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, TICK_INTERVAL_TICKS, TICK_INTERVAL_TICKS);
    }

    // ================= Różdżka do zaznaczania =================

    public ItemStack createWand() {
        ItemStack wand = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§e§lRóżdżka generatora");
        List<String> lore = new ArrayList<>();
        lore.add("§7LPM w blok §f- pozycja 1");
        lore.add("§7PPM w blok §f- pozycja 2");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public void setPos1(Player player, Location location) {
        pos1Selection.put(player.getUniqueId(), location.getBlock().getLocation());
        player.sendMessage("§aPozycja 1 ustawiona: §f" + formatLoc(location));
    }

    public void setPos2(Player player, Location location) {
        pos2Selection.put(player.getUniqueId(), location.getBlock().getLocation());
        player.sendMessage("§aPozycja 2 ustawiona: §f" + formatLoc(location));
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    // ================= Zarządzanie generatorami =================

    public boolean exists(String genName) {
        return generators.containsKey(genName);
    }

    public boolean createGenerator(Player admin, String genName, Material material, int intervalSeconds) {
        Location p1 = pos1Selection.get(admin.getUniqueId());
        Location p2 = pos2Selection.get(admin.getUniqueId());
        if (p1 == null || p2 == null) {
            admin.sendMessage("§cNajpierw zaznacz obie pozycje różdżką (§f/bpvp wand§c).");
            return false;
        }
        if (!p1.getWorld().equals(p2.getWorld())) {
            admin.sendMessage("§cObie pozycje muszą być w tym samym świecie.");
            return false;
        }

        Generator gen = new Generator(genName, p1, p2, material, intervalSeconds);
        generators.put(genName, gen);
        saveGenerator(gen);
        fill(gen);
        return true;
    }

    public boolean removeGenerator(String genName) {
        Generator gen = generators.remove(genName);
        if (gen == null) {
            return false;
        }
        data.set(genName, null);
        save();
        plugin.getDecentHolograms().removeHologram(hologramId(gen));
        return true;
    }

    public List<String> names() {
        return new ArrayList<>(generators.keySet());
    }

    /**
     * Czy dany blok leży w obszarze KTÓREGOKOLWIEK generatora - używane przez auto-sprzedaż,
     * żeby sprzedawać tylko bloki faktycznie wykopane z generatora, a nie np. postawione ręcznie.
     */
    public boolean isGeneratorBlock(Location location) {
        for (Generator gen : generators.values()) {
            if (contains(gen, location)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(Generator gen, Location location) {
        World world = gen.pos1.getWorld();
        if (world == null || !world.equals(location.getWorld())) {
            return false;
        }
        int minX = Math.min(gen.pos1.getBlockX(), gen.pos2.getBlockX());
        int maxX = Math.max(gen.pos1.getBlockX(), gen.pos2.getBlockX());
        int minY = Math.min(gen.pos1.getBlockY(), gen.pos2.getBlockY());
        int maxY = Math.max(gen.pos1.getBlockY(), gen.pos2.getBlockY());
        int minZ = Math.min(gen.pos1.getBlockZ(), gen.pos2.getBlockZ());
        int maxZ = Math.max(gen.pos1.getBlockZ(), gen.pos2.getBlockZ());

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Generator gen : generators.values()) {
            long remainingMs = gen.nextFillAt - now;
            if (remainingMs <= 0) {
                fill(gen);
            } else {
                updateHologram(gen, remainingMs);
            }
        }
    }

    private void fill(Generator gen) {
        World world = gen.pos1.getWorld();
        if (world == null) {
            return;
        }
        int minX = Math.min(gen.pos1.getBlockX(), gen.pos2.getBlockX());
        int maxX = Math.max(gen.pos1.getBlockX(), gen.pos2.getBlockX());
        int minY = Math.min(gen.pos1.getBlockY(), gen.pos2.getBlockY());
        int maxY = Math.max(gen.pos1.getBlockY(), gen.pos2.getBlockY());
        int minZ = Math.min(gen.pos1.getBlockZ(), gen.pos2.getBlockZ());
        int maxZ = Math.max(gen.pos1.getBlockZ(), gen.pos2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    block.setType(gen.material);
                }
            }
        }

        Location center = new Location(world, (minX + maxX) / 2.0 + 0.5, maxY + 1.0, (minZ + maxZ) / 2.0 + 0.5);
        world.spawnParticle(Particle.CLOUD, center, 40, (maxX - minX) / 2.0 + 0.2, 0.3, (maxZ - minZ) / 2.0 + 0.2, 0.02);
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.2f);

        gen.nextFillAt = System.currentTimeMillis() + gen.intervalSeconds * 1000L;
        updateHologram(gen, gen.intervalSeconds * 1000L);
    }

    private void updateHologram(Generator gen, long remainingMs) {
        long remainingSeconds = Math.max(0, remainingMs / 1000);
        List<String> lines = new ArrayList<>();
        lines.add("&6&l" + gen.name);
        lines.add("&7Blok: &f" + materialDisplayName(gen.material));
        lines.add("&7Odnowienie za: &e" + formatDuration(remainingSeconds));

        plugin.getDecentHolograms().createInfoHologram(hologramId(gen), generatorHologramLocation(gen), lines);
    }

    private Location generatorHologramLocation(Generator gen) {
        int maxY = Math.max(gen.pos1.getBlockY(), gen.pos2.getBlockY());
        double centerX = (gen.pos1.getBlockX() + gen.pos2.getBlockX()) / 2.0 + 0.5;
        double centerZ = (gen.pos1.getBlockZ() + gen.pos2.getBlockZ()) / 2.0 + 0.5;
        return new Location(gen.pos1.getWorld(), centerX, maxY + 2.0, centerZ);
    }

    private String hologramId(Generator gen) {
        return "bpvp_gen_" + gen.name;
    }

    private static String materialDisplayName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) {
            return minutes + "min " + seconds + "s";
        }
        return seconds + "s";
    }

    // ================= Zapis/odczyt =================

    private void saveGenerator(Generator gen) {
        String base = gen.name;
        data.set(base + ".world", gen.pos1.getWorld().getName());
        data.set(base + ".x1", gen.pos1.getBlockX());
        data.set(base + ".y1", gen.pos1.getBlockY());
        data.set(base + ".z1", gen.pos1.getBlockZ());
        data.set(base + ".x2", gen.pos2.getBlockX());
        data.set(base + ".y2", gen.pos2.getBlockY());
        data.set(base + ".z2", gen.pos2.getBlockZ());
        data.set(base + ".material", gen.material.name());
        data.set(base + ".interval-seconds", gen.intervalSeconds);
        save();
    }

    private void loadAll() {
        for (String genName : data.getKeys(false)) {
            String base = genName;
            String worldName = data.getString(base + ".world");
            World world = worldName == null ? null : Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }
            Location p1 = new Location(world, data.getInt(base + ".x1"), data.getInt(base + ".y1"), data.getInt(base + ".z1"));
            Location p2 = new Location(world, data.getInt(base + ".x2"), data.getInt(base + ".y2"), data.getInt(base + ".z2"));
            Material material = Material.matchMaterial(data.getString(base + ".material", "STONE"));
            if (material == null) {
                material = Material.STONE;
            }
            int intervalSeconds = data.getInt(base + ".interval-seconds", 120);

            Generator gen = new Generator(genName, p1, p2, material, intervalSeconds);
            generators.put(genName, gen);
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać generators.yml: " + e.getMessage());
        }
    }

    private static final class Generator {
        final String name;
        final Location pos1;
        final Location pos2;
        final Material material;
        final int intervalSeconds;
        long nextFillAt;

        Generator(String name, Location pos1, Location pos2, Material material, int intervalSeconds) {
            this.name = name;
            this.pos1 = pos1;
            this.pos2 = pos2;
            this.material = material;
            this.intervalSeconds = intervalSeconds;
            this.nextFillAt = System.currentTimeMillis() + intervalSeconds * 1000L;
        }
    }
}
