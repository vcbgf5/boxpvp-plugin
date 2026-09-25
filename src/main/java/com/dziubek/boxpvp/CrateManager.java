package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CrateManager {

    private static final String CHANCE_LORE_PREFIX = "§7Szansa: §f";

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey keyTag;
    private final NamespacedKey chanceTag;

    // blockKey ("world,x,y,z") -> nazwa skrzyni, przebudowywana po każdej zmianie
    private final Map<String, String> locationCache = new HashMap<>();

    public CrateManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "crates.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć crates.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.keyTag = new NamespacedKey(plugin, "crate_key");
        this.chanceTag = new NamespacedKey(plugin, "crate_reward_chance");
        rebuildLocationCache();
    }

    public boolean exists(String name) {
        return data.contains(name);
    }

    public List<String> names() {
        return new ArrayList<>(data.getKeys(false));
    }

    /**
     * Zapisuje zawartość GUI konfiguracji skrzyni jako listę nagród.
     * Przedmioty oznaczone na czacie (PPM -> wpisany procent) mają ustaloną szansę ("locked"),
     * reszta dostaje automatycznie wyliczony udział tak, by suma zawsze wynosiła 100%.
     * Ilość sztuk w slocie to teraz WYŁĄCZNIE ilość wypłaty, nie ma już wpływu na szansę.
     */
    public void saveRewards(String name, ItemStack[] contents) {
        List<ItemStack> items = new ArrayList<>();
        List<Double> explicit = new ArrayList<>();

        for (ItemStack raw : contents) {
            if (raw == null || raw.getType() == Material.AIR) {
                continue;
            }
            explicit.add(readChanceTag(raw));
            items.add(stripChanceMeta(raw));
        }

        double[] chances = computeChances(explicit);

        data.set(name + ".rewards", null);
        for (int i = 0; i < items.size(); i++) {
            String base = name + ".rewards." + i;
            data.set(base + ".item", items.get(i));
            data.set(base + ".chance", chances[i]);
            data.set(base + ".chance-locked", explicit.get(i) != null);
        }
        save();
    }

    private double[] computeChances(List<Double> explicit) {
        int n = explicit.size();
        double[] result = new double[n];
        if (n == 0) {
            return result;
        }

        double explicitSum = 0;
        int explicitCount = 0;
        int autoCount = 0;
        for (Double v : explicit) {
            if (v != null) {
                explicitSum += v;
                explicitCount++;
            } else {
                autoCount++;
            }
        }

        if (explicitCount == 0) {
            Arrays.fill(result, 100.0 / n);
            return result;
        }

        if (autoCount > 0) {
            double remaining = 100.0 - explicitSum;
            if (remaining <= 0) {
                // przypisane procenty już wypełniają/przekraczają 100% - przeskaluj je proporcjonalnie,
                // nieoznaczone przedmioty nie dostają nic (0%)
                for (int i = 0; i < n; i++) {
                    Double v = explicit.get(i);
                    result[i] = v != null ? (v / explicitSum) * 100.0 : 0.0;
                }
            } else {
                double share = remaining / autoCount;
                for (int i = 0; i < n; i++) {
                    Double v = explicit.get(i);
                    result[i] = v != null ? v : share;
                }
            }
            return result;
        }

        // wszystkie przedmioty mają ręcznie wpisany procent
        if (Math.abs(explicitSum - 100.0) < 0.01) {
            for (int i = 0; i < n; i++) {
                result[i] = explicit.get(i);
            }
        } else if (explicitSum > 0) {
            // suma się nie zgadza - przeskaluj proporcjonalnie tak, by wyszło dokładnie 100%
            for (int i = 0; i < n; i++) {
                result[i] = (explicit.get(i) / explicitSum) * 100.0;
            }
        } else {
            Arrays.fill(result, 100.0 / n);
        }
        return result;
    }

    /**
     * Zwraca nagrody skrzyni. Stare skrzynie (sprzed systemu procentów) są migrowane
     * przy pierwszym odczycie - dawna waga (ilość sztuk) zostaje przeliczona na procent 1:1,
     * więc realne szanse wypadnięcia się nie zmieniają.
     */
    public List<CrateReward> getRewards(String name) {
        List<CrateReward> list = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(name + ".rewards");
        if (section == null) {
            return list;
        }

        List<String> keys = new ArrayList<>(section.getKeys(false));
        boolean needsMigration = false;
        for (String key : keys) {
            if (!data.contains(name + ".rewards." + key + ".chance")) {
                needsMigration = true;
                break;
            }
        }
        if (needsMigration) {
            migrateLegacyChances(name, keys);
        }

        for (String key : keys) {
            String base = name + ".rewards." + key;
            ItemStack item = data.getItemStack(base + ".item");
            if (item == null) {
                continue;
            }
            double chance = data.getDouble(base + ".chance", 0);
            boolean locked = data.getBoolean(base + ".chance-locked", false);
            list.add(new CrateReward(item, chance, locked));
        }
        return list;
    }

    private void migrateLegacyChances(String name, List<String> keys) {
        Map<String, Integer> amounts = new LinkedHashMap<>();
        int totalAmount = 0;
        for (String key : keys) {
            ItemStack item = data.getItemStack(name + ".rewards." + key + ".item");
            int amount = item != null ? Math.max(1, item.getAmount()) : 1;
            amounts.put(key, amount);
            totalAmount += amount;
        }
        for (String key : keys) {
            double chance = totalAmount > 0 ? (amounts.get(key) * 100.0 / totalAmount) : (100.0 / keys.size());
            data.set(name + ".rewards." + key + ".chance", chance);
            data.set(name + ".rewards." + key + ".chance-locked", false);
        }
        save();
    }

    /**
     * Oznacza przedmiot ręcznie wpisanym procentem szansy - dopisuje trwały tag (przetrwa
     * przenoszenie między slotami) i widoczną linijkę lore. Zwraca nową kopię przedmiotu.
     */
    public ItemStack applyChanceTag(ItemStack original, double chance) {
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().set(chanceTag, PersistentDataType.STRING, String.valueOf(chance));

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.startsWith(CHANCE_LORE_PREFIX));
        lore.add(CHANCE_LORE_PREFIX + trimPercent(chance) + "%");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Usuwa tag/lore procentu - przedmiot wraca do automatycznego wypełniania szansy.
     */
    public ItemStack clearChanceTag(ItemStack original) {
        return stripChanceMeta(original);
    }

    private Double readChanceTag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(chanceTag, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ItemStack stripChanceMeta(ItemStack original) {
        if (readChanceTag(original) == null) {
            return original.clone();
        }
        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.getPersistentDataContainer().remove(chanceTag);
        if (meta.hasLore()) {
            List<String> lore = new ArrayList<>(meta.getLore());
            lore.removeIf(line -> line.startsWith(CHANCE_LORE_PREFIX));
            meta.setLore(lore.isEmpty() ? null : lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String trimPercent(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać crates.yml: " + e.getMessage());
        }
    }

    public ItemStack createKey(String crateName, int amount) {
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, amount);
        ItemMeta meta = key.getItemMeta();
        meta.setDisplayName("§e§lKlucz do skrzyni: §f" + crateName);

        List<String> lore = new ArrayList<>();
        lore.add("§7Kliknij PRAWYM przyciskiem,");
        lore.add("§7aby otworzyć skrzynię!");
        meta.setLore(lore);

        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, crateName);

        String texture = getKeyTexture(crateName);
        if (texture != null) {
            meta.setItemModel(new NamespacedKey("boxpvp", "key_" + texture));
        }

        key.setItemMeta(meta);
        return key;
    }

    /** Nazwa customowej tekstury klucza (bez prefixu "key_") ustawiona przez /crate setkeytexture, albo null. */
    public String getKeyTexture(String crateName) {
        return data.getString(crateName + ".key-texture");
    }

    public void setKeyTexture(String crateName, String texture) {
        data.set(crateName + ".key-texture", texture);
        save();
    }

    public String getKeyCrateName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
    }

    // ===================== Fizyczne skrzynie w świecie =====================

    public boolean bindLocation(String name, Location location) {
        return bindLocation(name, location, null, 0f);
    }

    /**
     * @param modelName nazwa 3D modelu skrzyni (z CrateModelRegistry) do wyświetlenia zamiast
     *                   bloku, albo null - wtedy blok zostaje jaki jest, bez modelu 3D.
     * @param yaw        kierunek (tylko w poziomie, patrz CrateModelDisplayManager) w jakim model
     *                   ma być obrócony - kąt gracza w momencie bindowania.
     */
    public boolean bindLocation(String name, Location location, String modelName, float yaw) {
        if (!exists(name) || location.getWorld() == null) {
            return false;
        }

        String id = UUID.randomUUID().toString();
        String path = name + ".locations." + id;
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getBlockX());
        data.set(path + ".y", location.getBlockY());
        data.set(path + ".z", location.getBlockZ());
        if (modelName != null) {
            data.set(path + ".model", modelName);
            data.set(path + ".yaw", yaw);
        }
        save();
        rebuildLocationCache();
        createHologram(name, id, location);
        plugin.getCrateItemDisplays().spawnDisplay(name, location);
        if (modelName != null) {
            CrateModel model = plugin.getCrateModels().get(modelName);
            if (model != null) {
                plugin.getCrateModelDisplays().spawn(location, model, yaw);
            }
        }
        return true;
    }

    /**
     * Odpina fizyczną skrzynię wskazaną blokiem. Zwraca nazwę configu skrzyni, który został odpięty, lub null.
     */
    public String unbindLocation(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        for (String name : names()) {
            ConfigurationSection locations = data.getConfigurationSection(name + ".locations");
            if (locations == null) {
                continue;
            }
            for (String id : new ArrayList<>(locations.getKeys(false))) {
                String base = name + ".locations." + id;
                if (world.equals(data.getString(base + ".world"))
                        && x == data.getInt(base + ".x")
                        && y == data.getInt(base + ".y")
                        && z == data.getInt(base + ".z")) {
                    data.set(base, null);
                    save();
                    rebuildLocationCache();
                    removeHologram(name, id);
                    plugin.getCrateItemDisplays().removeDisplay(location);
                    plugin.getCrateModelDisplays().despawn(location);
                    location.getBlock().setType(Material.AIR);
                    return name;
                }
            }
        }
        return null;
    }

    public String getCrateNameAt(Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        return locationCache.get(blockKey(location));
    }

    public int countLocations(String name) {
        ConfigurationSection locations = data.getConfigurationSection(name + ".locations");
        return locations == null ? 0 : locations.getKeys(false).size();
    }

    public CrateEffect getEffect(String name) {
        CrateEffect effect = CrateEffect.fromString(data.getString(name + ".effect"));
        return effect != null ? effect : CrateEffect.FLAME;
    }

    public void setEffect(String name, CrateEffect effect) {
        data.set(name + ".effect", effect.name());
        save();
        refreshHolograms(name);
    }

    /**
     * Efekt ambientowy widoczny przy skrzyni gdy nikt jej nie otwiera. null = wyłączony (domyślnie).
     */
    public CrateEffect getIdleEffect(String name) {
        return CrateEffect.fromString(data.getString(name + ".idle-effect"));
    }

    public void setIdleEffect(String name, CrateEffect effect) {
        data.set(name + ".idle-effect", effect == null ? null : effect.name());
        save();
    }

    public boolean isPrivate(String name) {
        return data.getBoolean(name + ".private", false);
    }

    public void setPrivate(String name, boolean value) {
        data.set(name + ".private", value);
        save();
        refreshHolograms(name);
    }

    /**
     * Uprawnienie LuckPerms wymagane do otwarcia skrzyni oznaczonej jako prywatna.
     */
    public String getPrivatePermission(String name) {
        return "boxpvp.crate.private." + name;
    }

    // ===================== Darmowe otwarcie co X godzin (bez klucza) =====================

    public int getFreeCooldownHours(String name) {
        return data.getInt(name + ".free-cooldown-hours", 0);
    }

    public void setFreeCooldownHours(String name, int hours) {
        data.set(name + ".free-cooldown-hours", hours);
        save();
        refreshHolograms(name);
    }

    public boolean canUseFreeOpen(String name, UUID uuid) {
        int hours = getFreeCooldownHours(name);
        if (hours <= 0) {
            return false;
        }
        long last = data.getLong(name + ".free-last-use." + uuid, -1);
        if (last < 0) {
            return true;
        }
        return System.currentTimeMillis() - last >= hours * 3_600_000L;
    }

    public long freeSecondsLeft(String name, UUID uuid) {
        int hours = getFreeCooldownHours(name);
        if (hours <= 0) {
            return 0;
        }
        long last = data.getLong(name + ".free-last-use." + uuid, -1);
        if (last < 0) {
            return 0;
        }
        long totalMs = hours * 3_600_000L;
        long elapsedMs = System.currentTimeMillis() - last;
        return Math.max(0, (totalMs - elapsedMs) / 1000L);
    }

    public void markFreeUsed(String name, UUID uuid) {
        data.set(name + ".free-last-use." + uuid, System.currentTimeMillis());
        save();
    }

    public String getHologramTitle(String name) {
        return data.getString(name + ".hologram-title", Branding.accent(" " + name + " "));
    }

    public void setHologramTitle(String name, String title) {
        data.set(name + ".hologram-title", title);
        save();
        refreshHolograms(name);
    }

    public void refreshAllHolograms() {
        for (String name : names()) {
            refreshHolograms(name);
        }
    }

    /**
     * Stawia pływające ItemDisplay nad wszystkimi fizycznymi skrzyniami - wywoływane przy
     * starcie pluginu (analogicznie do refreshAllHolograms). Błąd przy jednej lokalizacji
     * nie przerywa reszty - trafia do logów, żeby dało się go namierzyć.
     */
    public void initializeItemDisplays() {
        plugin.getCrateItemDisplays().purgeOrphans();
        int spawned = 0;
        int total = 0;
        for (String name : names()) {
            for (Location location : getAllLocations(name)) {
                total++;
                try {
                    plugin.getCrateItemDisplays().spawnDisplay(name, location);
                    spawned++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Nie udało się postawić pływającego przedmiotu nad skrzynią '"
                            + name + "' (" + location + "): " + e);
                }
            }
        }
        plugin.getLogger().info("Pływające przedmioty nad skrzyniami: " + spawned + "/" + total + " postawionych.");
    }

    /** Odtwarza 3D modele skrzyń (jeśli przypięte z modelem) po restarcie serwera. */
    public void initializeCrateModelDisplays() {
        int spawned = 0;
        for (String name : names()) {
            ConfigurationSection locations = data.getConfigurationSection(name + ".locations");
            if (locations == null) {
                continue;
            }
            for (String id : locations.getKeys(false)) {
                String base = name + ".locations." + id;
                String modelName = data.getString(base + ".model");
                if (modelName == null) {
                    continue;
                }
                CrateModel model = plugin.getCrateModels().get(modelName);
                Location location = readLocation(name, id);
                if (model == null || location == null) {
                    continue;
                }
                float yaw = (float) data.getDouble(base + ".yaw", 0);
                try {
                    plugin.getCrateModelDisplays().spawn(location, model, yaw);
                    spawned++;
                } catch (Exception e) {
                    plugin.getLogger().warning("Nie udało się postawić modelu 3D skrzyni '"
                            + name + "' (" + location + "): " + e);
                }
            }
        }
        plugin.getLogger().info("Modele 3D skrzyń: " + spawned + " postawionych.");
    }

    private void refreshHolograms(String name) {
        if (!plugin.getDecentHolograms().isAvailable()) {
            return;
        }
        ConfigurationSection locations = data.getConfigurationSection(name + ".locations");
        if (locations == null) {
            return;
        }
        for (String id : locations.getKeys(false)) {
            Location location = readLocation(name, id);
            if (location != null) {
                createHologram(name, id, location);
            }
        }
    }

    /**
     * Wszystkie fizyczne lokalizacje danej skrzyni (dla efektu idle).
     */
    public List<Location> getAllLocations(String name) {
        List<Location> list = new ArrayList<>();
        ConfigurationSection locations = data.getConfigurationSection(name + ".locations");
        if (locations == null) {
            return list;
        }
        for (String id : locations.getKeys(false)) {
            Location location = readLocation(name, id);
            if (location != null) {
                list.add(location);
            }
        }
        return list;
    }

    private Location readLocation(String name, String id) {
        String base = name + ".locations." + id;
        String worldName = data.getString(base + ".world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, data.getInt(base + ".x"), data.getInt(base + ".y"), data.getInt(base + ".z"));
    }

    private void createHologram(String name, String id, Location blockLocation) {
        Location above = blockLocation.clone().add(0.5, 1.8, 0.5);
        plugin.getDecentHolograms().createInfoHologram(hologramId(name, id), above, buildHologramLines(name));
    }

    private void removeHologram(String name, String id) {
        plugin.getDecentHolograms().removeHologram(hologramId(name, id));
    }

    private String hologramId(String name, String id) {
        return "crate_" + name + "_" + id.substring(0, 8);
    }

    private List<String> buildHologramLines(String name) {
        List<String> lines = new ArrayList<>();
        lines.add(getHologramTitle(name));
        lines.add("&7Kliknij PPM z kluczem, by otworzyć!");

        List<String> extras = new ArrayList<>();
        if (isPrivate(name)) {
            extras.add("&c🔒 Prywatna");
        }
        if (getFreeCooldownHours(name) > 0) {
            extras.add("&a🎁 Darmowe co " + getFreeCooldownHours(name) + "h");
        }
        if (!extras.isEmpty()) {
            lines.add("&8&m--------------------");
            lines.addAll(extras);
        }
        return lines;
    }

    private void rebuildLocationCache() {
        locationCache.clear();
        for (String name : names()) {
            ConfigurationSection locations = data.getConfigurationSection(name + ".locations");
            if (locations == null) {
                continue;
            }
            for (String id : locations.getKeys(false)) {
                String base = name + ".locations." + id;
                String world = data.getString(base + ".world");
                if (world == null) {
                    continue;
                }
                int x = data.getInt(base + ".x");
                int y = data.getInt(base + ".y");
                int z = data.getInt(base + ".z");
                locationCache.put(world + "," + x + "," + y + "," + z, name);
            }
        }
    }

    private String blockKey(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
