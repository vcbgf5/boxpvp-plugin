package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * "Skrzynka do sprawdzania" (/sprawdz) - admin ustawia JEDNO odizolowane miejsce, a
 * /sprawdz <gracz> teleportuje tam podejrzanego i zamraża go w miejscu (może się rozglądać, nie
 * ruszy), ORAZ teleportuje obok niego admina, który wydał komendę - do bezpośredniej obserwacji/
 * przesłuchania. Gracz siedzi tam, dopóki admin nie wypuści go przez /sprawdz z <gracz> - wtedy
 * wraca dokładnie tam, gdzie stał w momencie zabrania.
 */
public class CheckpointManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    private Location point;
    private final Map<UUID, Location> held = new HashMap<>();

    public CheckpointManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "checkpoint.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć checkpoint.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public boolean isConfigured() {
        return point != null;
    }

    public Location getPoint() {
        return point == null ? null : point.clone();
    }

    public boolean isHeld(UUID uuid) {
        return held.containsKey(uuid);
    }

    public void setPoint(Location location) {
        this.point = location.clone();
        data.set("world", location.getWorld().getName());
        data.set("x", location.getX());
        data.set("y", location.getY());
        data.set("z", location.getZ());
        data.set("yaw", location.getYaw());
        data.set("pitch", location.getPitch());
        save();
    }

    /** Zabiera podejrzanego do skrzynki i teleportuje admina obok - false jeśli już trzymany. */
    public boolean check(Player admin, Player target) {
        if (!isConfigured() || held.containsKey(target.getUniqueId())) {
            return false;
        }
        held.put(target.getUniqueId(), target.getLocation().clone());
        target.teleport(point);
        admin.teleport(point.clone().add(1, 0, 0));
        return true;
    }

    /** Wypuszcza gracza z powrotem tam, gdzie stał przed sprawdzeniem - false jeśli nie był trzymany. */
    public boolean release(Player target) {
        Location returnLoc = held.remove(target.getUniqueId());
        if (returnLoc == null) {
            return false;
        }
        if (target.isOnline() && returnLoc.getWorld() != null) {
            target.teleport(returnLoc);
        }
        return true;
    }

    private void load() {
        String worldName = data.getString("world");
        if (worldName == null) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        point = new Location(world, data.getDouble("x"), data.getDouble("y"), data.getDouble("z"),
                (float) data.getDouble("yaw"), (float) data.getDouble("pitch"));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać checkpoint.yml: " + e.getMessage());
        }
    }
}
