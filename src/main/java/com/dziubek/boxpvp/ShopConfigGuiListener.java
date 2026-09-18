package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

public class ShopConfigGuiListener implements Listener {

    private final BoxPvpPlugin plugin;

    public ShopConfigGuiListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopConfigGuiHolder)) {
            return;
        }
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();

        ShopConfigSession session = plugin.getShopConfig().getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        switch (event.getRawSlot()) {
            case 2:
                session.type = nextType(session.type);
                plugin.getShopConfigGui().open(player);
                break;
            case 4:
                session.awaitingChatFor = "price";
                player.closeInventory();
                player.sendMessage("§eWpisz cenę na czacie (samą liczbę), albo 'anuluj':");
                break;
            case 6:
                handleSlotSix(player, session);
                break;
            case 7:
                trySave(player, session);
                break;
            case 8:
                plugin.getShopConfig().clearSession(player.getUniqueId());
                player.closeInventory();
                player.sendMessage("§cAnulowano dodawanie przedmiotu.");
                break;
            default:
                break;
        }
    }

    private void handleSlotSix(Player player, ShopConfigSession session) {
        if (session.type == ShopItemType.KIT) {
            List<String> names = plugin.getKits().names();
            if (names.isEmpty()) {
                player.sendMessage("§cNie masz jeszcze żadnego kitu - stwórz go: /kit create <nazwa>");
                return;
            }
            int idx = session.kitName == null ? -1 : names.indexOf(session.kitName);
            session.kitName = names.get((idx + 1) % names.size());
            plugin.getShopConfigGui().open(player);
        } else {
            session.awaitingChatFor = "commands";
            player.closeInventory();
            player.sendMessage("§eWpisz komendę/y na czacie (kilka oddziel ';', %player% = nick kupującego), albo 'anuluj':");
        }
    }

    private ShopItemType nextType(ShopItemType current) {
        ShopItemType[] values = ShopItemType.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private void trySave(Player player, ShopConfigSession session) {
        if (session.price <= 0) {
            player.sendMessage("§cUstaw najpierw poprawną cenę (> 0).");
            return;
        }
        if (session.type == ShopItemType.KIT) {
            if (session.kitName == null) {
                player.sendMessage("§cWybierz kit.");
                return;
            }
        } else {
            if (session.commands.isEmpty()) {
                player.sendMessage("§cUstaw przynajmniej jedną komendę.");
                return;
            }
        }

        int index = plugin.getShop().addItem(session.category, session.icon, session.price, session.commands, session.type, session.kitName);
        plugin.getShopConfig().clearSession(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("§aDodano przedmiot #" + index + " do kategorii '" + session.category + "'.");
    }
}
