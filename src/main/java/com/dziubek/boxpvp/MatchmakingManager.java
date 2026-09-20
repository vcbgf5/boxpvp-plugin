package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kolejka matchmakingu dla bare "/duel" (bez wyzywania konkretnego gracza) - kolejkuje się ze
 * stawką podaną na czacie, a menedżer paruje graczy o możliwie zbliżonym "poziomie umiejętności"
 * (kille + najlepsza seria + saldo), zamiast czysto losowo. Po dobraniu pary obaj widzą podgląd
 * przeciwnika (głowa + ekwipunek w lore) przez PREP_TICKS ("serwer przygotowuje mapę"), po czym
 * pojedynek faktycznie startuje przez istniejący DuelManager#start - ten sam klon areny, animacja
 * wejścia i odliczanie co przy zaproszeniach 1v1.
 */
public class MatchmakingManager {

    private static final long PREP_TICKS = 20L * 5;

    private final BoxPvpPlugin plugin;
    private final Map<UUID, Double> queue = new LinkedHashMap<>();
    private final Set<UUID> pendingBet = new HashSet<>();
    private final Set<UUID> busy = new HashSet<>();

    public MatchmakingManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isQueued(UUID uuid) {
        return queue.containsKey(uuid);
    }

    /** Gracz jest "zajęty" matchmakingiem od momentu dobrania pary do faktycznego startu pojedynku. */
    public boolean isBusy(UUID uuid) {
        return busy.contains(uuid);
    }

    public void awaitBet(Player player) {
        pendingBet.add(player.getUniqueId());
        player.sendMessage(Branding.chatPrefix() + "§eNapisz na czacie ile monet chcesz obstawić (0 = bez stawki).");
    }

    public boolean hasPendingBet(UUID uuid) {
        return pendingBet.contains(uuid);
    }

    public void cancelPendingBet(UUID uuid) {
        pendingBet.remove(uuid);
    }

    public void handleBetChatInput(Player player, String message) {
        if (!pendingBet.remove(player.getUniqueId())) {
            return;
        }
        double bet;
        try {
            bet = Double.parseDouble(message.replace(",", "."));
        } catch (NumberFormatException e) {
            player.sendMessage("§cStawka musi być liczbą.");
            return;
        }
        if (bet < 0) {
            player.sendMessage("§cStawka nie może być ujemna.");
            return;
        }
        if (bet > 0) {
            if (plugin.getEconomy() == null) {
                player.sendMessage("§cEkonomia (Vault) niedostępna.");
                return;
            }
            if (plugin.getEconomy().getBalance(player) < bet) {
                player.sendMessage("§cNie masz wystarczająco monet na tę stawkę.");
                return;
            }
        }
        join(player, bet);
    }

    public void join(Player player, double bet) {
        UUID uuid = player.getUniqueId();
        if (plugin.getDuels().hasActiveDuel(uuid) || isBusy(uuid)) {
            player.sendMessage("§cJesteś już w trakcie pojedynku.");
            return;
        }
        if (!plugin.getDuels().isConfigured()) {
            player.sendMessage("§cPojedynki nie są jeszcze skonfigurowane.");
            return;
        }
        if (isQueued(uuid)) {
            player.sendMessage("§cJesteś już w kolejce matchmakingu.");
            return;
        }
        queue.put(uuid, bet);
        player.sendMessage(Branding.chatPrefix() + "§aDołączono do kolejki matchmakingu §7(stawka: §a"
                + BankGuiManager.formatMoney(bet) + "$§7). Szukam przeciwnika...");
        tryMatch();
    }

    public void leave(Player player) {
        if (queue.remove(player.getUniqueId()) != null) {
            player.sendMessage("§eOpuściłeś kolejkę matchmakingu.");
        }
    }

    public void handleQuit(UUID uuid) {
        queue.remove(uuid);
        pendingBet.remove(uuid);
        busy.remove(uuid);
    }

    private double skillScore(UUID uuid) {
        int kills = plugin.getStats().getKills(uuid);
        int streak = plugin.getStats().getBestKillstreak(uuid);
        double balance = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(Bukkit.getOfflinePlayer(uuid)) : 0;
        return kills + streak * 2.0 + Math.log10(Math.max(1, balance)) * 10.0;
    }

    private void tryMatch() {
        UUID[] ids = queue.keySet().toArray(new UUID[0]);
        UUID bestA = null;
        UUID bestB = null;
        double bestDiff = Double.MAX_VALUE;

        for (int i = 0; i < ids.length; i++) {
            UUID a = ids[i];
            if (!isMatchable(a)) {
                continue;
            }
            for (int j = i + 1; j < ids.length; j++) {
                UUID b = ids[j];
                if (!isMatchable(b)) {
                    continue;
                }
                double diff = Math.abs(skillScore(a) - skillScore(b));
                if (diff < bestDiff) {
                    bestDiff = diff;
                    bestA = a;
                    bestB = b;
                }
            }
        }

        if (bestA == null) {
            return;
        }

        double betA = queue.remove(bestA);
        double betB = queue.remove(bestB);
        Player playerA = Bukkit.getPlayer(bestA);
        Player playerB = Bukkit.getPlayer(bestB);
        if (playerA == null || playerB == null) {
            return;
        }
        busy.add(bestA);
        busy.add(bestB);
        startPrep(playerA, playerB, Math.min(betA, betB));
    }

    private boolean isMatchable(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline() && !plugin.getDuels().hasActiveDuel(uuid) && !isBusy(uuid);
    }

    private void startPrep(Player a, Player b, double bet) {
        a.sendMessage(Branding.chatPrefix() + "§a§lZnaleziono przeciwnika! §f" + b.getName());
        b.sendMessage(Branding.chatPrefix() + "§a§lZnaleziono przeciwnika! §f" + a.getName());
        plugin.getMatchmakingGui().showOpponentPreview(a, b, bet);
        plugin.getMatchmakingGui().showOpponentPreview(b, a, bet);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            busy.remove(a.getUniqueId());
            busy.remove(b.getUniqueId());
            if (!a.isOnline() || !b.isOnline()) {
                if (a.isOnline()) {
                    a.sendMessage("§cPrzeciwnik rozłączył się, pojedynek anulowany.");
                    a.closeInventory();
                }
                if (b.isOnline()) {
                    b.sendMessage("§cPrzeciwnik rozłączył się, pojedynek anulowany.");
                    b.closeInventory();
                }
                return;
            }
            a.closeInventory();
            b.closeInventory();
            boolean started = plugin.getDuels().start(a, b, bet);
            if (!started) {
                a.sendMessage("§cNie udało się rozpocząć pojedynku (problem ze światem areny).");
                b.sendMessage("§cNie udało się rozpocząć pojedynku (problem ze światem areny).");
            }
        }, PREP_TICKS);
    }
}
