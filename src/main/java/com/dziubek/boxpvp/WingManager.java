package com.dziubek.boxpvp;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Posiadane/aktywne skrzydła gracza (players.&lt;uuid&gt;.owned / .active w wings.yml) - w
 * odróżnieniu od wcześniejszej wersji, skrzydła NIE są dostępne za darmo dla każdego: trzeba je
 * zdobyć (WingUnlockItem, wypadający ze skrzyń jak każda inna nagroda - admin sam decyduje z
 * jakiej skrzyni i jak często, przez istniejące GUI /crate create).
 */
public class WingManager {

    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private volatile boolean dirty = false;

    public WingManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "wings.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć wings.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    public Set<String> getOwned(UUID uuid) {
        return new LinkedHashSet<>(data.getStringList("players." + uuid + ".owned"));
    }

    public String getActive(UUID uuid) {
        return data.getString("players." + uuid + ".active");
    }

    /** Odblokowuje dany gatunek skrzydeł dla gracza (np. po zużyciu WingUnlockItem). */
    public void grant(Player player, String species) {
        Set<String> owned = getOwned(player.getUniqueId());
        if (!owned.add(species)) {
            return;
        }
        data.set("players." + player.getUniqueId() + ".owned", new ArrayList<>(owned));
        dirty = true;
    }

    /** Ustawia aktywne skrzydła (muszą być wcześniej odblokowane). Null = zdejmuje. */
    public void setActive(Player player, String species) {
        UUID uuid = player.getUniqueId();
        if (species == null) {
            data.set("players." + uuid + ".active", null);
            plugin.getWingDisplays().despawn(player);
        } else {
            if (plugin.getWingDisplays().spawn(plugin, player, species)) {
                data.set("players." + uuid + ".active", species);
            }
        }
        dirty = true;
    }

    /** Odtwarza aktywne skrzydła gracza (np. po wejściu na serwer), jeśli miał jakieś wybrane. */
    public void restoreActive(Player player) {
        String species = getActive(player.getUniqueId());
        if (species == null) {
            return;
        }
        plugin.getWingDisplays().spawn(plugin, player, species);
    }

    private void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać wings.yml: " + e.getMessage());
        }
    }

    public void saveNow() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać wings.yml: " + e.getMessage());
        }
    }
}
