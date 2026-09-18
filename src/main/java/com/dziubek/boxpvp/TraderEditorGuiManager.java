package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI edycji handlarza (Shift+PPM): górny rząd to kontrolki (wygląd, nazwa), środkowy rząd
 * to przedmioty "koszt", dolny rząd to przedmioty "nagroda" - w tej samej kolumnie tworzą
 * jedną wymianę. Zamknięcie okna zapisuje trade'y.
 */
public class TraderEditorGuiManager {

    public static final int PROFESSION_SLOT = 0;
    public static final int TYPE_SLOT = 1;
    public static final int RENAME_SLOT = 2;

    private final BoxPvpPlugin plugin;

    public TraderEditorGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player admin, String traderName) {
        TraderManager.TraderData td = plugin.getTraders().getData(traderName);
        if (td == null) {
            admin.sendMessage("§cNie znaleziono handlarza '" + traderName + "'.");
            return;
        }

        Inventory inv = Bukkit.createInventory(new TraderEditorGuiHolder(traderName), 27,
                Branding.accent("Edycja:") + " §f" + traderName);

        inv.setItem(PROFESSION_SLOT, professionButton(td.profession));
        inv.setItem(TYPE_SLOT, typeButton(td.type));
        inv.setItem(RENAME_SLOT, renameButton(td.title));
        for (int i = 3; i < 9; i++) {
            inv.setItem(i, border());
        }

        for (int col = 0; col < 9 && col < td.trades.size(); col++) {
            Trade trade = td.trades.get(col);
            inv.setItem(9 + col, trade.cost().clone());
            inv.setItem(18 + col, trade.reward().clone());
        }

        admin.openInventory(inv);
        GuiDecor.playOpenSound(admin);
        admin.sendMessage("§7Rząd 2 = co gracz oddaje, rząd 3 = co dostaje (ta sama kolumna = jedna wymiana).");
    }

    /**
     * Odświeża przyciski zawodu/typu po zmianie (np. po kliknięciu) - bez zamykania GUI.
     */
    public void refreshControlRow(Inventory inv, TraderManager.TraderData td) {
        inv.setItem(PROFESSION_SLOT, professionButton(td.profession));
        inv.setItem(TYPE_SLOT, typeButton(td.type));
        inv.setItem(RENAME_SLOT, renameButton(td.title));
    }

    private ItemStack professionButton(Villager.Profession profession) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a§lZawód: §f" + profession.name());
        meta.setLore(List.of("§7Kliknij, aby zmienić wygląd szaty."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack typeButton(Villager.Type type) {
        ItemStack item = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a§lTyp: §f" + type.name());
        meta.setLore(List.of("§7Kliknij, aby zmienić skórę (biom)."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack renameButton(String currentTitle) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a§lZmień nazwę");
        meta.setLore(List.of("§7Obecnie: " + currentTitle, "§7Kliknij, aby wpisać nową na czacie."));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack border() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        return pane;
    }
}
