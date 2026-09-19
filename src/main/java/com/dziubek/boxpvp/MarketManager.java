package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rynek graczy - /wymiana wystawia trzymany przedmiot na sprzedaż na 3 dni, /rynek to GUI do
 * przeglądania i kupowania. Sprzedawca dostaje monety nawet offline (Vault na OfflinePlayer -
 * Player i tak dziedziczy po OfflinePlayer, więc ta sama metoda ekonomii działa dla obu).
 * Niesprzedane po 3 dniach wracają do sprzedawcy - od razu, jeśli jest online, w przeciwnym
 * razie czekają w kolejce i trafiają do niego przy najbliższym dołączeniu
 * (PlayerSessionListener -> deliverPendingReturns).
 */
public class MarketManager {

    private static final long LISTING_DURATION_MS = 3L * 24 * 60 * 60 * 1000;
    private static final long CHECK_INTERVAL_TICKS = 20L * 60;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new LinkedHashMap<>();

    public MarketManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "market.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć market.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkExpired, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void createListing(Player seller, ItemStack item, double price) {
        String id = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();
        listings.put(id, new Listing(id, seller.getUniqueId(), seller.getName(), item.clone(), price, now + LISTING_DURATION_MS));
        save();
    }

    public List<Listing> activeListings() {
        return new ArrayList<>(listings.values());
    }

    public void buy(Player buyer, String id) {
        Listing listing = listings.get(id);
        if (listing == null) {
            buyer.sendMessage("§cTa oferta już nie istnieje.");
            return;
        }
        if (listing.sellerUuid.equals(buyer.getUniqueId())) {
            buyer.sendMessage("§cNie możesz kupić własnej oferty.");
            return;
        }
        if (plugin.getEconomy() == null) {
            buyer.sendMessage("§cRynek jest niedostępny - brak podłączonego systemu ekonomii.");
            return;
        }
        double balance = plugin.getEconomy().getBalance(buyer);
        if (balance < listing.price) {
            buyer.sendMessage("§cNie masz wystarczająco środków! Potrzebujesz §f" + BankGuiManager.formatMoney(listing.price)
                    + "§c, masz §f" + BankGuiManager.formatMoney(balance) + "§c.");
            return;
        }

        listings.remove(id);
        save();

        plugin.getEconomy().withdrawPlayer(buyer, listing.price);
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.sellerUuid);
        plugin.getEconomy().depositPlayer(seller, listing.price);

        Map<Integer, ItemStack> leftover = buyer.getInventory().addItem(listing.item.clone());
        for (ItemStack extra : leftover.values()) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), extra);
        }
        buyer.sendMessage("§aKupiono §f" + describeItem(listing.item) + " §aza §f" + BankGuiManager.formatMoney(listing.price) + " §amonet.");
        buyer.getWorld().spawnParticle(Particle.END_ROD, buyer.getLocation().add(0, 1.2, 0), 18, 0.3, 0.4, 0.3, 0.02);
        buyer.playSound(buyer.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.3f);

        Player sellerOnline = Bukkit.getPlayer(listing.sellerUuid);
        if (sellerOnline != null) {
            sellerOnline.sendMessage("§a" + buyer.getName() + " §7kupił(a) Twoją ofertę: §f" + describeItem(listing.item)
                    + " §7za §a" + BankGuiManager.formatMoney(listing.price) + " §7monet.");
        }
    }

    private void checkExpired() {
        long now = System.currentTimeMillis();
        List<String> expired = new ArrayList<>();
        for (Listing listing : listings.values()) {
            if (listing.expiresAt <= now) {
                expired.add(listing.id);
            }
        }
        if (expired.isEmpty()) {
            return;
        }
        for (String id : expired) {
            Listing listing = listings.remove(id);
            if (listing == null) {
                continue;
            }
            returnItem(listing.sellerUuid, listing.item);
            Player seller = Bukkit.getPlayer(listing.sellerUuid);
            if (seller != null) {
                seller.sendMessage("§7Twoja oferta na rynku wygasła (3 dni) - przedmiot §f" + describeItem(listing.item) + " §7wrócił do Ciebie.");
            }
        }
        save();
    }

    private void returnItem(UUID ownerUuid, ItemStack item) {
        Player online = Bukkit.getPlayer(ownerUuid);
        if (online != null) {
            Map<Integer, ItemStack> leftover = online.getInventory().addItem(item.clone());
            for (ItemStack extra : leftover.values()) {
                online.getWorld().dropItemNaturally(online.getLocation(), extra);
            }
            return;
        }
        pendingReturns.computeIfAbsent(ownerUuid, k -> new ArrayList<>()).add(item.clone());
    }

    /** Wywoływane przy dołączeniu gracza - oddaje przedmioty z wygasłych ofert, które czekały bo był offline. */
    public void deliverPendingReturns(Player player) {
        List<ItemStack> items = pendingReturns.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
        player.sendMessage("§aOtrzymujesz §f" + items.size() + " §aprzedmiot(y) z wygasłych ofert na rynku.");
        save();
    }

    public static String describeItem(ItemStack item) {
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : BankGuiManager.formatName(item.getType());
        return item.getAmount() + "x " + name;
    }

    private void load() {
        ConfigurationSection listingsSection = data.getConfigurationSection("listings");
        if (listingsSection != null) {
            for (String id : listingsSection.getKeys(false)) {
                String base = "listings." + id;
                String sellerUuidRaw = data.getString(base + ".seller-uuid");
                ItemStack item = data.getItemStack(base + ".item");
                if (sellerUuidRaw == null || item == null) {
                    continue;
                }
                UUID sellerUuid = UUID.fromString(sellerUuidRaw);
                String sellerName = data.getString(base + ".seller-name", "?");
                double price = data.getDouble(base + ".price");
                long expiresAt = data.getLong(base + ".expires-at");
                listings.put(id, new Listing(id, sellerUuid, sellerName, item, price, expiresAt));
            }
        }
        ConfigurationSection returnsSection = data.getConfigurationSection("pending-returns");
        if (returnsSection != null) {
            for (String uuidStr : returnsSection.getKeys(false)) {
                List<?> raw = data.getList("pending-returns." + uuidStr);
                if (raw == null) {
                    continue;
                }
                List<ItemStack> items = new ArrayList<>();
                for (Object obj : raw) {
                    if (obj instanceof ItemStack) {
                        items.add((ItemStack) obj);
                    }
                }
                if (!items.isEmpty()) {
                    pendingReturns.put(UUID.fromString(uuidStr), items);
                }
            }
        }
    }

    private void save() {
        data.set("listings", null);
        for (Listing listing : listings.values()) {
            String base = "listings." + listing.id;
            data.set(base + ".seller-uuid", listing.sellerUuid.toString());
            data.set(base + ".seller-name", listing.sellerName);
            data.set(base + ".item", listing.item);
            data.set(base + ".price", listing.price);
            data.set(base + ".expires-at", listing.expiresAt);
        }
        data.set("pending-returns", null);
        for (Map.Entry<UUID, List<ItemStack>> entry : pendingReturns.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                data.set("pending-returns." + entry.getKey(), entry.getValue());
            }
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać market.yml: " + e.getMessage());
        }
    }

    public static final class Listing {
        public final String id;
        public final UUID sellerUuid;
        public final String sellerName;
        public final ItemStack item;
        public final double price;
        public final long expiresAt;

        Listing(String id, UUID sellerUuid, String sellerName, ItemStack item, double price, long expiresAt) {
            this.id = id;
            this.sellerUuid = sellerUuid;
            this.sellerName = sellerName;
            this.item = item;
            this.price = price;
            this.expiresAt = expiresAt;
        }
    }
}
