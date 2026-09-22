package com.dziubek.boxpvp;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
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

        Bukkit.broadcastMessage(Branding.chatPrefix() + Branding.accent(" PRESTIŻ!") + " §f" + player.getName()
                + " §7awansował na prestiż §d" + newLevel + "§7!");
        TitleUtil.show(player, Branding.accent(" PRESTIŻ " + newLevel),
                "§7Mnożnik zarobków: §fx" + format(getMultiplier(player.getUniqueId())));

        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
        animatePrestigeRing(player, player.getLocation().clone(), 0);
        return true;
    }

    /** Rozszerzający się, pulsujący fioletowy pierścień + kolumna cząsteczek, kończy się fajerwerkiem. */
    private void animatePrestigeRing(Player player, Location center, int tick) {
        if (!player.isOnline()) {
            return;
        }
        double radius = 0.5 + tick * 0.35;
        int points = 24;
        Color color = Color.fromRGB(tick % 2 == 0 ? Branding.DARK_PURPLE : Branding.LIGHT_LAVENDER);
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            player.getWorld().spawnParticle(Particle.DUST, x, center.getY() + 0.1, z, 1, 0, 0, 0, 0,
                    new Particle.DustOptions(color, 1.3f));
        }
        player.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, tick * 0.3, 0), 3, 0.2, 0.1, 0.2, 0.01);

        if (tick >= 12) {
            player.getWorld().spawnParticle(Particle.FIREWORK, center.clone().add(0, 1.2, 0), 80, 0.6, 1.0, 0.6, 0.1);
            player.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> animatePrestigeRing(player, center, tick + 1), 2L);
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
