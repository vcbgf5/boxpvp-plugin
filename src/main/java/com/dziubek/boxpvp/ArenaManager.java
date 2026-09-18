package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Areny Box PvP: lobby, punkty spawnu, kit startowy (przechwycony z ekwipunku admina),
 * start/koniec rundy (ostatni żywy wygrywa), tablica wyników. Generatory bloków obsługuje
 * osobno GeneratorManager, ale start rundy każdorazowo je odświeża (resetArena).
 */
public class ArenaManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    private final Map<String, Set<UUID>> waitingRoom = new HashMap<>();
    private final Map<String, Match> activeMatches = new HashMap<>();
    private final Map<UUID, String> playerArena = new HashMap<>();

    public ArenaManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć arenas.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public boolean exists(String name) {
        return data.contains(name);
    }

    public List<String> names() {
        return new ArrayList<>(data.getKeys(false));
    }

    public void create(String name) {
        data.set(name + ".created", true);
        save();
    }

    public void setLobby(String name, Location location) {
        setLocation(name + ".lobby", location);
        save();
    }

    public Location getLobby(String name) {
        return readLocation(name + ".lobby");
    }

    public int addSpawn(String name, Location location) {
        List<Location> spawns = getSpawns(name);
        int index = spawns.size();
        setLocation(name + ".spawns." + index, location);
        save();
        return index + 1;
    }

    public void clearSpawns(String name) {
        data.set(name + ".spawns", null);
        save();
    }

    public List<Location> getSpawns(String name) {
        List<Location> list = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(name + ".spawns");
        if (section == null) {
            return list;
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
            Location loc = readLocation(name + ".spawns." + key);
            if (loc != null) {
                list.add(loc);
            }
        }
        return list;
    }

    /**
     * Zapisuje jako kit startowy areny aktualną zawartość ekwipunku podanego gracza
     * (tak samo jak /kit create w SurvivalManager - bez potrzeby osobnego systemu kitów).
     */
    public void setKit(String name, ItemStack[] contents) {
        data.set(name + ".kit", null);
        int idx = 0;
        for (ItemStack item : contents) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            data.set(name + ".kit." + idx, item);
            idx++;
        }
        save();
    }

    public List<ItemStack> getKitItems(String name) {
        List<ItemStack> list = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection(name + ".kit");
        if (section == null) {
            return list;
        }
        for (String key : section.getKeys(false)) {
            ItemStack item = data.getItemStack(name + ".kit." + key);
            if (item != null) {
                list.add(item);
            }
        }
        return list;
    }

    public boolean isRunning(String name) {
        return activeMatches.containsKey(name);
    }

    // ================= Poczekalnia / mecz =================

    public boolean join(Player player, String arenaName) {
        if (isRunning(arenaName)) {
            player.sendMessage("§cRunda na arenie '" + arenaName + "' już trwa - poczekaj na koniec.");
            return false;
        }
        Location lobby = getLobby(arenaName);
        if (lobby == null) {
            player.sendMessage("§cArena '" + arenaName + "' nie ma jeszcze ustawionego lobby (/bpvp setlobby).");
            return false;
        }
        leave(player);
        waitingRoom.computeIfAbsent(arenaName, k -> new HashSet<>()).add(player.getUniqueId());
        playerArena.put(player.getUniqueId(), arenaName);
        player.teleport(lobby);
        player.sendMessage("§aDołączyłeś do poczekalni areny '" + arenaName + "'. Czekaj na start.");
        broadcastArena(arenaName, "§e" + player.getName() + " §7dołączył do poczekalni (" + waitingCount(arenaName) + " graczy).");
        return true;
    }

    public void leave(Player player) {
        String arenaName = playerArena.remove(player.getUniqueId());
        if (arenaName == null) {
            return;
        }
        Set<UUID> waiting = waitingRoom.get(arenaName);
        if (waiting != null) {
            waiting.remove(player.getUniqueId());
        }
        Match match = activeMatches.get(arenaName);
        if (match != null && match.alive.remove(player.getUniqueId())) {
            clearScoreboard(player);
            checkWinCondition(arenaName);
        }
    }

    private int waitingCount(String arenaName) {
        Set<UUID> waiting = waitingRoom.get(arenaName);
        return waiting == null ? 0 : waiting.size();
    }

    public boolean start(String arenaName, CommandSender starter) {
        if (isRunning(arenaName)) {
            starter.sendMessage("§cRunda na arenie '" + arenaName + "' już trwa.");
            return false;
        }
        List<Location> spawns = getSpawns(arenaName);
        if (spawns.isEmpty()) {
            starter.sendMessage("§cArena '" + arenaName + "' nie ma ustawionych punktów spawnu (/bpvp addspawn).");
            return false;
        }
        Set<UUID> waiting = waitingRoom.get(arenaName);
        if (waiting == null || waiting.size() < 2) {
            starter.sendMessage("§cPotrzeba minimum 2 graczy w poczekalni areny '" + arenaName + "'.");
            return false;
        }

        List<Player> players = new ArrayList<>();
        for (UUID uuid : waiting) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                players.add(p);
            }
        }
        waiting.clear();

        List<ItemStack> kitItems = getKitItems(arenaName);
        Match match = new Match(arenaName);

        int i = 0;
        for (Player p : players) {
            Location spawn = spawns.get(i % spawns.size());
            i++;
            p.setGameMode(GameMode.SURVIVAL);
            p.getInventory().clear();
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.teleport(spawn);
            for (ItemStack item : kitItems) {
                p.getInventory().addItem(item.clone());
            }
            match.alive.add(p.getUniqueId());
            p.setScoreboard(match.scoreboard);
            TitleUtil.show(p, "§c§lWALKA!", "§7Ostatni żywy wygrywa!");
        }

        activeMatches.put(arenaName, match);
        updateScoreboard(match);
        plugin.getGenerators().resetArena(arenaName);
        broadcastArena(arenaName, "§a§lStart! §7Gracze: " + match.alive.size());
        return true;
    }

    public void stop(String arenaName) {
        Match match = activeMatches.remove(arenaName);
        if (match == null) {
            return;
        }
        Location lobby = getLobby(arenaName);
        for (UUID uuid : match.alive) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                clearScoreboard(p);
                if (lobby != null) {
                    p.teleport(lobby);
                }
            }
            playerArena.remove(uuid);
        }
    }

    public void onPlayerDeath(Player player) {
        String arenaName = playerArena.get(player.getUniqueId());
        if (arenaName == null) {
            return;
        }
        Match match = activeMatches.get(arenaName);
        if (match == null || !match.alive.remove(player.getUniqueId())) {
            return;
        }
        broadcastArena(arenaName, "§c" + player.getName() + " §7odpadł! Zostało: " + match.alive.size());
        checkWinCondition(arenaName);
    }

    public void onPlayerRespawn(Player player, PlayerRespawnEvent event) {
        String arenaName = playerArena.remove(player.getUniqueId());
        if (arenaName == null) {
            return;
        }
        clearScoreboard(player);
        Location lobby = getLobby(arenaName);
        if (lobby != null) {
            event.setRespawnLocation(lobby);
        }
    }

    private void checkWinCondition(String arenaName) {
        Match match = activeMatches.get(arenaName);
        if (match == null) {
            return;
        }
        if (match.alive.size() > 1) {
            updateScoreboard(match);
            return;
        }

        activeMatches.remove(arenaName);
        Location lobby = getLobby(arenaName);
        String winnerName = "brak";
        if (match.alive.size() == 1) {
            UUID winnerUuid = match.alive.iterator().next();
            Player winner = Bukkit.getPlayer(winnerUuid);
            if (winner != null) {
                winnerName = winner.getName();
                TitleUtil.show(winner, "§6§lWYGRANA!", "§7Zostałeś ostatnim graczem!");
            }
        }
        broadcastArena(arenaName, "§6§lKoniec rundy! §eZwycięzca: §f" + winnerName);

        for (UUID uuid : new ArrayList<>(match.alive)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                clearScoreboard(p);
                if (lobby != null) {
                    p.teleport(lobby);
                }
            }
            playerArena.remove(uuid);
        }
    }

    private void broadcastArena(String arenaName, String message) {
        Set<UUID> everyone = new HashSet<>();
        Set<UUID> waiting = waitingRoom.get(arenaName);
        if (waiting != null) {
            everyone.addAll(waiting);
        }
        Match match = activeMatches.get(arenaName);
        if (match != null) {
            everyone.addAll(match.alive);
        }
        for (UUID uuid : everyone) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    private void updateScoreboard(Match match) {
        for (String entry : new HashSet<>(match.scoreboard.getEntries())) {
            match.scoreboard.resetScores(entry);
        }
        match.objective.getScore("§7Arena: §f" + match.arenaName).setScore(2);
        match.objective.getScore("§7Żywi: §a" + match.alive.size()).setScore(1);
    }

    private void clearScoreboard(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    // ================= Zapis lokalizacji =================

    private void setLocation(String path, Location location) {
        data.set(path + ".world", location.getWorld().getName());
        data.set(path + ".x", location.getX());
        data.set(path + ".y", location.getY());
        data.set(path + ".z", location.getZ());
        data.set(path + ".yaw", location.getYaw());
        data.set(path + ".pitch", location.getPitch());
    }

    private Location readLocation(String path) {
        String worldName = data.getString(path + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = data.getDouble(path + ".x");
        double y = data.getDouble(path + ".y");
        double z = data.getDouble(path + ".z");
        float yaw = (float) data.getDouble(path + ".yaw");
        float pitch = (float) data.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać arenas.yml: " + e.getMessage());
        }
    }

    private static final class Match {
        final String arenaName;
        final Set<UUID> alive = new HashSet<>();
        final Scoreboard scoreboard;
        final Objective objective;

        Match(String arenaName) {
            this.arenaName = arenaName;
            this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            this.objective = scoreboard.registerNewObjective("bpvp", "dummy", "§6§lBOX PVP");
            this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
    }
}
