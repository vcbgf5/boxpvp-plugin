package com.dziubek.boxpvp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Auto-sprzedaż: blok wykopany z generatora od razu znika i zamienia się w monety
 * (bez dropa do ekwipunku) - klasyczny "auto sell" znany z serwerów typu Box/Prison.
 * Blok regeneruje się normalnie przy kolejnym "fill" generatora (bez zmian w tej logice).
 */
public class GeneratorSellListener implements Listener {

    private final BoxPvpPlugin plugin;

    public GeneratorSellListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.getGenerators().isGeneratorBlock(event.getBlock().getLocation())) {
            return;
        }
        double basePrice = plugin.getSell().getPrice(event.getBlock().getType());
        if (basePrice <= 0) {
            return;
        }
        event.setDropItems(false);

        if (plugin.getEconomy() == null) {
            return;
        }
        Player player = event.getPlayer();
        double price = basePrice * plugin.getEvents().totalMultiplier(player);
        plugin.getEconomy().depositPlayer(player, price);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§a+" + formatMoney(price) + "$ §7(auto-sprzedaż)"));
    }

    private static String formatMoney(double amount) {
        return String.format("%.2f", amount);
    }
}
