package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nominały fizycznej waluty Kantoru - każdy skonfigurowany materiał (słonecznik, sztabki itd.)
 * ma stałą wartość w monetach (Vault). Przy pierwszym uruchomieniu (pusty plik) zasiewa
 * domyślną progresję, admin może ją potem dowolnie zmienić przez /bank-serwer price.
 */
public class CurrencyManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final Map<Material, Double> prices = new LinkedHashMap<>();

    public CurrencyManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "currency.yml");
        boolean fresh = !file.exists();
        if (fresh) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć currency.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        if (fresh) {
            seedDefaults();
        } else {
            load();
        }
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

    /** Materiały posortowane rosnąco po wartości - kolejność wyświetlania w GUI Kantoru. */
    public List<Material> orderedMaterials() {
        List<Material> ordered = new ArrayList<>(prices.keySet());
        ordered.sort(Comparator.comparingDouble(prices::get));
        return ordered;
    }

    private void seedDefaults() {
        setPrice(Material.SUNFLOWER, 1.0);
        setPrice(Material.IRON_INGOT, 10.0);
        setPrice(Material.GOLD_INGOT, 100.0);
        setPrice(Material.DIAMOND, 1000.0);
        setPrice(Material.NETHERITE_INGOT, 10_000.0);
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
            plugin.getLogger().warning("Nie udało się zapisać currency.yml: " + e.getMessage());
        }
    }
}
