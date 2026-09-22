package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * GUI misji dziennych (sloty 10-12) i tygodniowych (sloty 15-17) z paskiem postępu i
 * przyciskiem odbioru nagrody dla ukończonych-nieodebranych.
 */
public class MissionsGuiManager {

    private static final int[] DAILY_SLOTS = {10, 11, 12};
    private static final int[] WEEKLY_SLOTS = {15, 16, 17};
    private static final int BAR_WIDTH = 20;

    private final BoxPvpPlugin plugin;

    public MissionsGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        List<MissionManager.Definition> definitions = plugin.getMissions().definitions();
        List<MissionManager.Definition> slotDefinitions = new ArrayList<>(Arrays.asList(new MissionManager.Definition[27]));

        Inventory inv = Bukkit.createInventory(new MissionsGuiHolder(slotDefinitions), 27,
                Branding.accent("Misje:") + " §f" + player.getName());

        int dailyIndex = 0;
        int weeklyIndex = 0;
        for (MissionManager.Definition def : definitions) {
            int slot;
            if (def.period() == MissionManager.Period.DAILY && dailyIndex < DAILY_SLOTS.length) {
                slot = DAILY_SLOTS[dailyIndex++];
            } else if (def.period() == MissionManager.Period.WEEKLY && weeklyIndex < WEEKLY_SLOTS.length) {
                slot = WEEKLY_SLOTS[weeklyIndex++];
            } else {
                continue;
            }
            slotDefinitions.set(slot, def);
            inv.setItem(slot, buildMissionItem(player, def));
        }

        GuiDecor.fillEmpty(inv);
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    private ItemStack buildMissionItem(Player player, MissionManager.Definition def) {
        UUID uuid = player.getUniqueId();
        int progress = plugin.getMissions().getProgress(uuid, def);
        int target = plugin.getMissions().targetFor(def);
        double reward = plugin.getMissions().rewardFor(def);
        boolean claimed = plugin.getMissions().isClaimed(uuid, def);
        boolean complete = plugin.getMissions().isComplete(uuid, def);

        Material icon = claimed ? Material.LIME_DYE : (complete ? Material.GOLD_NUGGET : def.icon());
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Branding.accentLight(def.displayName()));

        List<String> lore = new ArrayList<>();
        lore.add(buildProgressBar(progress, target));
        lore.add("§7Postęp: §f" + progress + "/" + target);
        lore.add("§7Nagroda: §a" + String.format("%.2f", reward) + "$");
        if (claimed) {
            lore.add("§a Odebrano");
        } else if (complete) {
            lore.add("§e§lKLIKNIJ, aby odebrać!");
        } else {
            lore.add("§7Jeszcze nie ukończono");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String buildProgressBar(int progress, int target) {
        int filled = target <= 0 ? 0 : (int) Math.round(BAR_WIDTH * Math.min(1.0, (double) progress / target));
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < BAR_WIDTH; i++) {
            if (i == filled) {
                bar.append("§7");
            }
            bar.append(i < filled ? "█" : "░");
        }
        return bar.toString();
    }
}
