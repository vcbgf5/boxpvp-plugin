package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pojedynki 1v1 na zakład. Admin ustawia JEDEN "szablonowy" świat areny + pozycję startową
 * (/bpvp duel setworld|setpos) - każdy pojedynek dostaje świeżą, fizyczną KOPIĘ tego świata na
 * dysku, więc kilka pojedynków może iść naraz bez wchodzenia sobie w drogę i bez trwałych
 * zniszczeń w szablonie. Ekwipunek/HP/głód obu graczy jest migawkowany PRZED wejściem do areny
 * i przywracany PO wyjściu, więc śmierć w pojedynku nigdy nie kosztuje realnych przedmiotów -
 * jedyne co się przenosi, to zakład, który przegrany płaci zwycięzcy.
 * Świat-szablon powinien być mały/prosty (np. superflat) - kopiowanie całego folderu świata
 * na dysku przy starcie KAŻDEGO pojedynku jest tym tańsze, im mniejszy jest ten folder.
 */
public class DuelManager {

    private static final long INVITE_TIMEOUT_SECONDS = 60L;
    private static final long WORLD_DELETE_DELAY_TICKS = 20L * 5;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    private String templateWorldName;
    private Location arenaPosition;
    private int duelCounter = 0;

    private final Map<UUID, Invite> pendingInvites = new HashMap<>();
    private final Map<UUID, ActiveDuel> activeDuels = new HashMap<>();
    private final Map<UUID, PendingRestore> pendingRestores = new HashMap<>();

    public DuelManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "duels.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć duels.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    // ================= Konfiguracja (admin) =================

    public boolean isConfigured() {
        return templateWorldName != null && arenaPosition != null;
    }

    public String getTemplateWorldName() {
        return templateWorldName;
    }

