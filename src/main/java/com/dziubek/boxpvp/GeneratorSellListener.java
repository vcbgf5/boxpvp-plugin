package com.dziubek.boxpvp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Auto-sprzedaż: blok wykopany z generatora od razu znika i zamienia się w monety
 * (bez dropa do ekwipunku) - klasyczny "auto sell" znany z serwerów typu Box/Prison.
 * Blok regeneruje się normalnie przy kolejnym "fill" generatora (bez zmian w tej logice).
 */
public class GeneratorSellListener implements Listener {

    private static final long COMBO_TIMEOUT_MS = 1_500L;
    private static final int COMBO_EFFECT_STEP = 10;

    private final BoxPvpPlugin plugin;
    private final Map<UUID, Combo> combos = new HashMap<>();

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
        Player player = event.getPlayer();
        plugin.getMissions().addProgress(player, MissionManager.Type.BLOCKS_MINED, 1);
        plugin.getStats().recordBlockMined(player.getUniqueId(), player.getName());
        playTrail(player, event.getBlock().getLocation());
        int combo = trackCombo(player);

        if (plugin.getEconomy() == null) {
            return;
        }
        double price = basePrice * plugin.getEvents().totalMultiplier(player);
        plugin.getEconomy().depositPlayer(player, price);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§a+" + formatMoney(price) + "$ §7(auto-sprzedaż)"));
        FloatingTextEffect.show(plugin, player.getEyeLocation().add(0, 0.3, 0), "§a+" + formatMoney(price) + "$");

        if (combo > 0 && combo % COMBO_EFFECT_STEP == 0) {
            playCombo(player, combo);
        }
    }

    /** Krótki, prosty "ślad" cząsteczek od gracza do wykopanego bloku. */
    private void playTrail(Player player, Location blockLoc) {
        Location from = player.getEyeLocation();
        Location to = blockLoc.clone().add(0.5, 0.5, 0.5);
        int points = 6;
        for (int i = 1; i <= points; i++) {
            double t = i / (double) points;
            Location at = from.clone().add(to.clone().subtract(from).toVector().multiply(t));
            player.getWorld().spawnParticle(Particle.CRIT, at, 1, 0, 0, 0, 0);
        }
    }

    /** Rosnący "kombo" gracza przy kopaniu bez przerwy - resetuje się po COMBO_TIMEOUT_MS ciszy. */
    private int trackCombo(Player player) {
        long now = System.currentTimeMillis();
        Combo combo = combos.computeIfAbsent(player.getUniqueId(), id -> new Combo());
        if (now - combo.lastMineAt > COMBO_TIMEOUT_MS) {
            combo.count = 0;
        }
        combo.count++;
        combo.lastMineAt = now;
        return combo.count;
    }

    private void playCombo(Player player, int combo) {
        int tier = Math.min(3, combo / COMBO_EFFECT_STEP);
        Particle particle = tier >= 3 ? Particle.FLAME : tier == 2 ? Particle.CRIT : Particle.HAPPY_VILLAGER;
        player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, Math.min(2.0f, 1.0f + tier * 0.2f));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§6§lKOMBO x" + combo + "! §7Kop dalej bez przerwy!"));
    }

    private static String formatMoney(double amount) {
        return String.format("%.2f", amount);
    }

    private static final class Combo {
        int count;
        long lastMineAt;
    }
}
