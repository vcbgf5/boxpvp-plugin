package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ceny auto-sprzedaży bloków z generatorów - blok znika i od razu zamienia się w monety
 * (Vault), bez potrzeby chodzenia do /sklep czy noszenia pełnego ekwipunku.
 */
public class SellManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final Map<Material, Double> prices = new LinkedHashMap<>();

    public SellManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "sellprices.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć sellprices.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public void setPrice(Material material, double price) {
        prices.put(material, price);
        data.set(material.name(), price);
        save();
    }

    public boolean removePrice(Material material) {
        if (prices.remove(material) == null) {
            return false;
        }
        data.set(material.name(), null);
        save();
        return true;
    }

    public double getPrice(Material material) {
        Double price = prices.get(material);
        return price == null ? 0.0 : price;
    }

    public Map<Material, Double> all() {
        return prices;
    }

    private void load() {
        for (String key : data.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                continue;
            }
            prices.put(material, data.getDouble(key));
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać sellprices.yml: " + e.getMessage());
        }
    }
}
