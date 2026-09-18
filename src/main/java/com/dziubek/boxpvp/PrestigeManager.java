package com.dziubek.boxpvp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Prestiż: gracz oddaje całą zgromadzoną gotówkę po przekroczeniu progu i w zamian dostaje
 * trwały mnożnik zarobków (auto-sprzedaż + nagrody za zabójstwa) na zawsze - klasyczny "rebirth".
 */
public class PrestigeManager {

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;

    public PrestigeManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "prestige.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć prestige.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public int getLevel(UUID uuid) {
        return data.getInt(uuid + ".level", 0);
    }

    public double getMultiplier(UUID uuid) {
        double bonusPerLevel = plugin.getConfig().getDouble("prestige.bonus-per-level", 0.1);
        return 1.0 + getLevel(uuid) * bonusPerLevel;
    }

    public double getCost(int level) {
        double base = plugin.getConfig().getDouble("prestige.base-cost", 1_000_000.0);
        double growth = plugin.getConfig().getDouble("prestige.cost-multiplier-per-level", 1.5);
        return base * Math.pow(growth, level);
    }

    public boolean prestige(Player player) {
        Economy economy = plugin.getEconomy();
        if (economy == null) {
            player.sendMessage("§cEkonomia (Vault) niedostępna.");
            return false;
        }

        int maxLevel = plugin.getConfig().getInt("prestige.max", 0);
        int level = getLevel(player.getUniqueId());
        if (maxLevel > 0 && level >= maxLevel) {
            player.sendMessage("§cMasz już maksymalny prestiż (" + maxLevel + ").");
            return false;
        }

        double cost = getCost(level);
        if (economy.getBalance(player) < cost) {
            player.sendMessage("§cPotrzebujesz §f" + format(cost) + "$ §cna koncie, żeby awansować prestiż (masz §f"
                    + format(economy.getBalance(player)) + "$§c).");
            return false;
        }

        economy.withdrawPlayer(player, cost);
        int newLevel = level + 1;
        data.set(player.getUniqueId() + ".level", newLevel);
        save();

        Bukkit.broadcastMessage("§d§l✦ PRESTIŻ! §f" + player.getName() + " §7awansował na prestiż §d" + newLevel + "§7!");
        TitleUtil.show(player, "§d§l✦ PRESTIŻ " + newLevel,
                "§7Mnożnik zarobków: §fx" + format(getMultiplier(player.getUniqueId())));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 80, 0.5, 0.8, 0.5, 0.4);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
        return true;
    }

    private static String format(double value) {
        return String.format("%.2f", value);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać prestige.yml: " + e.getMessage());
        }
    }
}
