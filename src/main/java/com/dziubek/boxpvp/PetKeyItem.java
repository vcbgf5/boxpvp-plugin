package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * "Klucz do Peta" - wypada ze skrzyń (admin dodaje go do puli nagród tak samo jak każdy inny
 * przedmiot, przez /crate givekey trzymając go w ręce -> dodaj jako nagrodę), zużywany przy
 * schronisku peta (PetShelterListener), gdzie losuje nowego peta ważonego rzadkością.
 */
public final class PetKeyItem {

    private static final String DISPLAY_NAME = "§d§lKlucz do Peta";
    private static NamespacedKey tag;

    private PetKeyItem() {
    }

    public static ItemStack create(BoxPvpPlugin plugin, int amount) {
        ItemStack item = new ItemStack(Material.NAME_TAG, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(DISPLAY_NAME);
        meta.setLore(List.of(
                "§7Zanieś go do Schroniska Petów",
                "§7i wylosuj nowego towarzysza!"
        ));
        meta.getPersistentDataContainer().set(tag(plugin), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isPetKey(BoxPvpPlugin plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(tag(plugin), PersistentDataType.BYTE);
        return value != null;
    }

    private static NamespacedKey tag(BoxPvpPlugin plugin) {
        if (tag == null) {
            tag = new NamespacedKey(plugin, "pet_key");
        }
        return tag;
    }
}
