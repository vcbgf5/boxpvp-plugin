package com.dziubek.boxpvp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pojedynki 1v1 na zakład. Admin ustawia JEDEN "szablonowy" świat areny + dwie OSOBNE pozycje
 * startowe (/bpvp duel setworld|setpos1|setpos2) - każdy pojedynek dostaje świeżą, fizyczną
 * KOPIĘ tego świata na dysku, więc kilka pojedynków może iść naraz bez wchodzenia sobie w
 * drogę i bez trwałych zniszczeń w szablonie. Na starcie obaj gracze "zlatują z nieba" (2s
 * animacja) na swoje pozycje, po czym stoją zamrożeni i nietykalni przez 5s odliczania, zanim
 * walka się faktycznie zaczyna. Ekwipunek/HP/głód obu graczy jest migawkowany PRZED wejściem do
 * areny i przywracany PO wyjściu, więc śmierć w pojedynku nigdy nie kosztuje realnych
 * przedmiotów - jedyne co się przenosi, to zakład, który przegrany płaci zwycięzcy.
 * Świat-szablon powinien być mały/prosty (np. superflat) - kopiowanie całego folderu świata
 * na dysku przy starcie KAŻDEGO pojedynku jest tym tańsze, im mniejszy jest ten folder.
 */
public class DuelManager {

    private static final long INVITE_TIMEOUT_SECONDS = 60L;
    private static final long WORLD_DELETE_DELAY_TICKS = 20L * 13;

    private static final double ENTRANCE_FALL_HEIGHT = 20.0;
    private static final long ENTRANCE_FALL_TICKS = 40L;
    private static final int COUNTDOWN_SECONDS = 5;
    private static final int GHOST_SECONDS = 10;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    private String templateWorldName;
    private Location arenaPositionA;
    private Location arenaPositionB;
    private int duelCounter = 0;

