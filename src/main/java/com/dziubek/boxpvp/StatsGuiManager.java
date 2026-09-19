package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Read-only GUI z osobistymi statystykami gracza - nie modyfikuje żadnych danych,
 * tylko czyta je ze StatsManager/PrestigeManager/Economy i wyświetla w kafelkach.
 */
public class StatsGuiManager {

    private final BoxPvpPlugin plugin;

    public StatsGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        Inventory inv = Bukkit.createInventory(new StatsGuiHolder(), 27,
                Branding.accent("Statystyki:") + " §f" + player.getName());

        int kills = plugin.getStats().getKills(uuid);
        int deaths = plugin.getStats().getDeaths(uuid);
        double kd = deaths == 0 ? kills : (double) kills / deaths;

        inv.setItem(10, buildStatItem(Material.IRON_SWORD, "§dZabójstwa", List.of("§7" + kills)));
        inv.setItem(11, buildStatItem(Material.SKELETON_SKULL, "§dŚmierci", List.of("§7" + deaths)));
        inv.setItem(12, buildStatItem(Material.COMPASS, "§dK/D", List.of("§7" + String.format("%.2f", kd))));

        List<String> coinsLore;
        if (plugin.getEconomy() != null) {
            coinsLore = List.of("§a" + String.format("%.2f", plugin.getEconomy().getBalance(player)) + "$");
        } else {
            coinsLore = List.of("§cEkonomia niedostępna");
        }
        inv.setItem(14, buildStatItem(Material.GOLD_INGOT, "§dSaldo", coinsLore));

        int prestige = plugin.getPrestige().getLevel(uuid);
        double multiplier = plugin.getPrestige().getMultiplier(uuid);
        inv.setItem(15, buildStatItem(Material.NETHER_STAR, "§dPrestiż",
                List.of("§7Poziom: §f" + prestige, "§7Mnożnik: §fx" + String.format("%.2f", multiplier))));

        inv.setItem(16, buildStatItem(Material.BLAZE_POWDER, "§dNajlepsza seria",
                List.of("§7" + plugin.getStats().getBestKillstreak(uuid))));

        inv.setItem(20, buildStatItem(Material.ENDER_CHEST, "§dOtwarte skrzynie",
                List.of("§7" + plugin.getStats().getCratesOpened(uuid))));
        inv.setItem(21, buildStatItem(Material.SUNFLOWER, "§dOdebrane daily",
                List.of("§7" + plugin.getStats().getDailyClaims(uuid))));
        inv.setItem(22, buildStatItem(Material.DIAMOND_PICKAXE, "§dWykopane bloki",
                List.of("§7" + plugin.getStats().getBlocksMined(uuid))));
        inv.setItem(23, buildStatItem(Material.EMERALD, "§dWydane monety",
                List.of("§a" + String.format("%.2f", plugin.getStats().getMoneySpent(uuid)) + "$")));
        inv.setItem(24, buildStatItem(Material.CLOCK, "§dCzas gry",
                List.of("§7" + PlaytimeManager.formatDuration(plugin.getPlaytime().getSeconds(uuid)))));

        GuiDecor.fillEmpty(inv);
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    private ItemStack buildStatItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        item.setItemMeta(meta);
        return item;
    }
}
