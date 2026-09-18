package com.dziubek.boxpvp;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DailyRewardManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    public DailyRewardManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "dailyrewards.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć dailyrewards.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void setDayReward(int day, ItemStack item) {
        data.set("rewards.day" + day, item);
        save();
    }

    public ItemStack getDayReward(int day) {
        return data.getItemStack("rewards.day" + day);
    }

    public CrateEffect getEffect() {
        CrateEffect effect = CrateEffect.fromString(data.getString("effect"));
        return effect != null ? effect : CrateEffect.FAJERWERKI;
    }

    public void setEffect(CrateEffect effect) {
        data.set("effect", effect.name());
        save();
    }

    public boolean canClaimToday(UUID uuid) {
        long today = currentEpochDay();
        long last = data.getLong("players." + uuid + ".last-claim-day", -1);
        return last != today;
    }

    /**
     * Zwraca który dzień cyklu (1-7) gracz ma dziś odebrać.
     * Jeśli ostatnio odebrał wczoraj - kontynuuje serię, inaczej wraca do dnia 1.
     */
    public int getNextCycleDay(UUID uuid) {
        long today = currentEpochDay();
        long last = data.getLong("players." + uuid + ".last-claim-day", -1);
        int lastCycleDay = data.getInt("players." + uuid + ".cycle-day", 0);

        if (last == today - 1) {
            int next = lastCycleDay + 1;
            return next > 7 ? 1 : next;
        }
        return 1;
    }

    public void markClaimed(UUID uuid, int cycleDay) {
        data.set("players." + uuid + ".last-claim-day", currentEpochDay());
        data.set("players." + uuid + ".cycle-day", cycleDay);
        save();
    }

    private long currentEpochDay() {
        return System.currentTimeMillis() / (1000L * 60 * 60 * 24);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać dailyrewards.yml: " + e.getMessage());
        }
    }
}
