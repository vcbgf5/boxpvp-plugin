package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI matchmakingu dla bare "/duel": ekran "dołącz do kolejki" oraz podgląd znalezionego
 * przeciwnika (głowa gracza + jego ekwipunek w lore) pokazywany obu graczom podczas 5s
 * przygotowania mapy.
 */
public class MatchmakingGuiManager {

    private final BoxPvpPlugin plugin;

    public MatchmakingGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void openJoinPrompt(Player player) {
        Inventory inv = Bukkit.createInventory(new MatchmakingGuiHolder(MatchmakingGuiHolder.Kind.JOIN_PROMPT),
                27, Branding.accent("Matchmaking - /duel"));

        ItemStack join = new ItemStack(Material.LIME_WOOL);
        ItemMeta meta = join.getItemMeta();
        meta.setDisplayName("§a§lDołącz do kolejki!");
        List<String> lore = new ArrayList<>();
        lore.add("§7Zostaniesz sparowany z przeciwnikiem");
        lore.add("§7o podobnym poziomie (kille, seria, kasa).");
        lore.add("");
        lore.add("§eKliknij, a potem napisz na czacie");
        lore.add("§eile monet chcesz obstawić.");
        meta.setLore(lore);
        join.setItemMeta(meta);
        inv.setItem(13, join);

        GuiDecor.fillEmpty(inv);
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    public void showOpponentPreview(Player viewer, Player opponent, double bet) {
        Inventory inv = Bukkit.createInventory(new MatchmakingGuiHolder(MatchmakingGuiHolder.Kind.PREVIEW),
                9, Branding.accent("Przeciwnik znaleziony!"));

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(opponent);
        meta.setDisplayName("§e" + opponent.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Kille: §f" + plugin.getStats().getKills(opponent.getUniqueId()));
        lore.add("§7Najlepsza seria: §f" + plugin.getStats().getBestKillstreak(opponent.getUniqueId()));
        lore.add("§7Wygrane pojedynki: §f" + plugin.getStats().getDuelWins(opponent.getUniqueId()));
        lore.add("§7Stawka: §a" + BankGuiManager.formatMoney(bet) + "$");
        lore.add("");
        lore.add("§7Ekwipunek:");
        lore.addAll(describeInventory(opponent));
        lore.add("");
        lore.add("§eMapa przygotowywana...");
        meta.setLore(lore);
        head.setItemMeta(meta);
        inv.setItem(4, head);

        GuiDecor.fillEmpty(inv);
        viewer.openInventory(inv);
        GuiDecor.playOpenSound(viewer);
    }

    private List<String> describeInventory(Player player) {
        List<String> lines = new ArrayList<>();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hand.getType() != Material.AIR) {
            lines.add("§f- " + humanize(hand.getType()) + " §7(w ręce)");
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                lines.add("§f- " + humanize(armor.getType()));
            }
        }
        if (lines.isEmpty()) {
            lines.add("§7(pusto)");
        }
        return lines;
    }

    private static String humanize(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
