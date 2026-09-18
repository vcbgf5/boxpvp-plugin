package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShopConfigGuiManager {

    private final BoxPvpPlugin plugin;

    public ShopConfigGuiManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        ShopConfigSession session = plugin.getShopConfig().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        Inventory inv = Bukkit.createInventory(new ShopConfigGuiHolder(), 9, Branding.accent("Nowy przedmiot:") + " §f" + session.category);

        inv.setItem(0, build(session.icon.clone(), null, Collections.singletonList("&7To zobaczą gracze w sklepie")));

        inv.setItem(2, build(new ItemStack(Material.PAPER),
                "&eTyp: &f" + session.type.name(),
                List.of("&7Kliknij, aby zmienić", "&7KOMENDA -> KIT -> WALUTA")));

        inv.setItem(4, build(new ItemStack(Material.GOLD_INGOT),
                "&eCena: &f" + session.price,
                List.of("&7Kliknij i wpisz liczbę na czacie")));

        if (session.type == ShopItemType.KIT) {
            List<String> kitNames = plugin.getKits().names();
            String kitLabel = session.kitName == null ? "&cnie wybrano" : "&a" + session.kitName;
            inv.setItem(6, build(new ItemStack(Material.CHEST),
                    "&eKit: " + kitLabel,
                    kitNames.isEmpty()
                            ? List.of("&cNie masz jeszcze żadnego kitu!", "&7Stwórz: /kit create <nazwa>")
                            : List.of("&7Kliknij, aby przełączać między kitami")));
        } else {
            String cmdLabel = session.commands.isEmpty() ? "&cbrak" : "&a" + session.commands.size() + " komenda/y";
            inv.setItem(6, build(new ItemStack(Material.WRITABLE_BOOK),
                    "&eKomendy: " + cmdLabel,
                    List.of("&7Kliknij i wpisz na czacie", "&7Kilka na raz oddziel ';'", "&7%player% = nick kupującego")));
        }

        inv.setItem(7, build(new ItemStack(Material.EMERALD), "&a&lZAPISZ", Collections.singletonList("&7Zapisuje przedmiot do sklepu")));
        inv.setItem(8, build(new ItemStack(Material.BARRIER), "&c&lANULUJ", Collections.emptyList()));

        GuiDecor.fillEmpty(inv);
        player.openInventory(inv);
    }

    private ItemStack build(ItemStack base, String name, List<String> loreRaw) {
        ItemMeta meta = base.getItemMeta();
        if (name != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }
        List<String> lore = new ArrayList<>();
        for (String line : loreRaw) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);
        base.setItemMeta(meta);
        return base;
    }
}
