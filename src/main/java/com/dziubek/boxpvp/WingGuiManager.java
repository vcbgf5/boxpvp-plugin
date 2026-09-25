package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Wybór aktywnych skrzydeł (max 1 na raz) - tylko odblokowane (WingUnlockItem, wypadający ze
 * skrzyń) można nosić. 18 slotów (2 rzędy): 12 na skrzydła + "Zdejmij skrzydła".
 */
public class WingGuiManager {

    public static final int TAKE_OFF_SLOT = 17;

    private final BoxPvpPlugin plugin;

    public WingGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new WingGuiHolder(), 18, "§b§lSkrzydła");

        Set<String> owned = plugin.getWingManager().getOwned(player.getUniqueId());
        String active = plugin.getWingManager().getActive(player.getUniqueId());

        int slot = 0;
        for (String species : WingSpecies.ORDER) {
            WingSpecies.Info info = WingSpecies.of(species);
            if (info == null) {
                slot++;
                continue;
            }
            inv.setItem(slot, owned.contains(species) ? ownedIcon(info, species.equals(active)) : lockedIcon());
            slot++;
        }

        ItemStack takeOff = new ItemStack(Material.BARRIER);
        ItemMeta meta = takeOff.getItemMeta();
        meta.setDisplayName("§c§lZdejmij skrzydła");
        meta.setLore(List.of("§7Kliknij, aby nie nosić żadnych skrzydeł."));
        takeOff.setItemMeta(meta);
        inv.setItem(TAKE_OFF_SLOT, takeOff);

        player.openInventory(inv);
    }

    private ItemStack ownedIcon(WingSpecies.Info info, boolean active) {
        ItemStack item = new ItemStack(Material.ELYTRA);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((active ? "§a§l> " : "") + info.displayName() + " §fSkrzydła");
        List<String> lore = new ArrayList<>();
        lore.add(active ? "§aNoszone teraz!" : "§7Kliknij, aby założyć.");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack lockedIcon() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§8???");
        meta.setLore(List.of("§7Jeszcze nie odblokowane.", "§7Zdobądź odpowiedni przedmiot ze skrzyni!"));
        item.setItemMeta(meta);
        return item;
    }
}
