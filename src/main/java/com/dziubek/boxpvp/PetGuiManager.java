package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wybór aktywnego peta (max 1 na raz) - proste GUI (wygląd dopracujemy później), 8 slotów na
 * gatunki (posiadane = klikalna ikona z rzadkością, nieposiadane = szare szkło "???") + slot
 * "Schowaj peta".
 */
public class PetGuiManager {

    public static final int PUT_AWAY_SLOT = 8;

    private final BoxPvpPlugin plugin;

    public PetGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new PetGuiHolder(), 9, "§d§lTwoje Pety");

        Set<String> owned = plugin.getPetManager().getOwned(player.getUniqueId());
        String active = plugin.getPetManager().getActive(player.getUniqueId());

        int slot = 0;
        for (String species : PetSpecies.ORDER) {
            PetSpecies.Info info = PetSpecies.of(species);
            if (info == null) {
                slot++;
                continue;
            }
            inv.setItem(slot, owned.contains(species) ? ownedIcon(info, species.equals(active)) : lockedIcon());
            slot++;
        }

        ItemStack putAway = new ItemStack(Material.BARRIER);
        ItemMeta meta = putAway.getItemMeta();
        meta.setDisplayName("§c§lSchowaj peta");
        meta.setLore(List.of("§7Kliknij, aby nie mieć aktywnego peta."));
        putAway.setItemMeta(meta);
        inv.setItem(PUT_AWAY_SLOT, putAway);

        player.openInventory(inv);
    }

    private ItemStack ownedIcon(PetSpecies.Info info, boolean active) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("boxpvp", "pet_icon_" + info.species()));
        meta.setDisplayName((active ? "§a§l> " : "") + info.coloredName());
        List<String> lore = new ArrayList<>();
        lore.add("§7Rzadkość: " + info.rarity().displayName());
        lore.add(active ? "§aAktywny teraz!" : "§7Kliknij, aby aktywować.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack lockedIcon() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§8???");
        meta.setLore(List.of("§7Jeszcze nie wylosowany.", "§7Zdobądź Klucz do Peta ze skrzyni!"));
        item.setItemMeta(meta);
        return item;
    }
}
