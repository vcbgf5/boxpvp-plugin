package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Misje dzienne/tygodniowe - stały zestaw definicji (target/nagroda konfigurowalne przez
 * config.yml), postęp resetowany na granicy kalendarzowego dnia/tygodnia (ten sam mechanizm
 * co DailyRewardManager.currentEpochDay(), tylko szerszy krok dla tygodnia).
 */
public class MissionManager {

    public enum Type {
        BLOCKS_MINED, KILLS, CRATES_OPENED
    }

    public enum Period {
        DAILY, WEEKLY
    }

    public record Definition(String id, Type type, Period period, String displayName,
                              Material icon, int defaultTarget, double defaultReward) {
    }

    public static final List<Definition> DEFINITIONS = List.of(
            new Definition("daily_mine", Type.BLOCKS_MINED, Period.DAILY, "Kopacz dnia",
                    Material.DIAMOND_PICKAXE, 200, 250),
            new Definition("weekly_mine", Type.BLOCKS_MINED, Period.WEEKLY, "Kopacz tygodnia",
                    Material.NETHERITE_PICKAXE, 1000, 1200),
            new Definition("daily_kills", Type.KILLS, Period.DAILY, "Zabójca dnia",
                    Material.IRON_SWORD, 5, 150),
            new Definition("weekly_kills", Type.KILLS, Period.WEEKLY, "Zabójca tygodnia",
                    Material.DIAMOND_SWORD, 25, 800),
            new Definition("daily_crates", Type.CRATES_OPENED, Period.DAILY, "Łowca skrzyń (dzień)",
                    Material.CHEST, 1, 60),
            new Definition("weekly_crates", Type.CRATES_OPENED, Period.WEEKLY, "Łowca skrzyń (tydzień)",
                    Material.ENDER_CHEST, 3, 200)
    );

    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private volatile boolean dirty = false;

    public MissionManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "missions.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć missions.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Zapis na dysk NIE idzie już przy każdym postępie misji (np. co wykopany blok przy
     * auto-sprzedaży) - to powodowało zauważalne lagi przy szybkim kopaniu (pełny plik
     * missions.yml zapisywany synchronicznie na głównym wątku nawet kilka razy na sekundę).
     * Zamiast tego zmiany tylko oznaczają dane jako "brudne", a osobny, rzadki task je zrzuca.
     */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    public List<Definition> definitions() {
        return DEFINITIONS;
    }

    public int targetFor(Definition def) {
        return plugin.getConfig().getInt("missions." + def.id() + ".target", def.defaultTarget());
    }

    public double rewardFor(Definition def) {
        return plugin.getConfig().getDouble("missions." + def.id() + ".reward", def.defaultReward());
    }

    public int getProgress(UUID uuid, Definition def) {
        ensureFreshBucket(uuid, def.period());
        return data.getInt(progressPath(uuid, def), 0);
    }

    public boolean isClaimed(UUID uuid, Definition def) {
        ensureFreshBucket(uuid, def.period());
        return data.getBoolean(claimedPath(uuid, def), false);
    }

    public boolean isComplete(UUID uuid, Definition def) {
        return getProgress(uuid, def) >= targetFor(def);
    }

    /**
     * Podbija postęp wszystkich definicji pasujących do danego typu naraz (np. jedno
     * zabójstwo liczy się i do dziennej, i do tygodniowej misji zabójstw).
     */
    public void addProgress(Player player, Type type, int amount) {
        UUID uuid = player.getUniqueId();
        for (Definition def : DEFINITIONS) {
            if (def.type() != type) {
                continue;
            }
            ensureFreshBucket(uuid, def.period());
            if (isClaimed(uuid, def)) {
                continue;
            }
            int target = targetFor(def);
            int before = data.getInt(progressPath(uuid, def), 0);
            if (before >= target) {
                continue;
            }
            int after = Math.min(target, before + amount);
            data.set(progressPath(uuid, def), after);
            markDirty();
            if (after >= target && before < target) {
                player.sendMessage(Branding.chatPrefix() + "§aUkończono misję: §f" + def.displayName()
                        + " §a- odbierz nagrodę w §f/missions§a!");
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.3f);
                player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
                        player.getLocation().add(0, 1.2, 0), 35, 0.4, 0.6, 0.4, 0.25);
                FloatingTextEffect.show(plugin, player.getLocation().add(0, 2.2, 0), "§a Misja ukończona!");
            }
        }
    }

    public boolean claim(Player player, Definition def) {
        UUID uuid = player.getUniqueId();
        if (isClaimed(uuid, def) || !isComplete(uuid, def)) {
            return false;
        }
        double reward = rewardFor(def) * plugin.getEvents().totalMultiplier(player);
        if (plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(player, reward);
        }
        data.set(claimedPath(uuid, def), true);
        markDirty();
        flush(); // odbiór nagrody jest rzadki (ręczny klik w GUI) - warto zapisać od razu, nie czekać na flush
        return true;
    }

    /**
     * Jeśli zapisany "bucket" (kalendarzowy dzień/tydzień) gracza dla danego okresu jest
     * przestarzały, kasuje jego postęp+odbiór dla WSZYSTKICH misji tego okresu i zapisuje
     * nowy bucket - wywoływane na początku każdego odczytu/zapisu postępu.
     */
    private void ensureFreshBucket(UUID uuid, Period period) {
        String bucketPath = "players." + uuid + "." + period.name() + ".bucket";
        long currentBucket = currentBucket(period);
        long storedBucket = data.getLong(bucketPath, -1);
        if (storedBucket == currentBucket) {
            return;
        }
        data.set("players." + uuid + "." + period.name(), null);
        data.set(bucketPath, currentBucket);
        markDirty();
    }

    private long currentBucket(Period period) {
        long epochDay = System.currentTimeMillis() / (1000L * 60 * 60 * 24);
        return period == Period.WEEKLY ? epochDay / 7 : epochDay;
    }

    private String progressPath(UUID uuid, Definition def) {
        return "players." + uuid + "." + def.period().name() + ".progress." + def.id();
    }

    private String claimedPath(UUID uuid, Definition def) {
        return "players." + uuid + "." + def.period().name() + ".claimed." + def.id();
    }

    private void markDirty() {
        dirty = true;
    }

    /** Zapisuje na dysk tylko jeśli coś się realnie zmieniło od ostatniego zapisu. */
    public void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać missions.yml: " + e.getMessage());
        }
    }
}
