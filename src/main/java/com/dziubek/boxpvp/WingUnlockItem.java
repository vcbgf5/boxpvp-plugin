package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Przedmiot odblokowujący konkretne skrzydła - wypada ze skrzyń jak każda inna nagroda (admin
 * sam dodaje go do wybranej skrzyni przez istniejące /crate create, np. do skrzyni "cosmetic" -
 * ten kod NIE dotyka konfiguracji żadnej skrzyni). Kliknięcie PPM zużywa przedmiot i na trwałe
 * odblokowuje dany gatunek w /wings.
 */
public final class WingUnlockItem {

    private static NamespacedKey tag;

    private WingUnlockItem() {
    }

    public static ItemStack create(BoxPvpPlugin plugin, String species, int amount) {
        WingSpecies.Info info = WingSpecies.of(species);
        String name = info != null ? info.displayName() : species;

        ItemStack item = new ItemStack(Material.ELYTRA, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§b§l" + name + " §f§lSkrzydła");
        meta.setLore(List.of(
                "§7Kliknij PRAWYM przyciskiem,",
                "§7aby odblokować te skrzydła na stałe.",
                "§8(wybierzesz je potem w /wings)"
        ));
        meta.getPersistentDataContainer().set(tag(plugin), PersistentDataType.STRING, species);
        item.setItemMeta(meta);
        return item;
    }

    /** Zwraca gatunek skrzydeł zakodowany w przedmiocie, albo null jeśli to nie jest ten item. */
    public static String speciesOf(BoxPvpPlugin plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(tag(plugin), PersistentDataType.STRING);
    }

    private static NamespacedKey tag(BoxPvpPlugin plugin) {
        if (tag == null) {
            tag = new NamespacedKey(plugin, "wing_unlock");
        }
        return tag;
    }
}
