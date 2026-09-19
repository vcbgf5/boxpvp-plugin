package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Rotujący sklep (pomysł #13) - admin buduje pulę ofert (trzymany przedmiot + cena bazowa), a
 * co godzinę losuje się kilka z nich z losowym rabatem 20-50% na kolejną godzinę.
 * /rotshop pokazuje aktualną rotację + odliczanie do następnej.
 */
public class RotatingShopManager {

    private static final long ROTATE_INTERVAL_TICKS = 20L * 60 * 60;
    private static final int ROTATION_SIZE = 3;
    private static final int MIN_DISCOUNT = 20;
    private static final int MAX_DISCOUNT = 50;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final Random random = new Random();

    private final List<PoolEntry> pool = new ArrayList<>();
    private final List<RotationEntry> currentRotation = new ArrayList<>();
    private long nextRotationAt;

    public RotatingShopManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "rotatingshop.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć rotatingshop.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        loadPool();
    }

    public void start() {
        rotate();
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::rotate, ROTATE_INTERVAL_TICKS, ROTATE_INTERVAL_TICKS);
    }

    public void addToPool(ItemStack item, double basePrice) {
        pool.add(new PoolEntry(item.clone(), basePrice));
        savePool();
    }

    public boolean removeFromPool(int index) {
        if (index < 0 || index >= pool.size()) {
            return false;
        }
        pool.remove(index);
        savePool();
        return true;
    }

    public List<PoolEntry> getPool() {
        return pool;
    }

    public List<RotationEntry> getCurrentRotation() {
        return currentRotation;
    }

    public long getMillisUntilNextRotation() {
        return Math.max(0, nextRotationAt - System.currentTimeMillis());
    }

    private void rotate() {
        nextRotationAt = System.currentTimeMillis() + ROTATE_INTERVAL_TICKS * 50L;
        currentRotation.clear();
        if (pool.isEmpty()) {
            return;
        }
        List<PoolEntry> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        int count = Math.min(ROTATION_SIZE, shuffled.size());
        for (int i = 0; i < count; i++) {
            PoolEntry entry = shuffled.get(i);
            int discount = MIN_DISCOUNT + random.nextInt(MAX_DISCOUNT - MIN_DISCOUNT + 1);
            currentRotation.add(new RotationEntry(entry.icon.clone(), entry.basePrice, discount));
        }
        Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent("★ Nowa oferta specjalna!") + " §7Sprawdź §f/rotshop§7!");
    }

    /** Kupuje ofertę pod danym indeksem aktualnej rotacji - zwraca false, jeśli się nie udało (wtedy sam wysyła powód). */
    public boolean buy(Player player, int slot) {
        if (slot < 0 || slot >= currentRotation.size()) {
            return false;
        }
        RotationEntry entry = currentRotation.get(slot);
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cSklep niedostępny - brak podłączonego systemu ekonomii.");
            return false;
        }
        double price = entry.finalPrice();
        double balance = plugin.getEconomy().getBalance(player);
        if (balance < price) {
            player.sendMessage("§cNie masz wystarczająco monet! Potrzebujesz §f" + String.format("%.2f", price) + "$.");
            return false;
        }
        plugin.getEconomy().withdrawPlayer(player, price);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(entry.icon.clone());
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
        player.sendMessage("§aKupiono z rabatem §c-" + entry.discountPercent + "% §aza §f"
                + String.format("%.2f", price) + "$§a!");
        RewardRevealEffect.playLight(plugin, player, entry.icon);
        return true;
    }

    private void loadPool() {
        ConfigurationSection section = data.getConfigurationSection("pool");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            ItemStack icon = data.getItemStack("pool." + key + ".item");
            if (icon == null) {
                continue;
            }
            pool.add(new PoolEntry(icon, data.getDouble("pool." + key + ".price")));
        }
    }

    private void savePool() {
        data.set("pool", null);
        for (int i = 0; i < pool.size(); i++) {
            data.set("pool." + i + ".item", pool.get(i).icon);
            data.set("pool." + i + ".price", pool.get(i).basePrice);
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać rotatingshop.yml: " + e.getMessage());
        }
    }

    public static final class PoolEntry {
        public final ItemStack icon;
        public final double basePrice;

        PoolEntry(ItemStack icon, double basePrice) {
            this.icon = icon;
            this.basePrice = basePrice;
        }
    }

    public static final class RotationEntry {
        public final ItemStack icon;
        public final double basePrice;
        public final int discountPercent;

        RotationEntry(ItemStack icon, double basePrice, int discountPercent) {
            this.icon = icon;
            this.basePrice = basePrice;
            this.discountPercent = discountPercent;
        }

        public double finalPrice() {
            return basePrice * (100 - discountPercent) / 100.0;
        }
    }
}
