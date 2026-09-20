package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShopManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    public ShopManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "shop.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć shop.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean categoryExists(String id) {
        return data.contains("categories." + id);
    }

    public List<String> getCategories() {
        ConfigurationSection section = data.getConfigurationSection("categories");
        if (section == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    public void createCategory(String id, Material icon, String displayName) {
        data.set("categories." + id + ".icon", icon.toString());
        data.set("categories." + id + ".display-name", displayName);
        save();
    }

    public boolean removeCategory(String id) {
        if (!categoryExists(id)) {
            return false;
        }
        data.set("categories." + id, null);
        save();
        return true;
    }

    public Material getCategoryIcon(String id) {
        String name = data.getString("categories." + id + ".icon", "CHEST");
        try {
            return Material.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Material.CHEST;
        }
    }

    public String getCategoryDisplayName(String id) {
        return data.getString("categories." + id + ".display-name", id);
    }

    public int addItem(String category, ItemStack icon, double price, List<String> commands, ShopItemType type, String kitName,
                        double boosterMultiplier, int boosterMinutes) {
        int index = data.getInt("categories." + category + ".next-index", 0);
        String base = "categories." + category + ".items." + index;
        data.set(base + ".item", icon);
        data.set(base + ".price", price);
        data.set(base + ".commands", commands);
        data.set(base + ".type", type.name());
        data.set(base + ".kit-name", kitName);
        data.set(base + ".booster-multiplier", boosterMultiplier);
        data.set(base + ".booster-minutes", boosterMinutes);
        data.set("categories." + category + ".next-index", index + 1);
        save();
        return index;
    }

    public boolean removeItem(String category, int index) {
        String path = "categories." + category + ".items." + index;
        if (!data.contains(path)) {
            return false;
        }
        data.set(path, null);
        save();
        return true;
    }

    public Map<Integer, ShopItemData> getItems(String category) {
        Map<Integer, ShopItemData> map = new LinkedHashMap<>();
        ConfigurationSection section = data.getConfigurationSection("categories." + category + ".items");
        if (section == null) {
            return map;
        }

        List<String> keys = new ArrayList<>(section.getKeys(false));
        keys.sort((a, b) -> {
            try {
                return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
            } catch (NumberFormatException e) {
                return a.compareTo(b);
            }
        });

        for (String key : keys) {
            int index;
            try {
                index = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            String base = "categories." + category + ".items." + key;
            ItemStack icon = data.getItemStack(base + ".item");
            if (icon == null) {
                continue;
            }
            double price = data.getDouble(base + ".price");
            List<String> commands = data.getStringList(base + ".commands");
            String typeRaw = data.getString(base + ".type", "COMMAND");
            ShopItemType type;
            try {
                type = ShopItemType.valueOf(typeRaw);
            } catch (IllegalArgumentException e) {
                type = ShopItemType.COMMAND;
            }
            String kitName = data.getString(base + ".kit-name");
            double boosterMultiplier = data.getDouble(base + ".booster-multiplier", 0);
            int boosterMinutes = data.getInt(base + ".booster-minutes", 0);

            map.put(index, new ShopItemData(icon, price, commands, type, kitName, boosterMultiplier, boosterMinutes));
        }
        return map;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać shop.yml: " + e.getMessage());
        }
    }

    public static class ShopItemData {
        public final ItemStack icon;
        public final double price;
        public final List<String> commands;
        public final ShopItemType type;
        public final String kitName;
        public final double boosterMultiplier;
        public final int boosterMinutes;

        public ShopItemData(ItemStack icon, double price, List<String> commands, ShopItemType type, String kitName,
                             double boosterMultiplier, int boosterMinutes) {
            this.icon = icon;
            this.price = price;
            this.commands = commands;
            this.type = type;
            this.kitName = kitName;
            this.boosterMultiplier = boosterMultiplier;
            this.boosterMinutes = boosterMinutes;
        }
    }
}
