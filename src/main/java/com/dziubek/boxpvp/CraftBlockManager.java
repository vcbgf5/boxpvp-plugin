package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Blokada craftingu - admin oznacza materiały, których nie da się wykraftować na tym serwerze
 * (np. Netherite Block). Próba craftowania takiego przedmiotu daje zamiast niego kartkę z
 * informacją, że się nie da (CraftBlockListener).
 */
public class CraftBlockManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final Set<Material> blocked = new LinkedHashSet<>();

    public CraftBlockManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "craftblock.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć craftblock.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public boolean add(Material material) {
        boolean added = blocked.add(material);
        if (added) {
            save();
        }
        return added;
    }

    public boolean remove(Material material) {
        boolean removed = blocked.remove(material);
        if (removed) {
            save();
        }
        return removed;
    }

    public boolean isBlocked(Material material) {
        return blocked.contains(material);
    }

    public List<Material> all() {
        return new ArrayList<>(blocked);
    }

    private void load() {
        for (String name : data.getStringList("blocked")) {
            Material material = Material.matchMaterial(name);
            if (material != null) {
                blocked.add(material);
            }
        }
    }

    private void save() {
        List<String> names = new ArrayList<>();
        for (Material material : blocked) {
            names.add(material.name());
        }
        data.set("blocked", names);
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać craftblock.yml: " + e.getMessage());
        }
    }
}
