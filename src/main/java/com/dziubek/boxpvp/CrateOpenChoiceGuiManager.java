package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Menu wyboru pokazywane od razu po użyciu klucza/darmowego otwarcia, zanim wystartuje
 * właściwe losowanie - gracz wybiera, czy chce zobaczyć kręcący się bęben, czy od razu
 * poznać wynik.
 */
public class CrateOpenChoiceGuiManager {

    private static final int SIZE = 27;
    public static final int ICON_SLOT = 4;         // gorny rzad, srodek - duzy podglad skrzyni
    public static final int ANIMATED_SLOT = 12;    // srodkowy rzad, lewo od centrum
    public static final int INSTANT_SLOT = 14;     // srodkowy rzad, prawo od centrum

    private final BoxPvpPlugin plugin;

    public CrateOpenChoiceGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String crateName, List<CrateReward> rewards, Location crateBlockLocation) {
        Inventory inv = Bukkit.createInventory(new CrateOpenChoiceGuiHolder(crateName, rewards, crateBlockLocation),
                SIZE, Branding.customGuiTitle(Branding.GUI_BG_CRATE_CHOICE));

        inv.setItem(ICON_SLOT, crateIcon(crateName));
        inv.setItem(ANIMATED_SLOT, button(Material.CHEST, Branding.accent("▶ Otwórz z animacją"),
                "§7Zobaczysz kręcący się bęben", "§7i dramatyczne odliczanie."));
        inv.setItem(INSTANT_SLOT, button(Material.FEATHER, "§b§l⏩ Otwórz bez animacji",
                "§7Od razu poznasz wynik,", "§7bez czekania na bęben."));

        // Bez GuiDecor.fillEmpty() - puste sloty maja pokazywac wlasne tlo (GUI_BG_CRATE_CHOICE)
        // zamiast szklanych paneli, ktore by je zaslonily.
        player.openInventory(inv);
        GuiDecor.playOpenSound(player);
    }

    private ItemStack crateIcon(String crateName) {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§e§l" + crateName);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> loreList = new ArrayList<>(Arrays.asList(lore));
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }
}
