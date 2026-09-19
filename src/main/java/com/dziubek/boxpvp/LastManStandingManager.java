package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * "Ostatni ocalały" (pomysł #9) - mini battle-royale w wyznaczonej strefie. Faza zapisów
 * (/lms join) trwa SIGNUP_SECONDS, potem wszyscy zapisani zostają rozrzuceni losowo po strefie
 * z pełnym HP. Kto zginie - odpada, ale (tak jak w /duel) bez utraty realnych przedmiotów:
 * ekwipunek jest migawkowany PRZED startem i przywracany, gdy gracz wypadnie z gry (na
 * respawnie, bo nie da się bezpiecznie edytować ekwipunku martwej postaci) albo gdy wygra.
 * Ostatni żywy uczestnik dostaje nagrodę pieniężną.
 */
public class LastManStandingManager {

    private static final long SIGNUP_SECONDS = 30L;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final Random random = new Random();

    private Location corner1;
    private Location corner2;
    private boolean signupOpen;
    private boolean running;

    private final Set<UUID> signedUp = new HashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Set<UUID> pendingRespawnRestore = new HashSet<>();
    private final Map<UUID, ItemStack[]> savedContents = new HashMap<>();
    private final Map<UUID, ItemStack[]> savedArmor = new HashMap<>();
    private final Map<UUID, ItemStack> savedOffhand = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();

    public LastManStandingManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "lmsevent.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć lmsevent.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public boolean isConfigured() {
        return corner1 != null && corner2 != null;
    }

    public boolean isSignupOpen() {
        return signupOpen;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isAlive(UUID uuid) {
        return alive.contains(uuid);
    }

    public void setCorner(int corner, Location location) {
        if (corner == 1) {
            this.corner1 = location.clone();
        } else {
            this.corner2 = location.clone();
        }
        String base = "lms-zone.corner" + corner;
        data.set(base + ".world", location.getWorld().getName());
        data.set(base + ".x", location.getBlockX());
        data.set(base + ".y", location.getBlockY());
        data.set(base + ".z", location.getBlockZ());
        save();
    }

    // ================= Zapisy i start =================

    public boolean openSignup() {
        if (!isConfigured() || signupOpen || running || corner1.getWorld() == null) {
            return false;
        }
        signupOpen = true;
        signedUp.clear();
        Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent("☠ OSTATNI OCALAŁY!")
                + " §7Zapisy otwarte na " + SIGNUP_SECONDS + "s - wpisz §a/lms join§7!");
        plugin.getServer().getScheduler().runTaskLater(plugin, this::beginRound, SIGNUP_SECONDS * 20L);
        return true;
    }

    public boolean join(Player player) {
        if (!signupOpen) {
            return false;
        }
        return signedUp.add(player.getUniqueId());
    }

    public boolean leave(Player player) {
        return signedUp.remove(player.getUniqueId());
    }

    private void beginRound() {
        signupOpen = false;
        if (signedUp.size() < 2) {
            Bukkit.broadcastMessage(Branding.chatPrefix() + "§7Ostatni ocalały anulowany - za mało chętnych (" + signedUp.size() + "/2).");
            signedUp.clear();
            return;
        }
        running = true;
        alive.clear();
        alive.addAll(signedUp);
        signedUp.clear();

        World world = corner1.getWorld();
        for (UUID uuid : alive) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                continue;
            }
            snapshot(player);
            fullyHeal(player);
            player.teleport(randomPointInZone(world));
        }

        Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent("☠ OSTATNI OCALAŁY!")
                + " §7Start! Graczy: §f" + alive.size());
    }

    // ================= W trakcie rundy =================

    public void onDeath(Player victim) {
        UUID uuid = victim.getUniqueId();
        if (!alive.remove(uuid)) {
            return;
        }
        pendingRespawnRestore.add(uuid);
        Bukkit.broadcastMessage(Branding.chatPrefix() + "§c" + victim.getName()
                + " §7odpada z Ostatniego Ocalałego! Zostało: §f" + alive.size());
        checkWinner();
    }

    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        if (signedUp.remove(uuid)) {
            return;
        }
        if (!alive.remove(uuid)) {
            return;
        }
        restoreAliveNow(player);
        Bukkit.broadcastMessage(Branding.chatPrefix() + "§c" + player.getName()
                + " §7odpada z Ostatniego Ocalałego (wyszedł z gry)! Zostało: §f" + alive.size());
        checkWinner();
    }

    private void checkWinner() {
        if (!running || alive.size() > 1) {
            return;
        }
        running = false;
        if (alive.isEmpty()) {
            Bukkit.broadcastMessage(Branding.chatPrefix() + "§7Ostatni ocalały zakończony - brak zwycięzcy.");
            return;
        }
        UUID winnerUuid = alive.iterator().next();
        alive.clear();

        double reward = plugin.getConfig().getDouble("lms.reward", 1000);
        if (plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(winnerUuid), reward);
        }
        Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent("☠ Ostatni Ocalały!") + " §f" + nameOf(winnerUuid)
                + " §7wygrywa i dostaje §a" + BankGuiManager.formatMoney(reward) + "$§7!");

        Player winner = Bukkit.getPlayer(winnerUuid);
        if (winner != null && winner.isOnline()) {
            if (winner.isDead()) {
                pendingRespawnRestore.add(winnerUuid);
            } else {
                restoreAliveNow(winner);
            }
            TitleUtil.show(winner, Branding.accent("☠ ZWYCIĘSTWO!"), "§7+" + BankGuiManager.formatMoney(reward) + "$");
            winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    // ================= Przywracanie ekwipunku =================

    public boolean consumePendingRestore(UUID uuid) {
        return pendingRespawnRestore.remove(uuid);
    }

    public Location getReturnLocation(UUID uuid) {
        return returnLocations.get(uuid);
    }

    /** Woła się na PlayerRespawnEvent (rok po ustawieniu respawn location) albo dla żywego zwycięzcy. */
    public void finishRestore(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack[] contents = savedContents.remove(uuid);
        ItemStack[] armor = savedArmor.remove(uuid);
        ItemStack offhand = savedOffhand.remove(uuid);
        returnLocations.remove(uuid);
        if (!player.isOnline()) {
            return;
        }
        if (contents != null) {
            player.getInventory().setContents(contents);
        }
        if (armor != null) {
            player.getInventory().setArmorContents(armor);
        }
        if (offhand != null) {
            player.getInventory().setItemInOffHand(offhand);
        }
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
    }

    private void restoreAliveNow(Player player) {
        Location returnLoc = returnLocations.get(player.getUniqueId());
        finishRestore(player);
        if (returnLoc != null && player.isOnline()) {
            player.teleport(returnLoc);
        }
    }

    private void snapshot(Player player) {
        UUID uuid = player.getUniqueId();
        savedContents.put(uuid, deepClone(player.getInventory().getContents()));
        savedArmor.put(uuid, deepClone(player.getInventory().getArmorContents()));
        savedOffhand.put(uuid, player.getInventory().getItemInOffHand().clone());
        returnLocations.put(uuid, player.getLocation().clone());
    }

    private void fullyHeal(Player player) {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
    }

    private Location randomPointInZone(World world) {
        int minX = Math.min(corner1.getBlockX(), corner2.getBlockX());
        int maxX = Math.max(corner1.getBlockX(), corner2.getBlockX());
        int minZ = Math.min(corner1.getBlockZ(), corner2.getBlockZ());
        int maxZ = Math.max(corner1.getBlockZ(), corner2.getBlockZ());
        int y = Math.max(corner1.getBlockY(), corner2.getBlockY());
        int x = minX + random.nextInt(maxX - minX + 1);
        int z = minZ + random.nextInt(maxZ - minZ + 1);
        return new Location(world, x + 0.5, y, z + 0.5);
    }

    private static ItemStack[] deepClone(ItemStack[] source) {
        ItemStack[] out = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i] == null ? null : source[i].clone();
        }
        return out;
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    // ================= Zapis/odczyt strefy =================

    private void load() {
        corner1 = loadCorner(1);
        corner2 = loadCorner(2);
    }

    private Location loadCorner(int corner) {
        String base = "lms-zone.corner" + corner;
        String worldName = data.getString(base + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, data.getInt(base + ".x"), data.getInt(base + ".y"), data.getInt(base + ".z"));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać lmsevent.yml: " + e.getMessage());
        }
    }
}
