package com.dziubek.boxpvp;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.ArrayList;
import java.util.List;

public class ShopChatListener implements Listener {

    private final BoxPvpPlugin plugin;

    public ShopChatListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ShopConfigSession session = plugin.getShopConfig().getSession(player.getUniqueId());
        if (session == null || session.awaitingChatFor == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> handleInput(player, session, message));
    }

    private void handleInput(Player player, ShopConfigSession session, String message) {
        if (message.equalsIgnoreCase("anuluj")) {
            session.awaitingChatFor = null;
            player.sendMessage("§7Anulowano wpisywanie, wracasz do menu.");
            plugin.getShopConfigGui().open(player);
            return;
        }

        if ("price".equals(session.awaitingChatFor)) {
            double price;
            try {
                price = Double.parseDouble(message.replace(",", "."));
            } catch (NumberFormatException e) {
                player.sendMessage("§cTo nie jest liczba, spróbuj ponownie albo napisz 'anuluj':");
                return;
            }
            if (price <= 0) {
                player.sendMessage("§cCena musi być większa od 0. Spróbuj ponownie:");
                return;
            }
            session.price = price;
        } else if ("name".equals(session.awaitingChatFor)) {
            session.customName = message;
        } else if ("boost".equals(session.awaitingChatFor)) {
            String[] parts = message.split("\\s+");
            if (parts.length != 2) {
                player.sendMessage("§cUżycie: <mnożnik> <minuty> (np. '2 30'), spróbuj ponownie:");
                return;
            }
            double boosterMultiplier;
            int boosterMinutes;
            try {
                boosterMultiplier = Double.parseDouble(parts[0].replace(",", "."));
                boosterMinutes = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cMnożnik i minuty muszą być liczbami, spróbuj ponownie:");
                return;
            }
            if (boosterMultiplier <= 0 || boosterMinutes <= 0) {
                player.sendMessage("§cMnożnik i minuty muszą być większe od 0, spróbuj ponownie:");
                return;
            }
            session.boosterMultiplier = boosterMultiplier;
            session.boosterMinutes = boosterMinutes;
        } else if ("commands".equals(session.awaitingChatFor)) {
            List<String> commands = new ArrayList<>();
            for (String part : message.split(";")) {
                if (!part.trim().isEmpty()) {
                    commands.add(part.trim());
                }
            }
            if (commands.isEmpty()) {
                player.sendMessage("§cNie podałeś żadnej komendy, spróbuj ponownie:");
                return;
            }
            session.commands = commands;
        }

        session.awaitingChatFor = null;
        plugin.getShopConfigGui().open(player);
    }
}
