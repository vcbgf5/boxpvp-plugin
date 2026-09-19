package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Kantor - zamiast żywej encji Villager (jej programowy spawn bywa cicho blokowany, np. flagą
 * WorldGuard "mob-spawning" na chronionym spawnie) używa dokładnie tego samego triku co
 * skrzynki-event (EnvoyDisplayManager): ItemDisplay jako "prop" + TextDisplay jako etykieta +
 * niewidzialna Interaction jako hitbox do PPM. Żadna z tych encji nie jest "stworzeniem"
 * (CreatureSpawnEvent), więc nic ich nie blokuje. Nietrwałe (setPersistent(false)) - odtwarzane
 * od zera przy każdym starcie pluginu z zapisanej lokalizacji, tak jak tablice w
 * LeaderboardManager.
 */
public class BankManager {

    private static final String TAG = "bpvp_bank";
    private static final double LABEL_HEIGHT_OFFSET = 0.9;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey nameTag;

    private final Map<String, BankData> banks = new HashMap<>();

    public BankManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "bank.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć bank.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.nameTag = new NamespacedKey(plugin, "bpvp_bank_name");
    }

    /** Sprząta osierocone encje sprzed restartu, potem odtwarza wszystkie skonfigurowane Kantory. */
    public void initialize() {
        purgeOrphans();
        for (String name : data.getKeys(false)) {
            Location loc = readLocation(name);
            if (loc == null) {
                continue;
            }
            banks.put(name, spawnEntities(name, loc));
        }
    }

    public boolean exists(String name) {
        return banks.containsKey(name) || data.contains(name);
    }

    public List<String> names() {
        return new ArrayList<>(banks.keySet());
    }

    public void createBank(String name, Location location) {
        setLocation(name, location);
        save();
        banks.put(name, spawnEntities(name, location));
    }

    public boolean removeBank(String name) {
        BankData bd = banks.remove(name);
        if (bd == null) {
            return false;
        }
        despawn(bd);
        data.set(name, null);
        save();
        return true;
    }

    public BankData getByEntity(Entity entity) {
        String name = entity.getPersistentDataContainer().get(nameTag, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        return banks.get(name);
    }

    private BankData spawnEntities(String name, Location loc) {
        World world = loc.getWorld();
        loc.getChunk().load();

        ItemDisplay icon = world.spawn(loc, ItemDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setItemStack(new ItemStack(Material.EMERALD));
            e.getPersistentDataContainer().set(nameTag, PersistentDataType.STRING, name);
            e.addScoreboardTag(TAG);
        });

        TextDisplay label = world.spawn(loc.clone().add(0, LABEL_HEIGHT_OFFSET, 0), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setText(Branding.accent("Kantor"));
            e.setSeeThrough(false);
            e.setShadowed(false);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            e.getPersistentDataContainer().set(nameTag, PersistentDataType.STRING, name);
            e.addScoreboardTag(TAG);
        });

        Interaction hitbox = world.spawn(loc, Interaction.class, e -> {
            e.setInteractionWidth(1.0f);
            e.setInteractionHeight(1.4f);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.getPersistentDataContainer().set(nameTag, PersistentDataType.STRING, name);
            e.addScoreboardTag(TAG);
        });

        return new BankData(name, icon, label, hitbox);
    }

    private void despawn(BankData bd) {
        if (bd.icon != null && bd.icon.isValid()) {
            bd.icon.remove();
        }
        if (bd.label != null && bd.label.isValid()) {
            bd.label.remove();
        }
        if (bd.hitbox != null && bd.hitbox.isValid()) {
            bd.hitbox.remove();
        }
    }

    private void purgeOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
            for (Entity entity : world.getEntitiesByClass(Interaction.class)) {
                if (entity.getScoreboardTags().contains(TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private void setLocation(String name, Location location) {
        data.set(name + ".world", location.getWorld().getName());
        data.set(name + ".x", location.getX());
        data.set(name + ".y", location.getY());
        data.set(name + ".z", location.getZ());
        data.set(name + ".yaw", location.getYaw());
        data.set(name + ".pitch", location.getPitch());
    }

    private Location readLocation(String name) {
        String worldName = data.getString(name + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = data.getDouble(name + ".x");
        double y = data.getDouble(name + ".y");
        double z = data.getDouble(name + ".z");
        float yaw = (float) data.getDouble(name + ".yaw");
        float pitch = (float) data.getDouble(name + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać bank.yml: " + e.getMessage());
        }
    }

    public static final class BankData {
        final String name;
        final ItemDisplay icon;
        final TextDisplay label;
        final Interaction hitbox;

        BankData(String name, ItemDisplay icon, TextDisplay label, Interaction hitbox) {
            this.name = name;
            this.icon = icon;
            this.label = label;
            this.hitbox = hitbox;
        }
    }
}
