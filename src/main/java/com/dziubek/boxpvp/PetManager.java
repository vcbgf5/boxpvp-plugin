package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Właściciel/aktywny pet gracza (players.&lt;uuid&gt;.owned / .active w pets.yml), losowanie
 * nowego peta ważone rzadkością (PetRarity#weight) oraz odświeżanie pasywnych efektów eliksiru
 * aktywnego peta.
 */
public class PetManager {

    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;
    private static final long ABILITY_REFRESH_TICKS = 20L * 6;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final Random random = new Random();
    private volatile boolean dirty = false;

    public PetManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "pets.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć pets.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::reapplyAbilities, ABILITY_REFRESH_TICKS, ABILITY_REFRESH_TICKS);
    }

    public Set<String> getOwned(UUID uuid) {
        return new LinkedHashSet<>(data.getStringList("players." + uuid + ".owned"));
    }

    public String getActive(UUID uuid) {
        return data.getString("players." + uuid + ".active");
    }

    /** Losuje gatunek ważony rzadkością i dodaje go do kolekcji gracza. Zwraca wylosowany gatunek. */
    public String rollAndGrant(UUID uuid) {
        int totalWeight = 0;
        for (PetSpecies.Info info : PetSpecies.all().values()) {
            totalWeight += info.rarity().weight();
        }
        int roll = random.nextInt(totalWeight);
        String chosen = null;
        int cursor = 0;
        for (PetSpecies.Info info : PetSpecies.all().values()) {
            cursor += info.rarity().weight();
            if (roll < cursor) {
                chosen = info.species();
                break;
            }
        }
        if (chosen == null) {
            chosen = PetSpecies.all().keySet().iterator().next();
        }

        Set<String> owned = getOwned(uuid);
        owned.add(chosen);
        data.set("players." + uuid + ".owned", new ArrayList<>(owned));
        dirty = true;
        return chosen;
    }

    /** Ustawia aktywnego peta (musi być wcześniej wylosowany/posiadany). Null = chowa peta. */
    public void setActive(Player player, String species) {
        UUID uuid = player.getUniqueId();
        if (species == null) {
            data.set("players." + uuid + ".active", null);
            plugin.getPetDisplays().despawn(player);
        } else {
            PetModel model = plugin.getPetModels().get(species);
            if (model == null) {
                return;
            }
            data.set("players." + uuid + ".active", species);
            plugin.getPetDisplays().spawn(plugin, player, species, model);
        }
        dirty = true;
    }

    /** Odtwarza aktywnego peta gracza (np. po wejściu na serwer), jeśli miał jednego wybranego. */
    public void restoreActive(Player player) {
        String species = getActive(player.getUniqueId());
        if (species == null) {
            return;
        }
        PetModel model = plugin.getPetModels().get(species);
        if (model == null) {
            return;
        }
        plugin.getPetDisplays().spawn(plugin, player, species, model);
    }

    private void reapplyAbilities() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getPetDisplays().hasActivePet(player)) {
                continue;
            }
            String species = plugin.getPetDisplays().activeSpecies(player);
            PetSpecies.Info info = species == null ? null : PetSpecies.of(species);
            if (info == null) {
                continue;
            }
            for (PotionEffect effect : PetSpecies.effects(info)) {
                player.addPotionEffect(effect);
            }
        }
    }

    private void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać pets.yml: " + e.getMessage());
        }
    }

    public void saveNow() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać pets.yml: " + e.getMessage());
        }
    }
}
