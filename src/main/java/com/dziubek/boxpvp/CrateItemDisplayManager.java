package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pływający ItemDisplay nad każdą fizyczną skrzynią. Gdy nikt jej nie otwiera, co sekundę
 * przełącza się na kolejny przedmiot z możliwych nagród (w kółko, w kolejności z configu),
 * cały czas wirując. Zaraz po wylosowaniu nagrody pokazuje przez chwilę dokładnie wylosowany
 * przedmiot z szybszym, mocniejszym obrotem, po czym sam wraca do normalnego przełączania.
 */
public class CrateItemDisplayManager {

    private static final String TAG = "sm_crate_item_display";
    private static final double DEFAULT_HEIGHT_ABOVE_BLOCK = 2.0;
    private static final long IDLE_PERIOD_MS = 2200;
    private static final long HIGHLIGHT_PERIOD_MS = 450;
    private static final long CYCLE_INTERVAL_TICKS = 20L;
    private static final long SPIN_INTERVAL_TICKS = 2L;
    private static final long RECONCILE_INTERVAL_TICKS = 100L;
    private static final float IDLE_SCALE = 0.6f;
    private static final float HIGHLIGHT_SCALE = 1.5f;

    // Skrzynia z wlasnym modelem 3D (BetterModel, ma realnie otwierana pokrywe) ma DWIE rozne
    // wysokosci: IDLE (caly czas widoczny plywajacy przedmiot, gdy nikt nie otwiera) i WIN_SETTLE
    // (gdzie ladzuje wygrana PO otwarciu - nizej niz idle). Item wylatuje z bloku -1 (spod
    // skrzyni), wspina sie do WIN_SETTLE i tam trzyma przez cala dlugosc "podswietlenia"
    // (HIGHLIGHT_DURATION_MS), potem wraca do zwyklego cyklu na wysokosci IDLE. Zwykla skrzynia
    // (bez modelu) nie ma tego rozroznienia - IDLE i "settle" to ta sama, globalna heightAboveBlock.
    private static final double EMERGE_HEIGHT_MODEL_CRATE = -1.0;
    private static final double MODEL_CRATE_IDLE_HEIGHT = 5.0;
    private static final double MODEL_CRATE_WIN_SETTLE_HEIGHT = 1.5;
    private static final double EMERGE_HEIGHT_PLAIN_CRATE = 1.0;
    private static final long DROP_DURATION_MS = 1100;
    // ile wygrana zostaje duza w miejscu spoczynku PO wyladowaniu, zanim wroci do normalnego cyklu
    private static final long BIG_HOLD_MS = 2000;
    private static final long HIGHLIGHT_DURATION_MS = DROP_DURATION_MS + BIG_HOLD_MS;

    private final BoxPvpPlugin plugin;
    private final NamespacedKey ownerTag;
    private final Map<String, Entry> entries = new HashMap<>();
    private double heightAboveBlock;

