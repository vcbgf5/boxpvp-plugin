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
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kantor - zamiast żywej encji Villager (jej programowy spawn bywa cicho blokowany, np. flagą
 * WorldGuard "mob-spawning" na chronionym spawnie) używa dokładnie tego samego triku co
 * skrzynki-event (EnvoyDisplayManager): ItemDisplay jako "prop" + TextDisplay jako etykieta +
 * niewidzialna Interaction jako hitbox do PPM. Żadna z tych encji nie jest "stworzeniem"
 * (CreatureSpawnEvent), więc nic ich nie blokuje. Nietrwałe (setPersistent(false)) - odtwarzane
 * od zera przy każdym starcie pluginu z zapisanej lokalizacji, tak jak tablice w
 * LeaderboardManager.
 *
 * Cała ekonomia Kantoru (kupno/sprzedaż nominałów, czeki) żyje tutaj, żeby BankListener
 * (kliknięcia w GUI) i BankChatListener (wpisana na czacie ilość/kwota) mogły korzystać z tej
 * samej logiki bez duplikacji.
 */
public class BankManager {

    private static final String TAG = "bpvp_bank";
    private static final double LABEL_HEIGHT_OFFSET = 0.9;
    private static final int MAX_CHAT_AMOUNT = 10_000;
    public static final double MAX_CHECK_VALUE = 1_000_000.0;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final NamespacedKey nameTag;
    private final NamespacedKey checkAmountTag;

