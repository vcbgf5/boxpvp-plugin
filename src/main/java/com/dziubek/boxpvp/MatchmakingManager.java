package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kolejka matchmakingu dla bare "/duel" (bez wyzywania konkretnego gracza) - DWIE osobne pule:
 * zwykła (dobiera wg killi/serii/kasy) i ranked (dobiera WYŁĄCZNIE wg zbliżonego ratingu ELO).
 * Bet podaje się na czacie po wybraniu trybu w GUI. Po dobraniu pary obaj widzą podgląd
 * przeciwnika i mają PREP_TICKS (5s) na kliknięcie "Akceptuj" (gdy OBAJ zaakceptują, pojedynek
 * startuje od razu) albo "Odrzuć" (anuluje mecz, drugi gracz wraca do swojej kolejki) - jeśli
 * nikt nic nie kliknie, po 5s startuje mimo to. Start idzie przez istniejący DuelManager#start -
 * ten sam klon areny, animacja wejścia i odliczanie co przy zaproszeniach 1v1. ELO aktualizuje
 * się identycznie dla obu trybów (DuelManager#finish już robi to dla KAŻDEGO realnego pojedynku),
 * więc "ranked" różni się tylko sposobem dobierania pary, nie konsekwencjami wyniku.
 */
public class MatchmakingManager {

    private static final long PREP_TICKS = 20L * 5;

    private final BoxPvpPlugin plugin;
    private final Map<UUID, Double> queue = new LinkedHashMap<>();
    private final Map<UUID, Double> rankedQueue = new LinkedHashMap<>();
    private final Map<UUID, Boolean> pendingBet = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, PendingMatch> pendingMatches = new HashMap<>();

    public MatchmakingManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isQueued(UUID uuid) {
        return queue.containsKey(uuid) || rankedQueue.containsKey(uuid);
    }

    /** Gracz jest "zajęty" matchmakingiem od momentu dobrania pary do faktycznego startu pojedynku. */
    public boolean isBusy(UUID uuid) {
        return busy.contains(uuid);
    }

    public void awaitBet(Player player, boolean ranked) {
        pendingBet.put(player.getUniqueId(), ranked);
        player.sendMessage(Branding.chatPrefix() + "§eNapisz na czacie ile monet chcesz obstawić (0 = bez stawki).");
    }

    public boolean hasPendingBet(UUID uuid) {
        return pendingBet.containsKey(uuid);
    }

    public void cancelPendingBet(UUID uuid) {
        pendingBet.remove(uuid);
    }

    public void handleBetChatInput(Player player, String message) {
        Boolean ranked = pendingBet.remove(player.getUniqueId());
        if (ranked == null) {
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
        join(player, bet, ranked);
    }

    public void join(Player player, double bet, boolean ranked) {
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
        Map<UUID, Double> pool = ranked ? rankedQueue : queue;
        pool.put(uuid, bet);
        player.sendMessage(Branding.chatPrefix() + (ranked ? "§b§lKolejka RANKED §7" : "§aDołączono do kolejki matchmakingu §7")
                + "(stawka: §a" + BankGuiManager.formatMoney(bet) + "$§7). Szukam przeciwnika...");
        tryMatch(pool, ranked);
    }

    public void leave(Player player) {
        UUID uuid = player.getUniqueId();
        boolean removed = queue.remove(uuid) != null;
        removed |= rankedQueue.remove(uuid) != null;
        if (removed) {
            player.sendMessage("§eOpuściłeś kolejkę matchmakingu.");
        }
    }

    public void handleQuit(UUID uuid) {
        queue.remove(uuid);
        rankedQueue.remove(uuid);
        pendingBet.remove(uuid);
        busy.remove(uuid);

        PendingMatch match = pendingMatches.remove(uuid);
        if (match == null) {
            return;
        }
        UUID opponentUuid = match.opponentOf(uuid);
        pendingMatches.remove(opponentUuid);
        busy.remove(opponentUuid);
        if (match.task != null) {
            match.task.cancel();
        }
        Player opponent = Bukkit.getPlayer(opponentUuid);
        if (opponent != null && opponent.isOnline()) {
            opponent.sendMessage("§cPrzeciwnik rozłączył się, pojedynek anulowany.");
            opponent.closeInventory();
        }
    }

    public boolean isPendingMatch(UUID uuid) {
        return pendingMatches.containsKey(uuid);
    }

    /** Klik "Akceptuj" w podglądzie przeciwnika - gdy OBAJ zaakceptują, pojedynek startuje od razu. */
    public void accept(Player player) {
        PendingMatch match = pendingMatches.get(player.getUniqueId());
        if (match == null) {
            return;
        }
        if (!match.accepted.add(player.getUniqueId())) {
            return;
        }
        UUID opponentUuid = match.opponentOf(player.getUniqueId());
        if (match.accepted.contains(opponentUuid)) {
            player.sendMessage("§aObaj zaakceptowaliście - zaczynamy!");
            if (match.task != null) {
                match.task.cancel();
            }
            finishPrep(match);
        } else {
            player.sendMessage("§aZaakceptowano! Czekam na przeciwnika (albo minie 5s)...");
            Player opponent = Bukkit.getPlayer(opponentUuid);
            if (opponent != null && opponent.isOnline()) {
                opponent.sendMessage("§ePrzeciwnik zaakceptował - kliknij §aAkceptuj§e, żeby zacząć od razu!");
            }
        }
    }

    /** Klik "Odrzuć" - anuluje dobrany mecz, przeciwnik automatycznie wraca do swojej kolejki. */
    public void decline(Player player) {
        PendingMatch match = pendingMatches.get(player.getUniqueId());
        if (match == null) {
            return;
        }
        if (match.task != null) {
            match.task.cancel();
        }
        pendingMatches.remove(match.playerA);
        pendingMatches.remove(match.playerB);
        busy.remove(match.playerA);
        busy.remove(match.playerB);

        UUID opponentUuid = match.opponentOf(player.getUniqueId());
        player.closeInventory();
        player.sendMessage("§eZrezygnowałeś z dobranego pojedynku.");

        Player opponent = Bukkit.getPlayer(opponentUuid);
        if (opponent != null && opponent.isOnline()) {
            opponent.closeInventory();
            opponent.sendMessage("§cPrzeciwnik zrezygnował - wracasz do kolejki.");
            join(opponent, match.bet, match.ranked);
        }
    }

    private double skillScore(UUID uuid) {
        int kills = plugin.getStats().getKills(uuid);
        int streak = plugin.getStats().getBestKillstreak(uuid);
        double balance = plugin.getEconomy() != null ? plugin.getEconomy().getBalance(Bukkit.getOfflinePlayer(uuid)) : 0;
        return kills + streak * 2.0 + Math.log10(Math.max(1, balance)) * 10.0;
    }

    private void tryMatch(Map<UUID, Double> pool, boolean ranked) {
        UUID[] ids = pool.keySet().toArray(new UUID[0]);
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
                double diff = ranked
                        ? Math.abs(plugin.getElo().getRating(a) - plugin.getElo().getRating(b))
                        : Math.abs(skillScore(a) - skillScore(b));
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

        double betA = pool.remove(bestA);
        double betB = pool.remove(bestB);
        Player playerA = Bukkit.getPlayer(bestA);
        Player playerB = Bukkit.getPlayer(bestB);
        if (playerA == null || playerB == null) {
            return;
        }
        busy.add(bestA);
        busy.add(bestB);
        startPrep(playerA, playerB, Math.min(betA, betB), ranked);
    }

    private boolean isMatchable(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        return player != null && player.isOnline() && !plugin.getDuels().hasActiveDuel(uuid) && !isBusy(uuid);
    }

    private void startPrep(Player a, Player b, double bet, boolean ranked) {
        String tagPrefix = ranked ? "§b§l[RANKED] §r" : "";
        a.sendMessage(Branding.chatPrefix() + tagPrefix + "§a§lZnaleziono przeciwnika! §f" + b.getName()
                + " §7- masz 5s: kliknij §aAkceptuj §7(zacznie od razu, gdy obaj klikną), §cOdrzuć §7albo nic nie rób.");
        b.sendMessage(Branding.chatPrefix() + tagPrefix + "§a§lZnaleziono przeciwnika! §f" + a.getName()
                + " §7- masz 5s: kliknij §aAkceptuj §7(zacznie od razu, gdy obaj klikną), §cOdrzuć §7albo nic nie rób.");

        PendingMatch match = new PendingMatch(a.getUniqueId(), b.getUniqueId(), bet, ranked);
        pendingMatches.put(a.getUniqueId(), match);
        pendingMatches.put(b.getUniqueId(), match);

        plugin.getMatchmakingGui().showOpponentPreview(a, b, bet, ranked);
        plugin.getMatchmakingGui().showOpponentPreview(b, a, bet, ranked);

        match.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> finishPrep(match), PREP_TICKS);
    }

    private void finishPrep(PendingMatch match) {
        pendingMatches.remove(match.playerA);
        pendingMatches.remove(match.playerB);
        busy.remove(match.playerA);
        busy.remove(match.playerB);

        Player a = Bukkit.getPlayer(match.playerA);
        Player b = Bukkit.getPlayer(match.playerB);
        if (a == null || !a.isOnline() || b == null || !b.isOnline()) {
            if (a != null && a.isOnline()) {
                a.sendMessage("§cPrzeciwnik rozłączył się, pojedynek anulowany.");
                a.closeInventory();
            }
            if (b != null && b.isOnline()) {
                b.sendMessage("§cPrzeciwnik rozłączył się, pojedynek anulowany.");
                b.closeInventory();
            }
            return;
        }
        a.closeInventory();
        b.closeInventory();
        boolean started = plugin.getDuels().start(a, b, match.bet);
        if (!started) {
            a.sendMessage("§cNie udało się rozpocząć pojedynku (problem ze światem areny).");
            b.sendMessage("§cNie udało się rozpocząć pojedynku (problem ze światem areny).");
        }
    }

    private static final class PendingMatch {
        final UUID playerA;
        final UUID playerB;
        final double bet;
        final boolean ranked;
        final Set<UUID> accepted = new HashSet<>();
        BukkitTask task;

        PendingMatch(UUID playerA, UUID playerB, double bet, boolean ranked) {
            this.playerA = playerA;
            this.playerB = playerB;
            this.bet = bet;
            this.ranked = ranked;
        }

        UUID opponentOf(UUID uuid) {
            return playerA.equals(uuid) ? playerB : playerA;
        }
    }
}