    /** Ustawia świat-szablon areny - jeśli nie jest jeszcze wczytany, próbuje go załadować. */
    public World setTemplateWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(worldName));
        }
        if (world == null) {
            return null;
        }
        this.templateWorldName = worldName;
        data.set("template-world", worldName);
        save();
        return world;
    }

    public void setArenaPosition(Location location) {
        this.arenaPosition = location.clone();
        data.set("arena.world", location.getWorld().getName());
        data.set("arena.x", location.getX());
        data.set("arena.y", location.getY());
        data.set("arena.z", location.getZ());
        data.set("arena.yaw", location.getYaw());
        data.set("arena.pitch", location.getPitch());
        save();
    }

    // ================= Zaproszenia =================

    public boolean hasActiveDuel(UUID uuid) {
        return activeDuels.containsKey(uuid);
    }

    public void invite(Player inviter, Player target, double bet) {
        pendingInvites.put(target.getUniqueId(), new Invite(inviter.getUniqueId(), bet, System.currentTimeMillis() + INVITE_TIMEOUT_SECONDS * 1000L));
    }

    public Invite getInvite(UUID target) {
        Invite invite = pendingInvites.get(target);
        if (invite == null) {
            return null;
        }
        if (System.currentTimeMillis() > invite.expiresAt) {
            pendingInvites.remove(target);
            return null;
        }
        return invite;
    }

    public void clearInvite(UUID target) {
        pendingInvites.remove(target);
    }

    /** Anuluje zaproszenie, które WYSŁAŁ dany gracz (jeszcze nie zaakceptowane) - zwraca false, jeśli takiego nie ma. */
    public boolean cancelOutgoingInvite(UUID inviterUuid) {
        for (Map.Entry<UUID, Invite> entry : pendingInvites.entrySet()) {
            if (entry.getValue().inviter.equals(inviterUuid)) {
                pendingInvites.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    // ================= Pojedynek =================

    public UUID getOpponent(UUID uuid) {
        ActiveDuel duel = activeDuels.get(uuid);
        if (duel == null) {
            return null;
        }
        return duel.playerA.equals(uuid) ? duel.playerB : duel.playerA;
    }

    public boolean start(Player a, Player b, double bet) {
        if (!isConfigured()) {
            return false;
        }
        World template = Bukkit.getWorld(templateWorldName);
        if (template == null) {
            return false;
        }
        World duelWorld = cloneTemplateWorld(template);
        if (duelWorld == null) {
            return false;
        }

        ActiveDuel duel = new ActiveDuel(a.getUniqueId(), b.getUniqueId(), bet, duelWorld);
        duel.snapshotA = snapshot(a);
        duel.snapshotB = snapshot(b);
        duel.returnA = a.getLocation().clone();
        duel.returnB = b.getLocation().clone();

        activeDuels.put(a.getUniqueId(), duel);
        activeDuels.put(b.getUniqueId(), duel);

        Location spawnA = arenaPosition.clone();
        spawnA.setWorld(duelWorld);
        Location spawnB = spawnA.clone().add(2, 0, 0);

        fullyHeal(a);
        fullyHeal(b);
        a.teleport(spawnA);
        b.teleport(spawnB);

        Bukkit.broadcastMessage(Branding.chatPrefix() + "§c§l⚔ POJEDYNEK! §f" + a.getName() + " §7vs §f" + b.getName()
                + " §7- stawka: §a" + BankGuiManager.formatMoney(bet) + "$");
        return true;
    }

    private void fullyHeal(Player player) {
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            player.setHealth(maxHealth.getValue());
        }
        player.setFoodLevel(20);
    }

    /** Poddanie się w trakcie trwającego pojedynku - liczy się jak przegrana (płaci zakład). */
    public void forfeit(Player player) {
        UUID opponent = getOpponent(player.getUniqueId());
        if (opponent == null) {
            return;
        }
        finish(opponent, player.getUniqueId(), true);
    }

    /** Admin przerywa czyjś pojedynek - bez wypłaty zakładu. */
    public void adminStop(Player target) {
        UUID opponent = getOpponent(target.getUniqueId());
        if (opponent == null) {
            return;
        }
        finish(target.getUniqueId(), opponent, false);
    }

    /**
     * Kończy pojedynek: (opcjonalnie) przelewa zakład od przegranego do zwycięzcy, przywraca
     * obu ekwipunek/HP/głód sprzed pojedynku i teleportuje ich z powrotem. Jeśli przegrany
     * właśnie zginął (martwy w momencie wywołania), jego przywrócenie czeka na PlayerRespawnEvent
     * (nie da się bezpiecznie teleportować/edytować ekwipunku martwej postaci).
     */
    public void finish(UUID winnerUuid, UUID loserUuid, boolean payout) {
        ActiveDuel duel = activeDuels.remove(winnerUuid);
        if (duel == null) {
            duel = activeDuels.remove(loserUuid);
        } else {
            activeDuels.remove(loserUuid);
        }
        if (duel == null) {
            return;
        }

        if (payout && plugin.getEconomy() != null && duel.bet > 0) {
            plugin.getEconomy().withdrawPlayer(Bukkit.getOfflinePlayer(loserUuid), duel.bet);
            plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(winnerUuid), duel.bet);
        }

        Snapshot winnerSnapshot = duel.playerA.equals(winnerUuid) ? duel.snapshotA : duel.snapshotB;
        Location winnerReturn = duel.playerA.equals(winnerUuid) ? duel.returnA : duel.returnB;
        Snapshot loserSnapshot = duel.playerA.equals(loserUuid) ? duel.snapshotA : duel.snapshotB;
        Location loserReturn = duel.playerA.equals(loserUuid) ? duel.returnA : duel.returnB;

        Player winner = Bukkit.getPlayer(winnerUuid);
        if (winner != null && winner.isOnline()) {
            restoreNow(winner, winnerSnapshot, winnerReturn);
            winner.sendMessage(payout
                    ? "§a§lWygrałeś pojedynek! §f+" + BankGuiManager.formatMoney(duel.bet) + "$"
                    : "§ePojedynek przerwany przez administrację.");
        }

        Player loser = Bukkit.getPlayer(loserUuid);
        if (loser != null && loser.isOnline()) {
            if (loser.isDead()) {
                pendingRestores.put(loserUuid, new PendingRestore(loserSnapshot, loserReturn));
            } else {
                restoreNow(loser, loserSnapshot, loserReturn);
            }
            loser.sendMessage(payout
                    ? "§c§lPrzegrałeś pojedynek. §f-" + BankGuiManager.formatMoney(duel.bet) + "$"
                    : "§ePojedynek przerwany przez administrację.");
        }

        deleteWorldLater(duel.world);
    }

    public PendingRestore consumePendingRestore(UUID uuid) {
        return pendingRestores.remove(uuid);
    }

    public void applySnapshot(Player player, Snapshot snapshot) {
        if (!player.isOnline() || snapshot == null) {
            return;
        }
        player.getInventory().setContents(snapshot.contents);
        player.getInventory().setArmorContents(snapshot.armor);
        player.getInventory().setItemInOffHand(snapshot.offhand);
        var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        double health = maxHealth != null ? Math.min(snapshot.health, maxHealth.getValue()) : snapshot.health;
        player.setHealth(Math.max(1.0, health));
        player.setFoodLevel(snapshot.food);
    }

    private void restoreNow(Player player, Snapshot snapshot, Location returnLoc) {
        applySnapshot(player, snapshot);
        if (returnLoc != null && returnLoc.getWorld() != null) {
            player.teleport(returnLoc);
        }
    }

    private Snapshot snapshot(Player player) {
        Snapshot s = new Snapshot();
        s.contents = deepClone(player.getInventory().getContents());
        s.armor = deepClone(player.getInventory().getArmorContents());
        s.offhand = player.getInventory().getItemInOffHand().clone();
        s.health = player.getHealth();
        s.food = player.getFoodLevel();
        return s;
    }

    private static ItemStack[] deepClone(ItemStack[] source) {
        ItemStack[] out = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            out[i] = source[i] == null ? null : source[i].clone();
        }
        return out;
    }

    // ================= Klonowanie/kasowanie świata areny =================

    private World cloneTemplateWorld(World template) {
        String newName = "duel_" + (duelCounter++) + "_" + System.currentTimeMillis();
        File target = new File(Bukkit.getWorldContainer(), newName);
        try {
            copyDirectory(template.getWorldFolder().toPath(), target.toPath());
        } catch (IOException | UncheckedIOException e) {
            plugin.getLogger().warning("Nie udało się skopiować świata pojedynku: " + e.getMessage());
            return null;
        }
        WorldCreator creator = new WorldCreator(newName);
        creator.environment(template.getEnvironment());
        return Bukkit.createWorld(creator);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(sourcePath -> {
                String name = sourcePath.getFileName().toString();
                if (name.equals("session.lock") || name.equals("uid.dat")) {
                    return;
                }
                Path targetPath = target.resolve(source.relativize(sourcePath));
                try {
                    if (Files.isDirectory(sourcePath)) {
                        Files.createDirectories(targetPath);
                    } else {
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    private void deleteWorldLater(World world) {
        if (world == null) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Bukkit.unloadWorld(world, false);
            deleteRecursively(world.getWorldFolder());
        }, WORLD_DELETE_DELAY_TICKS);
    }

    private static void deleteRecursively(File dir) {
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        dir.delete();
    }

    // ================= Zapis/odczyt konfiguracji areny =================

    private void load() {
        templateWorldName = data.getString("template-world");
        String worldName = data.getString("arena.world");
        if (worldName == null) {
            return;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return;
        }
        arenaPosition = new Location(world, data.getDouble("arena.x"), data.getDouble("arena.y"), data.getDouble("arena.z"),
                (float) data.getDouble("arena.yaw"), (float) data.getDouble("arena.pitch"));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać duels.yml: " + e.getMessage());
        }
    }

    public static final class Invite {
        private final UUID inviter;
        private final double bet;
        private final long expiresAt;

        Invite(UUID inviter, double bet, long expiresAt) {
            this.inviter = inviter;
            this.bet = bet;
            this.expiresAt = expiresAt;
        }

        public UUID getInviter() {
            return inviter;
        }

        public double getBet() {
            return bet;
        }
    }

    static final class Snapshot {
        ItemStack[] contents;
        ItemStack[] armor;
        ItemStack offhand;
        double health;
        int food;
    }

    static final class PendingRestore {
        private final Snapshot snapshot;
        private final Location returnLocation;

        PendingRestore(Snapshot snapshot, Location returnLocation) {
            this.snapshot = snapshot;
            this.returnLocation = returnLocation;
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }

        public Location getReturnLocation() {
            return returnLocation;
        }
    }

    private static final class ActiveDuel {
        final UUID playerA;
        final UUID playerB;
        final double bet;
        final World world;
        Snapshot snapshotA;
        Snapshot snapshotB;
        Location returnA;
        Location returnB;

        ActiveDuel(UUID playerA, UUID playerB, double bet, World world) {
            this.playerA = playerA;
            this.playerB = playerB;
            this.bet = bet;
            this.world = world;
        }
    }
}
