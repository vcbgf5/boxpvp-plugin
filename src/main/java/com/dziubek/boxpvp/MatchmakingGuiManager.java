package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
                27, Branding.DUELE_TITLE);

        inv.setItem(11, build(icon("duel_normal"), "§a§lZwykły matchmaking",
                List.of("§7Dobiera przeciwnika wg killi,", "§7serii zabójstw i kasy.", "",
                        "§eKliknij, a potem napisz na czacie", "§eile monet chcesz obstawić.")));

        inv.setItem(15, build(icon("duel_ranked"), "§b§lRanked (wg ELO)",
                List.of("§7Dobiera przeciwnika o zbliżonym", "§7ratingu ELO §f(Twój: " + plugin.getElo().getRating(player.getUniqueId()) + ")", "",
                        "§eKliknij, a potem napisz na czacie", "§eile monet chcesz obstawić.")));

        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    public void showOpponentPreview(Player viewer, Player opponent, double bet, boolean ranked) {
        String title = ranked ? "§bRanked §7- przeciwnik znaleziony!" : Branding.accent("Przeciwnik znaleziony!");
        Inventory inv = Bukkit.createInventory(new MatchmakingGuiHolder(MatchmakingGuiHolder.Kind.PREVIEW), 9, title);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(opponent);
        meta.setDisplayName("§e" + opponent.getName());
        List<String> lore = new ArrayList<>();
        lore.add("§7ELO: §f" + plugin.getElo().getRating(opponent.getUniqueId()));
        lore.add("§7Kille: §f" + plugin.getStats().getKills(opponent.getUniqueId()));
        lore.add("§7Najlepsza seria: §f" + plugin.getStats().getBestKillstreak(opponent.getUniqueId()));
        lore.add("§7Wygrane pojedynki: §f" + plugin.getStats().getDuelWins(opponent.getUniqueId()));
        lore.add("§7Stawka: §a" + BankGuiManager.formatMoney(bet) + "$");
        lore.add("");
        lore.add("§7Ekwipunek:");
        lore.addAll(describeInventory(opponent));
        meta.setLore(lore);
        head.setItemMeta(meta);
        inv.setItem(4, head);

        inv.setItem(0, build(new ItemStack(Material.CLOCK), "§eMasz 5s",
                List.of("§7Zaakceptuj, odrzuć, albo nic nie rób -", "§7pojedynek wystartuje sam po czasie")));
        inv.setItem(2, build(icon("duel_accept"), "§a§l Akceptuj",
                List.of("§7Kliknij, żeby zacząć od razu", "§7(gdy obaj klikną, pomija resztę czekania)")));
        inv.setItem(6, build(icon("duel_reject"), "§c§l✖ Odrzuć",
                List.of("§7Anuluje ten pojedynek", "§7Przeciwnik wraca do kolejki")));

        GuiDecor.fillEmpty(inv);
        viewer.openInventory(inv);
        GuiDecor.playOpenSound(viewer);
    }

    private ItemStack icon(String name) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("boxpvp", name));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack build(ItemStack base, String name, List<String> lore) {
        ItemMeta meta = base.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        base.setItemMeta(meta);
        return base;
    }

    private List<String> describeInventory(Player player) {
        List<String> lines = new ArrayList<>();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && hand.getType() != Material.AIR) {
            lines.add("§f- " + MaterialNames.humanize(hand.getType()) + " §7(w ręce)");
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && armor.getType() != Material.AIR) {
                lines.add("§f- " + MaterialNames.humanize(armor.getType()));
            }
        }
        if (lines.isEmpty()) {
            lines.add("§7(pusto)");
        }
        return lines;
    }
}