    private final Map<String, BankData> banks = new HashMap<>();
    private final Map<UUID, PendingAction> pendingActions = new HashMap<>();

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
        this.checkAmountTag = new NamespacedKey(plugin, "bpvp_bank_check_amount");
    }

    // ================= Encje Kantoru =================

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

    // ================= Kupno / sprzedaż nominałów =================

    public int countOwned(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public void buy(Player player, Material material, int amount) {
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cKantor jest niedostępny - brak podłączonego systemu ekonomii.");
            return;
        }
        if (amount <= 0) {
            player.sendMessage("§cPodaj liczbę większą od zera.");
            return;
        }
        double price = plugin.getCurrency().getPrice(material);
        double cost = amount * price;
        double balance = plugin.getEconomy().getBalance(player);
        if (balance < cost) {
            player.sendMessage("§cNie masz wystarczająco środków! Potrzebujesz §f" + BankGuiManager.formatMoney(cost)
                    + "§c, masz §f" + BankGuiManager.formatMoney(balance) + "§c.");
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, cost);
        giveItems(player, material, amount);
        player.sendMessage("§aKupiono §f" + amount + "x " + BankGuiManager.formatName(material)
                + " §aza §f" + BankGuiManager.formatMoney(cost) + " §amonet.");
    }

    public void sell(Player player, Material material, int amount) {
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cKantor jest niedostępny - brak podłączonego systemu ekonomii.");
            return;
        }
        if (amount <= 0) {
            player.sendMessage("§cNie masz żadnego " + BankGuiManager.formatName(material) + " do sprzedania.");
            return;
        }
        int owned = countOwned(player, material);
        if (owned < amount) {
            player.sendMessage("§cMasz tylko §f" + owned + "x " + BankGuiManager.formatName(material) + "§c.");
            return;
        }

        removeItems(player, material, amount);
        double price = plugin.getCurrency().getPrice(material);
        double payout = amount * price;
        plugin.getEconomy().depositPlayer(player, payout);
        player.sendMessage("§aSprzedano §f" + amount + "x " + BankGuiManager.formatName(material)
                + " §aza §f" + BankGuiManager.formatMoney(payout) + " §amonet.");
    }

    private void giveItems(Player player, Material material, int amount) {
        int maxStack = material.getMaxStackSize();
        int remaining = amount;
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStack);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(material, chunk));
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
            remaining -= chunk;
        }
    }

    private void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != material) {
                continue;
            }
            int take = Math.min(remaining, item.getAmount());
            if (take >= item.getAmount()) {
                player.getInventory().setItem(i, null);
            } else {
                item.setAmount(item.getAmount() - take);
            }
            remaining -= take;
        }
    }

    // ================= Czeki =================

    public void withdrawCheck(Player player, double amount) {
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cKantor jest niedostępny - brak podłączonego systemu ekonomii.");
            return;
        }
        if (amount <= 0) {
            player.sendMessage("§cKwota musi być większa od zera.");
            return;
        }
        if (amount > MAX_CHECK_VALUE) {
            player.sendMessage("§cMaksymalna wartość czeku to §f" + BankGuiManager.formatMoney(MAX_CHECK_VALUE) + " §cmonet.");
            return;
        }
        double balance = plugin.getEconomy().getBalance(player);
        if (balance < amount) {
            player.sendMessage("§cNie masz wystarczająco środków! Masz §f" + BankGuiManager.formatMoney(balance) + " §cmonet.");
            return;
        }

        plugin.getEconomy().withdrawPlayer(player, amount);
        ItemStack check = createCheck(amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(check);
        for (ItemStack item : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        }
        player.sendMessage("§aWypłacono czek na §f" + BankGuiManager.formatMoney(amount)
                + " §amonet. Możesz go dać innemu graczowi - zrealizuje go PPM na Kantorze.");
    }

    private ItemStack createCheck(double amount) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lCzek: §f" + BankGuiManager.formatMoney(amount) + " monet");
            List<String> lore = new ArrayList<>();
            lore.add("§7Zrealizuj PPM na Kantorze,");
            lore.add("§7aby otrzymać monety na konto.");
            lore.add("§7Możesz go dać innemu graczowi.");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(checkAmountTag, PersistentDataType.DOUBLE, amount);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Zwraca wartość czeku (albo null, jeśli przedmiot nie jest czekiem Kantoru). */
    public Double getCheckAmount(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(checkAmountTag, PersistentDataType.DOUBLE)) {
            return null;
        }
        return meta.getPersistentDataContainer().get(checkAmountTag, PersistentDataType.DOUBLE);
    }

    public void redeemCheck(Player player, ItemStack checkItem, double amount) {
        if (plugin.getEconomy() == null) {
            player.sendMessage("§cKantor jest niedostępny - brak podłączonego systemu ekonomii.");
            return;
        }
        if (checkItem.getAmount() > 1) {
            checkItem.setAmount(checkItem.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }
        plugin.getEconomy().depositPlayer(player, amount);
        player.sendMessage("§aZrealizowano czek na §f" + BankGuiManager.formatMoney(amount) + " §amonet.");
    }

    // ================= Wpisywana na czacie ilość/kwota =================

    public void awaitBuyAmount(Player player, Material material) {
        pendingActions.put(player.getUniqueId(), new PendingAction(PendingType.BUY, material));
        player.closeInventory();
        player.sendMessage("§eWpisz na czacie ile chcesz kupić (liczba, maks. " + MAX_CHAT_AMOUNT + "), albo §f'anuluj'§e:");
    }

    public void awaitSellAmount(Player player, Material material) {
        pendingActions.put(player.getUniqueId(), new PendingAction(PendingType.SELL, material));
        player.closeInventory();
        player.sendMessage("§eWpisz na czacie ile chcesz sprzedać (liczba, maks. " + MAX_CHAT_AMOUNT + "), albo §f'anuluj'§e:");
    }

    public void awaitCheckAmount(Player player) {
        pendingActions.put(player.getUniqueId(), new PendingAction(PendingType.CHECK, null));
        player.closeInventory();
        player.sendMessage("§eWpisz na czacie kwotę czeku (maks. " + BankGuiManager.formatMoney(MAX_CHECK_VALUE) + "), albo §f'anuluj'§e:");
    }

    public boolean hasPendingAmount(UUID uuid) {
        return pendingActions.containsKey(uuid);
    }

    public boolean handleAmountChatInput(Player player, String message) {
        PendingAction pending = pendingActions.remove(player.getUniqueId());
        if (pending == null) {
            return false;
        }
        if (message.equalsIgnoreCase("anuluj")) {
            player.sendMessage("§eAnulowano.");
            return true;
        }

        if (pending.type == PendingType.CHECK) {
            double amount;
            try {
                amount = Double.parseDouble(message.trim());
            } catch (NumberFormatException e) {
                player.sendMessage("§cTo nie jest liczba.");
                return true;
            }
            withdrawCheck(player, amount);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(message.trim());
        } catch (NumberFormatException e) {
            player.sendMessage("§cTo nie jest liczba całkowita.");
            return true;
        }
        if (amount > MAX_CHAT_AMOUNT) {
            player.sendMessage("§cMaksymalnie " + MAX_CHAT_AMOUNT + " na raz.");
            return true;
        }
        if (pending.type == PendingType.BUY) {
            buy(player, pending.material, amount);
        } else {
            sell(player, pending.material, amount);
        }
        return true;
    }

    private enum PendingType {
        BUY, SELL, CHECK
    }

    private static final class PendingAction {
        final PendingType type;
        final Material material;

        PendingAction(PendingType type, Material material) {
            this.type = type;
            this.material = material;
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