    public CrateItemDisplayManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        this.ownerTag = new NamespacedKey(plugin, "crate_item_display_owner");
        this.heightAboveBlock = plugin.getConfig().getDouble("crate-item-display.height", DEFAULT_HEIGHT_ABOVE_BLOCK);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::spin, 0L, SPIN_INTERVAL_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cycle, 0L, CYCLE_INTERVAL_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::reconcile, RECONCILE_INTERVAL_TICKS, RECONCILE_INTERVAL_TICKS);
    }

    public double getHeight() {
        return heightAboveBlock;
    }

    /**
     * Usuwa TYLKO "osierocone" encje - oznaczone naszym tagiem, ale nieznane tej instancji
     * managera (np. zostawione przez poprzedni /reload). Displaye, którymi ten manager już
     * żywo zarządza - w tym te w trakcie animacji wygranej - zostają NIETKNIĘTE: to nie jest
     * "zniszcz wszystko i postaw od nowa", tylko wybiórcze sprzątanie śmieci.
     */
    public void purgeOrphans() {
        int removed = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ItemDisplay.class)) {
                if (!entity.getScoreboardTags().contains(TAG) || isTracked(entity)) {
                    continue;
                }
                entity.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("Usunięto " + removed + " osieroconych pływających przedmiotów nad skrzyniami sprzed restartu.");
        }
    }

    private boolean isTracked(Entity entity) {
        for (Entry entry : entries.values()) {
            if (entry.display.equals(entity)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Zmienia wysokość na żywo, bez restartu - zapisuje w config.yml i od razu przestawia
     * wszystkie już postawione displaye na nową wysokość (teleportacja, bez ich niszczenia).
     */
    public void setHeight(double newHeight) {
        this.heightAboveBlock = newHeight;
        plugin.getConfig().set("crate-item-display.height", newHeight);
        plugin.saveConfig();

        for (Entry entry : entries.values()) {
            // skrzynie z wlasnym modelem 3D maja stale wysokosci (MODEL_CRATE_IDLE_HEIGHT/
            // MODEL_CRATE_WIN_SETTLE_HEIGHT), niezalezne od tego admin-ustawienia - dotyczy
            // tylko zwyklych skrzyn bez modelu
            if (entry.hasModel || !entry.display.isValid()) {
                continue;
            }
            Location newAnchor = entry.blockLocation.clone().add(0.5, heightAboveBlock, 0.5);
            entry.display.teleport(newAnchor);
        }
    }

    /**
     * Co RECONCILE_INTERVAL_TICKS sprawdza, czy każda przypięta fizyczna skrzynia ma żywy
     * pływający display - jeśli brakuje (np. coś go usunęło), stawia nowy.
     */
    private void reconcile() {
        for (String name : plugin.getCrates().names()) {
            for (Location location : plugin.getCrates().getAllLocations(name)) {
                Entry entry = entries.get(blockKey(location));
                if (entry == null || !entry.display.isValid()) {
                    spawnDisplay(name, location);
                }
            }
        }
    }

    /**
     * Usuwa wszystkie pływające displaye - wywoływane przy wyłączaniu/przeładowaniu pluginu,
     * żeby nie zostawić "osieroconych" encji do czasu ponownego /reload.
     */
    public void shutdown() {
        for (Entry entry : entries.values()) {
            if (entry.display.isValid()) {
                entry.display.remove();
            }
        }
        entries.clear();
    }

    /**
     * Tworzy pływający display nad podanym blokiem skrzyni. Jeśli w tym miejscu już mamy
     * żywy, śledzony display (np. wywołanie /crate purgedisplays, gdy skrzynia jest właśnie
     * otwierana i trwa animacja wygranej) - nic nie robi, żeby go nie przerywać duplikatem.
     * W przeciwnym razie najpierw sprząta ewentualne "osierocone" encje sprzed restartu
     * serwera (te trzymane w tej mapie giną razem z JVM, ale same encje w świecie zostają).
     */
    public void spawnDisplay(String crateName, Location blockLocation) {
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }

        Entry existing = entries.get(blockKey(blockLocation));
        if (existing != null && existing.display.isValid()) {
            return;
        }

        removeStrayEntities(blockLocation);

        boolean hasModel = plugin.getCrateModelDisplays().hasModel(blockLocation);
        double restHeight = hasModel ? MODEL_CRATE_IDLE_HEIGHT : heightAboveBlock;
        Location spawnAt = blockLocation.clone().add(0.5, restHeight, 0.5);
        List<CrateReward> rewards = plugin.getCrates().getRewards(crateName);
        if (rewards.isEmpty()) {
            plugin.getLogger().warning("Skrzynia '" + crateName + "' nie ma jeszcze skonfigurowanych nagród - "
                    + "pływający przedmiot nad nią pokaże tylko zastępczą ikonę skrzyni.");
        }

        ItemDisplay display = world.spawn(spawnAt, ItemDisplay.class, e -> {
            e.setBillboard(Display.Billboard.FIXED);
            e.setGravity(false);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.getPersistentDataContainer().set(ownerTag, PersistentDataType.STRING, crateName);
            e.addScoreboardTag(TAG);
            e.setItemStack(rewards.isEmpty() ? placeholderItem() : rewards.get(0).item().clone());
        });

        entries.put(blockKey(blockLocation), new Entry(display, crateName, blockLocation.clone(), hasModel));
        plugin.getLogger().info("Postawiono pływający przedmiot nad skrzynią '" + crateName + "' w "
                + world.getName() + " (" + blockLocation.getBlockX() + "," + blockLocation.getBlockY()
                + "," + blockLocation.getBlockZ() + ").");
    }

    private static ItemStack placeholderItem() {
        return new ItemStack(Material.CHEST);
    }

    public void removeDisplay(Location blockLocation) {
        Entry entry = entries.remove(blockKey(blockLocation));
        if (entry != null && entry.display.isValid()) {
            entry.display.remove();
        }
        removeStrayEntities(blockLocation);
    }

    /**
     * Wywoływane po zakończeniu losowania (CrateRollAnimation) - pokazuje nad konkretną,
     * fizycznie klikniętą skrzynią dokładnie wylosowany przedmiot z mocniejszym obrotem
     * przez kilka sekund, po czym samo przełączanie nagród co sekundę wraca do normy.
     */
    public void highlightWin(Location blockLocation, ItemStack won) {
        Entry entry = entries.get(blockKey(blockLocation));
        if (entry == null) {
            return;
        }
        long now = System.currentTimeMillis();
        entry.highlightUntil = now + HIGHLIGHT_DURATION_MS;
        entry.dropStartAt = now;
        if (entry.display.isValid()) {
            entry.display.setItemStack(won.clone());
        }
    }

    private void spin() {
        long now = System.currentTimeMillis();
        for (Entry entry : entries.values()) {
            ItemDisplay display = entry.display;
            if (!display.isValid()) {
                continue;
            }
            boolean highlighted = now < entry.highlightUntil;
            long period = highlighted ? HIGHLIGHT_PERIOD_MS : IDLE_PERIOD_MS;
            float angle = (float) ((now % period) / (double) period * Math.PI * 2);
            float scale = highlighted ? HIGHLIGHT_SCALE : IDLE_SCALE;
            float translateY = computeTranslateY(entry, now);

            Transformation transform = new Transformation(
                    new Vector3f(0f, translateY, 0f),
                    new Quaternionf(new AxisAngle4f(angle, 0f, 1f, 0f)),
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
            );
            display.setInterpolationDelay(0);
            display.setInterpolationDuration((int) SPIN_INTERVAL_TICKS);
            display.setTransformation(transform);
        }
    }

    /**
     * Pionowe przesunięcie renderowania względem pozycji spoczynku (IDLE): lekkie bujanie cały
     * czas, plus - zaraz po wygranej - wyjście ze skrzyni w górę do wysokości WIN_SETTLE (ease-out
     * w ciągu DROP_DURATION_MS), gdzie trzyma się przez resztę HIGHLIGHT_DURATION_MS (cały czas
     * "podświetlenia" - większej skali/szybszego obrotu), po czym wraca do zwykłego cyklu na
     * wysokości IDLE. Startuje od EMERGE_HEIGHT_MODEL_CRATE/EMERGE_HEIGHT_PLAIN_CRATE, więc
     * wygląda jak wyjmowanie nagrody ZE ŚRODKA skrzyni, a nie opadanie z nieba nad nią.
     * Współdzielone przez spin() i getVisualLocation(), żeby kamera cutscenki (CrateRollAnimation)
     * patrzyła dokładnie tam, gdzie przedmiot faktycznie jest renderowany w danej chwili.
     */
    private float computeTranslateY(Entry entry, long now) {
        float translateY = (float) (Math.sin(now / 500.0) * 0.05);
        if (entry.dropStartAt > 0) {
            long elapsed = now - entry.dropStartAt;
            if (elapsed < HIGHLIGHT_DURATION_MS) {
                double t = Math.min(1.0, elapsed / (double) DROP_DURATION_MS);
                double emergeHeight = entry.hasModel ? EMERGE_HEIGHT_MODEL_CRATE : EMERGE_HEIGHT_PLAIN_CRATE;
                double currentAbsolute = emergeHeight
                        + (settleHeightFor(entry) - emergeHeight) * CameraUtil.easeOutCubic(t);
                translateY += (float) (currentAbsolute - restHeightFor(entry));
            } else {
                entry.dropStartAt = 0L;
            }
        }
        return translateY;
    }

    private double restHeightFor(Entry entry) {
        return entry.hasModel ? MODEL_CRATE_IDLE_HEIGHT : heightAboveBlock;
    }

    private double settleHeightFor(Entry entry) {
        return entry.hasModel ? MODEL_CRATE_WIN_SETTLE_HEIGHT : heightAboveBlock;
    }

    /**
     * Zwraca dokładną, aktualną pozycję renderowania przedmiotu nad daną skrzynią (z
     * uwzględnieniem bujania/opadania w danej chwili) - używane, żeby kamera w cutscence po
     * wygranej realnie podążała za przedmiotem, a nie patrzyła w jeden stały punkt.
     */
    public Location getVisualLocation(Location blockLocation) {
        Entry entry = entries.get(blockKey(blockLocation));
        double restHeight = entry == null ? heightAboveBlock : restHeightFor(entry);
        Location base = blockLocation.clone().add(0.5, restHeight, 0.5);
        if (entry == null || !entry.display.isValid()) {
            return base;
        }
        return base.add(0, computeTranslateY(entry, System.currentTimeMillis()), 0);
    }

    private void cycle() {
        long now = System.currentTimeMillis();
        for (Entry entry : entries.values()) {
            if (now < entry.highlightUntil) {
                continue; // trwa pokaz wygranej - nie nadpisuj jej kolejną losową nagrodą
            }
            List<CrateReward> rewards = plugin.getCrates().getRewards(entry.crateName);
            if (rewards.isEmpty()) {
                continue;
            }
            entry.rewardIndex = (entry.rewardIndex + 1) % rewards.size();
            if (entry.display.isValid()) {
                entry.display.setItemStack(rewards.get(entry.rewardIndex).item().clone());
            }
        }
    }

    /**
     * Szuka w kolumnie nad blokiem (nie tylko na aktualnej wysokości) - żeby złapać też
     * displaye postawione zanim wysokość została zmieniona komendą /crate setdisplayheight.
     */
    private void removeStrayEntities(Location blockLocation) {
        World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }
        Location center = blockLocation.clone().add(0.5, 3.5, 0.5);
        for (Entity entity : world.getNearbyEntities(center, 0.6, 4.0, 0.6)) {
            if (entity instanceof ItemDisplay && entity.getScoreboardTags().contains(TAG)) {
                entity.remove();
            }
        }
    }

    private static String blockKey(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private static final class Entry {
        final ItemDisplay display;
        final String crateName;
        final Location blockLocation;
        final boolean hasModel;
        int rewardIndex = 0;
        long highlightUntil = 0L;
        long dropStartAt = 0L;

        Entry(ItemDisplay display, String crateName, Location blockLocation, boolean hasModel) {
            this.display = display;
            this.crateName = crateName;
            this.blockLocation = blockLocation;
            this.hasModel = hasModel;
        }
    }
}
