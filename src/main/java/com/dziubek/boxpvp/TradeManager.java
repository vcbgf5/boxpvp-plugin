package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bezpieczna wymiana przedmiotów 1v1 (pomysł #15) - obaj gracze widzą tę samą wspólną
 * "szafkę", ale każdy może kłaść/zabierać przedmioty TYLKO w swoich 9 slotach (SLOTS_A/SLOTS_B).
 * Wymiana wykonuje się dopiero, gdy OBIE strony klikną "gotowe" jednocześnie - jakakolwiek
 * zmiana w ofercie którejkolwiek strony cofa oba potwierdzenia, więc nie da się dorzucić
 * czegoś po cichu tuż przed zamianą. Zamknięcie GUI przez którąkolwiek stronę bez potwierdzenia
 * anuluje wymianę i oddaje przedmioty właścicielom.
 */
public class TradeManager {

    private static final long INVITE_TIMEOUT_SECONDS = 60L;

    static final int[] SLOTS_A = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    static final int[] SLOTS_B = {14, 15, 16, 23, 24, 25, 32, 33, 34};
    static final int CONFIRM_A = 38;
    static final int CONFIRM_B = 42;
    static final int STATUS = 40;
    private static final int SIZE = 54;

    private final BoxPvpPlugin plugin;
    private final Map<UUID, Invite> pendingInvites = new HashMap<>();
    private final Map<UUID, ActiveTrade> activeTrades = new HashMap<>();

    public TradeManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasActiveTrade(UUID uuid) {
        return activeTrades.containsKey(uuid);
    }

    public ActiveTrade getActiveTrade(UUID uuid) {
        return activeTrades.get(uuid);
    }

    // ================= Zaproszenia =================

    public void invite(Player inviter, Player target) {
        pendingInvites.put(target.getUniqueId(), new Invite(inviter.getUniqueId(), System.currentTimeMillis() + INVITE_TIMEOUT_SECONDS * 1000L));
    }

    public Invite getInvite(UUID target) {
        Invite invite = pendingInvites.get(target);
        if (invite == null) {
            return null;
        }
        if (System.currentTimeMillis() > invite.expiresAt) {
            pendingInvites.remove(target);
            return null;
        }
        return invite;
    }

    public void clearInvite(UUID target) {
        pendingInvites.remove(target);
    }

    public boolean cancelOutgoingInvite(UUID inviterUuid) {
        for (Map.Entry<UUID, Invite> entry : pendingInvites.entrySet()) {
            if (entry.getValue().inviter.equals(inviterUuid)) {
                pendingInvites.remove(entry.getKey());
                return true;
            }
        }
        return false;
    }

    // ================= Wymiana =================

    public void start(Player a, Player b) {
        ActiveTrade trade = new ActiveTrade(a.getUniqueId(), b.getUniqueId());
        Inventory inv = Bukkit.createInventory(new TradeGuiHolder(trade), SIZE,
                Branding.accent("Wymiana") + " §f- " + a.getName() + " / " + b.getName());
        trade.inventory = inv;
        render(trade);

        activeTrades.put(a.getUniqueId(), trade);
        activeTrades.put(b.getUniqueId(), trade);

        a.openInventory(inv);
        b.openInventory(inv);
        GuiDecor.playOpenSound(a);
        GuiDecor.playOpenSound(b);
    }

    /** Wywoływane przez TradeListener na każdy klik w GUI wymiany - decyduje co wolno danej stronie. */
    public void handleClick(InventoryClickEvent event, ActiveTrade trade, Player player) {
        int rawSlot = event.getRawSlot();
        boolean clickedTop = rawSlot >= 0 && rawSlot < SIZE;

        if (!clickedTop) {
            if (event.isShiftClick()) {
                // wymuszamy ręczne przeciąganie z ekwipunku - inaczej shift-klik mógłby
                // wylądować po drugiej stronie zamiast w slotach klikającego gracza
                event.setCancelled(true);
            }
            return;
        }
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            // zbieranie stacków przez podwójny klik przeszukuje CAŁE widoczne inventory
            // (obie strony naraz) - blokujemy całkowicie, żeby nie dało się tak "podebrać"
            // pasujących przedmiotów z cudzych slotów
            event.setCancelled(true);
            return;
        }

        boolean isA = trade.playerA.equals(player.getUniqueId());
        int[] mySlots = isA ? SLOTS_A : SLOTS_B;
        int myConfirm = isA ? CONFIRM_A : CONFIRM_B;

        if (rawSlot == myConfirm) {
            event.setCancelled(true);
            toggleConfirm(trade, isA);
            return;
        }
        if (contains(mySlots, rawSlot)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> resetConfirmations(trade));
            return;
        }
        event.setCancelled(true);
    }

    private void toggleConfirm(ActiveTrade trade, boolean isA) {
        if (isA) {
            trade.confirmedA = !trade.confirmedA;
        } else {
            trade.confirmedB = !trade.confirmedB;
        }
        render(trade);
        if (trade.confirmedA && trade.confirmedB) {
            execute(trade);
        }
    }

    private void resetConfirmations(ActiveTrade trade) {
        if (trade.completed) {
            return;
        }
        trade.confirmedA = false;
        trade.confirmedB = false;
        render(trade);
    }

    private void execute(ActiveTrade trade) {
        trade.completed = true;
        activeTrades.remove(trade.playerA);
        activeTrades.remove(trade.playerB);

        List<ItemStack> offerA = collect(trade.inventory, SLOTS_A);
        List<ItemStack> offerB = collect(trade.inventory, SLOTS_B);
        clearSlots(trade.inventory, SLOTS_A);
        clearSlots(trade.inventory, SLOTS_B);

        Player a = Bukkit.getPlayer(trade.playerA);
        Player b = Bukkit.getPlayer(trade.playerB);
        if (a != null && a.isOnline()) {
            giveItems(a, offerB);
            a.closeInventory();
            a.sendMessage("§a§lWymiana zakończona!");
            a.playSound(a.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
        if (b != null && b.isOnline()) {
            giveItems(b, offerA);
            b.closeInventory();
            b.sendMessage("§a§lWymiana zakończona!");
            b.playSound(b.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        }
    }

    /** Anulowanie (zamknięcie GUI bez potwierdzenia przez którąkolwiek stronę) - oddaje przedmioty. */
    public void cancel(ActiveTrade trade) {
        if (trade.completed) {
            return;
        }
        trade.completed = true;
        activeTrades.remove(trade.playerA);
        activeTrades.remove(trade.playerB);

        Player a = Bukkit.getPlayer(trade.playerA);
        Player b = Bukkit.getPlayer(trade.playerB);
        returnItems(trade.inventory, SLOTS_A, a);
        returnItems(trade.inventory, SLOTS_B, b);

        if (a != null && a.isOnline()) {
            a.closeInventory();
            a.sendMessage("§cWymiana anulowana.");
        }
        if (b != null && b.isOnline()) {
            b.closeInventory();
            b.sendMessage("§cWymiana anulowana.");
        }
    }

    private void render(ActiveTrade trade) {
        Inventory inv = trade.inventory;
        for (int slot = 0; slot < inv.getSize(); slot++) {
            if (!contains(SLOTS_A, slot) && !contains(SLOTS_B, slot)) {
                inv.setItem(slot, null);
            }
        }
        GuiDecor.fillEmpty(inv);
        inv.setItem(CONFIRM_A, confirmItem(trade.confirmedA));
        inv.setItem(CONFIRM_B, confirmItem(trade.confirmedB));
        inv.setItem(STATUS, statusItem(trade));
    }

    private ItemStack confirmItem(boolean confirmed) {
        ItemStack item = new ItemStack(confirmed ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(confirmed ? "§a§lGOTOWE! §7(kliknij, by cofnąć)" : "§7Kliknij, gdy skończysz układać ofertę");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack statusItem(ActiveTrade trade) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Branding.accent("Wymiana"));
        meta.setLore(List.of(
                "§7Zmiana oferty cofa oba potwierdzenia.",
                trade.confirmedA ? "§aLewa strona: gotowa" : "§cLewa strona: układa ofertę",
                trade.confirmedB ? "§aPrawa strona: gotowa" : "§cPrawa strona: układa ofertę"
        ));
        item.setItemMeta(meta);
        return item;
    }

    private List<ItemStack> collect(Inventory inv, int[] slots) {
        List<ItemStack> list = new ArrayList<>();
        for (int slot : slots) {
            ItemStack item = inv.getItem(slot);
            if (item != null && !item.getType().isAir()) {
                list.add(item.clone());
            }
        }
        return list;
    }

    private void clearSlots(Inventory inv, int[] slots) {
        for (int slot : slots) {
            inv.setItem(slot, null);
        }
    }

    private void giveItems(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            for (ItemStack extra : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), extra);
            }
        }
    }

    private void returnItems(Inventory inv, int[] slots, Player owner) {
        for (int slot : slots) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (owner != null && owner.isOnline()) {
                Map<Integer, ItemStack> leftover = owner.getInventory().addItem(item);
                for (ItemStack extra : leftover.values()) {
                    owner.getWorld().dropItemNaturally(owner.getLocation(), extra);
                }
            }
            inv.setItem(slot, null);
        }
    }

    private static boolean contains(int[] array, int value) {
        for (int v : array) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }

    public static final class Invite {
        private final UUID inviter;
        private final long expiresAt;

        Invite(UUID inviter, long expiresAt) {
            this.inviter = inviter;
            this.expiresAt = expiresAt;
        }

        public UUID getInviter() {
            return inviter;
        }
    }

    public static final class ActiveTrade {
        final UUID playerA;
        final UUID playerB;
        Inventory inventory;
        boolean confirmedA;
        boolean confirmedB;
        boolean completed;

        ActiveTrade(UUID playerA, UUID playerB) {
            this.playerA = playerA;
            this.playerB = playerB;
        }
    }
}