    private final Map<UUID, Invite> pendingInvites = new HashMap<>();
    private final Map<UUID, ActiveDuel> activeDuels = new HashMap<>();
    private final Map<UUID, PendingRestore> pendingRestores = new HashMap<>();
    private final Map<UUID, PendingRestore> ghosts = new HashMap<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();

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
        return templateWorldName != null && arenaPositionA != null && arenaPositionB != null;
    }

    public String getTemplateWorldName() {
        return templateWorldName;
    }

    /** Zwraca pozycję 1 (do np. /bpvp duel gototemplateworld) albo null, jeśli nieustawiona. */
    public Location getArenaPosition() {
        return arenaPositionA == null ? null : arenaPositionA.clone();
    }

    public boolean isFrozen(UUID uuid) {
        return frozenPlayers.contains(uuid);
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

    /** slot 1 albo 2 - dwie OSOBNE pozycje startowe, po jednej dla każdego z pojedynkujących się. */
    public void setArenaPosition(int slot, Location location) {
        if (slot == 1) {
            this.arenaPositionA = location.clone();
        } else {
            this.arenaPositionB = location.clone();
        }
        String base = "arena" + slot;
        data.set(base + ".world", location.getWorld().getName());
        data.set(base + ".x", location.getX());
        data.set(base + ".y", location.getY());
        data.set(base + ".z", location.getZ());
        data.set(base + ".yaw", location.getYaw());
        data.set(base + ".pitch", location.getPitch());
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

        Location spawnA = arenaPositionA.clone();
        spawnA.setWorld(duelWorld);
        Location spawnB = arenaPositionB.clone();
        spawnB.setWorld(duelWorld);

        fullyHeal(a);
        fullyHeal(b);
        Bukkit.broadcastMessage(Branding.chatPrefix() + "§c§l⚔ POJEDYNEK! §f" + a.getName() + " §7vs §f" + b.getName()
                + " §7- stawka: §a" + BankGuiManager.formatMoney(bet) + "$");
        beginEntrance(duel, a, b, spawnA, spawnB);
        return true;
    }

    /**
     * Obaj gracze "zlatują z nieba" (2s, kontrolowana animacja teleportami, nie fizyka) na swoje
     * OSOBNE pozycje startowe, po czym stoją zamrożeni (zero ruchu, nietykalni) przez 5s
     * odliczania - dopiero po nim mogą się ruszać i bić.
     */
    private void beginEntrance(ActiveDuel duel, Player a, Player b, Location targetA, Location targetB) {
        frozenPlayers.add(a.getUniqueId());
        frozenPlayers.add(b.getUniqueId());
        a.setInvulnerable(true);
        b.setInvulnerable(true);

        Location startA = targetA.clone().add(0, ENTRANCE_FALL_HEIGHT, 0);
        Location startB = targetB.clone().add(0, ENTRANCE_FALL_HEIGHT, 0);
        a.teleport(startA);
        b.teleport(startB);
        a.getWorld().playSound(startA, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 1.5f);

        animateFall(duel, a, startA, targetA, 0L);
        animateFall(duel, b, startB, targetB, 0L);
    }

    private void animateFall(ActiveDuel duel, Player player, Location start, Location target, long tick) {
        if (duel.completed || !player.isOnline()) {
            return;
        }
        double t = Math.min(1.0, tick / (double) ENTRANCE_FALL_TICKS);
        double eased = CameraUtil.easeOutCubic(t);
        Location at = target.clone();
        at.setY(start.getY() + (target.getY() - start.getY()) * eased);
        player.teleport(at);
        player.getWorld().spawnParticle(Particle.CLOUD, at, 2, 0.2, 0.1, 0.2, 0.01);
        player.getWorld().spawnParticle(Particle.END_ROD, at, 1, 0.1, 0.3, 0.1, 0.005);

        if (t >= 1.0) {
            onLanded(duel, player);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animateFall(duel, player, start, target, tick + 1), 1L);
    }

    private void onLanded(ActiveDuel duel, Player player) {
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 1);
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.7f, 1.3f);
        duel.landedCount++;
        if (duel.landedCount >= 2) {
            startCountdown(duel, COUNTDOWN_SECONDS);
        }
    }

    private void startCountdown(ActiveDuel duel, int secondsLeft) {
        if (duel.completed) {
            return;
        }
        Player a = Bukkit.getPlayer(duel.playerA);
        Player b = Bukkit.getPlayer(duel.playerB);
        if (secondsLeft <= 0) {
            endCountdown(duel, a, b);
            return;
        }
        for (Player p : new Player[]{a, b}) {
            if (p == null || !p.isOnline()) {
                continue;
            }
            showCountdownNumber(p, secondsLeft);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> startCountdown(duel, secondsLeft - 1), 20L);
    }

    private void showCountdownNumber(Player player, int number) {
        World world = player.getWorld();
        TextDisplay display = world.spawn(player.getEyeLocation().add(0, 1.0, 0), TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setText("§e§l" + number);
            e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (display.isValid()) {
                display.remove();
            }
        }, 20L);
    }

    private void endCountdown(ActiveDuel duel, Player a, Player b) {
        for (Player p : new Player[]{a, b}) {
            if (p == null) {
                continue;
            }
            frozenPlayers.remove(p.getUniqueId());
            if (!p.isOnline()) {
                continue;
            }
            p.setInvulnerable(false);
            TitleUtil.show(p, "§c§lWALKA!", "");
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.5f);
        }
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
        duel.completed = true;
        frozenPlayers.remove(winnerUuid);
        frozenPlayers.remove(loserUuid);

        if (payout && plugin.getEconomy() != null && duel.bet > 0) {
            plugin.getEconomy().withdrawPlayer(Bukkit.getOfflinePlayer(loserUuid), duel.bet);
            plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(winnerUuid), duel.bet);
        }

        // Ranking ELO liczy się tylko dla realnie rozegranych pojedynków (payout=true) - przerwane
        // przez admina (payout=false) nie wpływają na rating.
        int[] eloChange = null;
        if (payout) {
            eloChange = plugin.getElo().recordDuelResult(winnerUuid, nameOf(winnerUuid), loserUuid, nameOf(loserUuid));
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
                    + " §7(ELO: " + formatEloChange(eloChange[0]) + ")"
                    : "§ePojedynek przerwany przez administrację.");
        }

        Player loser = Bukkit.getPlayer(loserUuid);
        if (loser != null && loser.isOnline()) {
            if (loser.isDead()) {
                pendingRestores.put(loserUuid, new PendingRestore(loserSnapshot, loserReturn, loser.getLocation().clone()));
            } else {
                restoreNow(loser, loserSnapshot, loserReturn);
            }
            loser.sendMessage(payout
                    ? "§c§lPrzegrałeś pojedynek. §f-" + BankGuiManager.formatMoney(duel.bet) + "$"
                    + " §7(ELO: " + formatEloChange(eloChange[1]) + ")"
                    : "§ePojedynek przerwany przez administrację.");
        }

        deleteWorldLater(duel.world);
    }

    public PendingRestore consumePendingRestore(UUID uuid) {
        return pendingRestores.remove(uuid);
    }

    /**
     * Zamiast zwykłego ekranu "Zginąłeś" z przyciskiem, przegrany od razu widzi tytuł
     * "PRZEGRAŁEŚ" i staje się duchem (spectator) na GHOST_SECONDS - odliczanie na action-barze,
     * z możliwością wcześniejszego powrotu przez /duel wroc.
     */
    public void startGhostPhase(Player player, PendingRestore pending) {
        player.setGameMode(GameMode.SPECTATOR);
        TitleUtil.show(player, "§c§lPRZEGRAŁEŚ!", "§7Jesteś duchem...");
        ghosts.put(player.getUniqueId(), pending);
        tickGhost(player.getUniqueId(), GHOST_SECONDS);
    }

    private void tickGhost(UUID uuid, int secondsLeft) {
        if (!ghosts.containsKey(uuid)) {
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            ghosts.remove(uuid);
            return;
        }
        if (secondsLeft <= 0) {
            finishGhost(player);
            return;
        }
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent("§5§lDUCH §7- powrót za §f"
                + secondsLeft + "s §7- wpisz §a/duel wroc §7by wrócić od razu"));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> tickGhost(uuid, secondsLeft - 1), 20L);
    }

    /** /duel wroc - kończy fazę ducha wcześniej. Zwraca false, jeśli gracz nie jest teraz duchem. */
    public boolean returnFromGhost(Player player) {
        if (!ghosts.containsKey(player.getUniqueId())) {
            return false;
        }
        finishGhost(player);
        return true;
    }

    private void finishGhost(Player player) {
        PendingRestore pending = ghosts.remove(player.getUniqueId());
        if (pending == null) {
            return;
        }
        GameMode gameMode = pending.getSnapshot() != null && pending.getSnapshot().gameMode != null
                ? pending.getSnapshot().gameMode : GameMode.SURVIVAL;
        player.setGameMode(gameMode);
        applySnapshot(player, pending.getSnapshot());
        Location returnLoc = pending.getReturnLocation();
        if (returnLoc != null && returnLoc.getWorld() != null) {
            player.teleport(returnLoc);
        }
        player.sendMessage("§aWróciłeś z pojedynku.");
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
        player.setInvulnerable(false);
        if (snapshot != null && snapshot.gameMode != null) {
            player.setGameMode(snapshot.gameMode);
        }
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
        s.gameMode = player.getGameMode();
        return s;
    }

    private static String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString();
    }

    private static String formatEloChange(int change) {
        return change >= 0 ? "§a+" + change : "§c" + change;
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
        arenaPositionA = loadArenaPosition(1);
        arenaPositionB = loadArenaPosition(2);
    }

    private Location loadArenaPosition(int slot) {
        String base = "arena" + slot;
        String worldName = data.getString(base + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, data.getDouble(base + ".x"), data.getDouble(base + ".y"), data.getDouble(base + ".z"),
                (float) data.getDouble(base + ".yaw"), (float) data.getDouble(base + ".pitch"));
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
        GameMode gameMode;
    }

    static final class PendingRestore {
        private final Snapshot snapshot;
        private final Location returnLocation;
        private final Location ghostLocation;

        PendingRestore(Snapshot snapshot, Location returnLocation, Location ghostLocation) {
            this.snapshot = snapshot;
            this.returnLocation = returnLocation;
            this.ghostLocation = ghostLocation;
        }

        public Snapshot getSnapshot() {
            return snapshot;
        }

        public Location getReturnLocation() {
            return returnLocation;
        }

        public Location getGhostLocation() {
            return ghostLocation;
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
        int landedCount;
        boolean completed;

        ActiveDuel(UUID playerA, UUID playerB, double bet, World world) {
            this.playerA = playerA;
            this.playerB = playerB;
            this.bet = bet;
            this.world = world;
        }
    }
}
