package com.dziubek.boxpvp;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Shift+PPM na handlarzu = edycja (nasze GUI), zwykłe PPM = handel (natywne GUI Minecrafta,
 * nie ruszamy - samo się otwiera dzięki MerchantRecipe ustawionym w TraderManager).
 */
public class TraderListener implements Listener {

    private static final String ADMIN_PERMISSION = "boxpvp.admin";

    private final BoxPvpPlugin plugin;

    public TraderListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Handlarz to prawdziwy Villager, więc jego spawn podlega fladze WorldGuard
     * "mob-spawning" - jeśli admin stawia go w chronionym regionie (np. Spawn01), event
     * zostaje po cichu anulowany i handlarz nigdy się nie pojawia. Cofamy to WYŁĄCZNIE gdy
     * spawn pochodzi z naszego TraderManager.spawnVillager (flaga spawningTrader), więc region
     * nie otwiera się na żadne inne moby.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled() && event.getEntityType() == EntityType.VILLAGER && plugin.getTraders().isSpawningTrader()) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // ignorujemy duplikat zdarzenia dla off-hand
        }
        TraderManager.TraderData td = plugin.getTraders().getByEntity(event.getRightClicked());
        if (td == null || !event.getPlayer().isSneaking()) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission(ADMIN_PERMISSION)) {
            player.sendMessage("§cNie masz uprawnień do edycji handlarzy.");
            return;
        }
        plugin.getTraderEditorGui().open(player, td.name);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof TraderEditorGuiHolder)) {
            return;
        }
        TraderEditorGuiHolder holder = (TraderEditorGuiHolder) event.getInventory().getHolder();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) {
            return; // klik we własnym ekwipunku admina - normalne
        }
        if (slot > TraderEditorGuiManager.RENAME_SLOT && slot < 9) {
            event.setCancelled(true);
            return; // border
        }
        if (slot >= 9) {
            return; // sloty koszt/nagroda - normalne stawianie przedmiotów
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player admin = (Player) event.getWhoClicked();
        String traderName = holder.getTraderName();

        if (slot == TraderEditorGuiManager.PROFESSION_SLOT) {
            plugin.getTraders().cycleProfession(traderName);
        } else if (slot == TraderEditorGuiManager.TYPE_SLOT) {
            plugin.getTraders().cycleType(traderName);
        } else if (slot == TraderEditorGuiManager.RENAME_SLOT) {
            plugin.getTraders().startRename(admin, traderName);
            return;
        }

        TraderManager.TraderData td = plugin.getTraders().getData(traderName);
        if (td != null) {
            plugin.getTraderEditorGui().refreshControlRow(event.getInventory(), td);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof TraderEditorGuiHolder)) {
            return;
        }
        TraderEditorGuiHolder holder = (TraderEditorGuiHolder) event.getInventory().getHolder();
        plugin.getTraders().saveTradesFromEditor(holder.getTraderName(), event.getInventory());
        event.getPlayer().sendMessage("§aZapisano wymiany handlarza '" + holder.getTraderName() + "'.");
    }
}
