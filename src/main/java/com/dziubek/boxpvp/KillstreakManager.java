package com.dziubek.boxpvp;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Żywa (nie zapisywana na dysk) seria zabójstw - resetuje się przy śmierci. Najlepszy wynik
 * trafia do StatsManager (na leaderboard), a każde zabójstwo i próg serii dają monety.
 */
public class KillstreakManager {

    private final BoxPvpPlugin plugin;
    private final Map<UUID, Integer> current = new HashMap<>();

    public KillstreakManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public int getCurrent(UUID uuid) {
        Integer streak = current.get(uuid);
        return streak == null ? 0 : streak;
    }

    public void onKill(Player killer) {
        int streak = current.merge(killer.getUniqueId(), 1, Integer::sum);

        if (plugin.getEconomy() != null) {
            double reward = plugin.getConfig().getDouble("killstreak.kill-reward", 15) * plugin.getEvents().totalMultiplier(killer);
            plugin.getEconomy().depositPlayer(killer, reward);
            killer.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§a+" + String.format("%.2f", reward) + "$ §7(zabójstwo, seria: §c" + streak + "§7)"));
        }

        int milestoneInterval = plugin.getConfig().getInt("killstreak.milestone-interval", 5);
        if (milestoneInterval > 0 && streak % milestoneInterval == 0) {
            announceMilestone(killer, streak);
        }
    }

    public void onDeath(Player victim) {
        Integer streak = current.remove(victim.getUniqueId());
        if (streak == null || streak <= 0) {
            return;
        }
        plugin.getStats().setBestKillstreakIfHigher(victim.getUniqueId(), victim.getName(), streak);
    }

    private void announceMilestone(Player killer, int streak) {
        double bonus = plugin.getConfig().getDouble("killstreak.milestone-bonus", 100) * plugin.getEvents().totalMultiplier(killer);
        if (plugin.getEconomy() != null) {
            plugin.getEconomy().depositPlayer(killer, bonus);
        }
        Bukkit.broadcastMessage("§c§l⚔ " + killer.getName() + " §7ma serię §c§l" + streak
                + " §7zabójstw! §a(+" + String.format("%.2f", bonus) + "$)");
        TitleUtil.show(killer, "§c§lSERIA x" + streak, "§7+" + String.format("%.2f", bonus) + "$");
        killer.playSound(killer.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.6f);
        killer.getWorld().spawnParticle(Particle.FLAME, killer.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
    }
}
