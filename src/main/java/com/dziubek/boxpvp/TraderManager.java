package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handlarze - prawdziwe encje Villager z natywnym GUI handlu Minecrafta (MerchantRecipe),
 * więc gracz dostaje gotowy, znajomy, ładny interfejs bez pisania własnego GUI wymiany.
 * PPM (bez shift) = handel (vanilla), Shift+PPM = edycja (nasze GUI: trade'y, wygląd, nazwa).
 * Wymiana jest zawsze przedmiot-za-przedmiot, bez pieniędzy.
 */
public class TraderManager {

    private static final String TAG = "bpvp_trader";

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey nameTag;

    private final Map<String, TraderData> traders = new HashMap<>();
    private final Map<UUID, String> awaitingRename = new HashMap<>();
    private volatile boolean spawningTrader = false;

    public TraderManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "traders.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć traders.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        this.nameTag = new NamespacedKey(plugin, "bpvp_trader_name");
    }

    /**
     * Wczytuje wszystkich skonfigurowanych handlarzy - jeśli w świecie już stoi ich encja
     * (przeżyła restart, bo jest trwała), przejmuje ją zamiast tworzyć duplikat.
     */
    public void initialize() {
        for (String name : data.getKeys(false)) {
            Location loc = readLocation(name);
            if (loc == null) {
                continue;
            }
            String title = data.getString(name + ".title", Branding.accent(name));
            Villager.Profession profession = parseProfession(data.getString(name + ".profession"));
            Villager.Type type = parseType(data.getString(name + ".type"));
            List<Trade> trades = readTrades(name);

            Villager villager = findExisting(name, loc);
            if (villager == null) {
                villager = spawnVillager(name, loc);
            }
            TraderData td = new TraderData(name, villager, title, profession, type, trades);
            traders.put(name, td);
            applyAppearance(td);
            applyRecipes(td);
        }
    }

    public boolean exists(String name) {
        return traders.containsKey(name) || data.contains(name);
    }

    public List<String> names() {
        return new ArrayList<>(traders.keySet());
    }

    public void createTrader(String name, Location location) {
        setLocation(name, location);
        data.set(name + ".title", Branding.accent(name));
        data.set(name + ".profession", Villager.Profession.NONE.name());
        data.set(name + ".type", Villager.Type.PLAINS.name());
        save();

        Villager villager = spawnVillager(name, location);
        TraderData td = new TraderData(name, villager, Branding.accent(name), Villager.Profession.NONE, Villager.Type.PLAINS, new ArrayList<>());
        traders.put(name, td);
        applyAppearance(td);
    }

    public boolean removeTrader(String name) {
        TraderData td = traders.remove(name);
        if (td == null) {
            return false;
        }
        if (td.villager.isValid()) {
            td.villager.remove();
        }
        data.set(name, null);
        save();
        return true;
    }

    public TraderData getData(String name) {
        return traders.get(name);
    }

    public TraderData getByEntity(Entity entity) {
        String name = entity.getPersistentDataContainer().get(nameTag, PersistentDataType.STRING);
        if (name == null) {
            return null;
        }
        return traders.get(name);
    }

    public boolean isSpawningTrader() {
        return spawningTrader;
    }

    // ================= Edycja =================

    public void cycleProfession(String name) {
        TraderData td = traders.get(name);
        if (td == null) {
            return;
        }
        Villager.Profession[] values = Villager.Profession.values();
        int next = (td.profession.ordinal() + 1) % values.length;
        td.profession = values[next];
        data.set(name + ".profession", td.profession.name());
        save();
        applyAppearance(td);
    }

    public void cycleType(String name) {
        TraderData td = traders.get(name);
        if (td == null) {
            return;
        }
        Villager.Type[] values = Villager.Type.values();
        int next = (td.type.ordinal() + 1) % values.length;
        td.type = values[next];
        data.set(name + ".type", td.type.name());
        save();
        applyAppearance(td);
    }

    public void startRename(Player admin, String name) {
        awaitingRename.put(admin.getUniqueId(), name);
        admin.closeInventory();
        admin.sendMessage("§eWpisz na czacie nową nazwę handlarza '" + name + "' (obsługuje &kody kolorów), albo §f'anuluj'§e:");
    }

    public boolean hasPendingRename(UUID uuid) {
        return awaitingRename.containsKey(uuid);
    }

    public boolean handleChatInput(Player player, String message) {
        String name = awaitingRename.remove(player.getUniqueId());
        if (name == null) {
            return false;
        }
        if (!message.equalsIgnoreCase("anuluj")) {
            TraderData td = traders.get(name);
            if (td != null) {
                String title = org.bukkit.ChatColor.translateAlternateColorCodes('&', message);
                td.title = title;
                data.set(name + ".title", title);
                save();
                applyAppearance(td);
                player.sendMessage("§aZmieniono nazwę handlarza '" + name + "'.");
            }
        } else {
            player.sendMessage("§eAnulowano.");
        }
        return true;
    }

    /**
     * Odczytuje trade'y z GUI edycji (rząd 1 = koszt, rząd 2 = nagroda, w tej samej kolumnie)
     * i zapisuje je jako listę handlarza, od razu aktualizując natywne GUI handlu.
     */
    public void saveTradesFromEditor(String name, Inventory inv) {
        TraderData td = traders.get(name);
        if (td == null) {
            return;
        }
        List<Trade> trades = new ArrayList<>();
        for (int col = 0; col < 9; col++) {
            ItemStack cost = inv.getItem(9 + col);
            ItemStack reward = inv.getItem(18 + col);
            if (cost == null || cost.getType().isAir() || reward == null || reward.getType().isAir()) {
                continue;
            }
            trades.add(new Trade(cost.clone(), reward.clone()));
        }
        td.trades = trades;
        saveTrades(name, trades);
        applyRecipes(td);
    }

    // ================= Encja/wygląd =================

    /**
     * Spawn przez World#spawn woła CreatureSpawnEvent tak samo jak wanilijny spawn - jeśli
     * handlarz stoi w regionie chronionym flagą WorldGuard "mob-spawning: deny" (np. Spawn01),
     * event zostanie po cichu anulowany i handlarz nigdy się nie pojawi. `spawningTrader` na czas
     * tego jednego, synchronicznego wywołania pozwala TraderListener.onCreatureSpawn cofnąć
     * anulowanie WYŁĄCZNIE dla tego naszego spawnu (nie otwiera regionu na żadne inne moby).
     */
    private Villager spawnVillager(String name, Location loc) {
        World world = loc.getWorld();
        loc.getChunk().load();
        spawningTrader = true;
        Villager villager;
        try {
            villager = world.spawn(loc, Villager.class, v -> {
                v.setAI(false);
                v.setGravity(false);
                v.setInvulnerable(true);
                v.setSilent(false);
                v.setPersistent(true);
                v.getPersistentDataContainer().set(nameTag, PersistentDataType.STRING, name);
                v.addScoreboardTag(TAG);
            });
        } finally {
            spawningTrader = false;
        }
        return villager;
    }

    private Villager findExisting(String name, Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        loc.getChunk().load();
        for (Entity entity : world.getNearbyEntities(loc, 3, 3, 3)) {
            if (entity instanceof Villager && name.equals(entity.getPersistentDataContainer().get(nameTag, PersistentDataType.STRING))) {
                return (Villager) entity;
            }
        }
        return null;
    }

    private void applyAppearance(TraderData td) {
        if (!td.villager.isValid()) {
            return;
        }
        td.villager.setCustomName(td.title);
        td.villager.setCustomNameVisible(true);
        td.villager.setProfession(td.profession);
        td.villager.setVillagerType(td.type);
    }

    private void applyRecipes(TraderData td) {
        if (!td.villager.isValid()) {
            return;
        }
        List<MerchantRecipe> recipes = new ArrayList<>();
        for (Trade trade : td.trades) {
            MerchantRecipe recipe = new MerchantRecipe(trade.reward().clone(), Integer.MAX_VALUE);
            recipe.addIngredient(trade.cost().clone());
            recipes.add(recipe);
        }
        td.villager.setRecipes(recipes);
    }

    private static Villager.Profession parseProfession(String raw) {
        if (raw == null) {
            return Villager.Profession.NONE;
        }
        try {
            return Villager.Profession.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Villager.Profession.NONE;
        }
    }

    private static Villager.Type parseType(String raw) {
        if (raw == null) {
            return Villager.Type.PLAINS;
        }
        try {
            return Villager.Type.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return Villager.Type.PLAINS;
        }
    }

    // ================= Zapis/odczyt =================

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

    private void saveTrades(String name, List<Trade> trades) {
        data.set(name + ".trades", null);
        for (int i = 0; i < trades.size(); i++) {
            data.set(name + ".trades." + i + ".cost", trades.get(i).cost());
            data.set(name + ".trades." + i + ".reward", trades.get(i).reward());
        }
        save();
    }

    private List<Trade> readTrades(String name) {
        List<Trade> list = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(name + ".trades");
        if (section == null) {
            return list;
        }
        for (String key : section.getKeys(false)) {
            ItemStack cost = data.getItemStack(name + ".trades." + key + ".cost");
            ItemStack reward = data.getItemStack(name + ".trades." + key + ".reward");
            if (cost != null && reward != null) {
                list.add(new Trade(cost, reward));
            }
        }
        return list;
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać traders.yml: " + e.getMessage());
        }
    }

    public static final class TraderData {
        final String name;
        final Villager villager;
        String title;
        Villager.Profession profession;
        Villager.Type type;
        List<Trade> trades;

        TraderData(String name, Villager villager, String title, Villager.Profession profession,
                   Villager.Type type, List<Trade> trades) {
            this.name = name;
            this.villager = villager;
            this.title = title;
            this.profession = profession;
            this.type = type;
            this.trades = trades;
        }
    }
}
