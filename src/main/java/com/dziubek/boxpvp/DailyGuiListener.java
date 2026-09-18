package com.dziubek.boxpvp;

import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

public class DailyGuiListener implements Listener {

    private final BoxPvpPlugin plugin;

    public DailyGuiListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof DailyConfigGuiHolder)) {
            return;
        }
        DailyConfigGuiHolder holder = (DailyConfigGuiHolder) event.getInventory().getHolder();
        ItemStack item = event.getInventory().getItem(4);

        HumanEntity human = event.getPlayer();

        if (item == null || item.getType().isAir()) {
            human.sendMessage("§cNie umieściłeś nagrody - konfiguracja dnia " + holder.getDay() + " nie zapisana.");
            return;
        }

        plugin.getDaily().setDayReward(holder.getDay(), item);
        human.sendMessage("§aZapisano nagrodę za dzień " + holder.getDay() + ".");
    }
}
