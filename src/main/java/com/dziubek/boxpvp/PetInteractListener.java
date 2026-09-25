package com.dziubek.boxpvp;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/** Odtwarza aktywnego peta gracza po wejściu na serwer, chowa go po wyjściu. */
public class PetInteractListener implements Listener {

    private final BoxPvpPlugin plugin;
    private final NamespacedKey petAnchorTag;
    private final NamespacedKey petOwnerTag;
    private final NamespacedKey crateAnchorTag;
    private final NamespacedKey wingAnchorTag;

    public PetInteractListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        this.petAnchorTag = new NamespacedKey(plugin, PetDisplayManager.ANCHOR_TAG_KEY);
        this.petOwnerTag = new NamespacedKey(plugin, "pet_owner");
        this.crateAnchorTag = new NamespacedKey(plugin, CrateModelDisplayManager.ANCHOR_TAG_KEY);
        this.wingAnchorTag = new NamespacedKey(plugin, WingDisplayManager.ANCHOR_TAG_KEY);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getPetDisplays().despawn(event.getPlayer());
        plugin.getWingDisplays().despawn(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getPetManager().restoreActive(event.getPlayer());
        plugin.getWingManager().restoreActive(event.getPlayer());
    }

    /**
     * Nasze znaczniki (ArmorStand) - peta, skrzydeł ORAZ modelu 3D skrzyni - MUSZĄ się zawsze
     * zespawnować, nawet w strefie z zablokowanym spawnowaniem mobów (np. flaga WorldGuard
     * mob-spawning/DENY na spawnie/lobby) - inne pluginy/flagi mogą anulować CreatureSpawnEvent
     * myśląc, że to naturalny mob. Odpalane na MONITOR (po wszystkich innych pluginach), żeby na
     * pewno nadpisać ich decyzję.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnchorSpawn(CreatureSpawnEvent event) {
        if (!event.isCancelled()) {
            return;
        }
        var pdc = event.getEntity().getPersistentDataContainer();
        if (pdc.has(petAnchorTag, PersistentDataType.BYTE) || pdc.has(crateAnchorTag, PersistentDataType.BYTE)
                || pdc.has(wingAnchorTag, PersistentDataType.BYTE)) {
            event.setCancelled(false);
        }
    }

    /** PPM na własnym pecie odpala krótką animację sztuczki. */
    @EventHandler
    public void onPetTrick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand anchor)) {
            return;
        }
        var pdc = anchor.getPersistentDataContainer();
        if (!pdc.has(petAnchorTag, PersistentDataType.BYTE)) {
            return;
        }
        String ownerRaw = pdc.get(petOwnerTag, PersistentDataType.STRING);
        if (ownerRaw == null) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(UUID.fromString(ownerRaw))) {
            return;
        }
        plugin.getPetDisplays().playTrick(player);
    }

    /** PPM w powietrze/blok z WingUnlockItem w ręce - zużywa go i odblokowuje dane skrzydła na stałe. */
    @EventHandler
    public void onWingUnlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        String species = WingUnlockItem.speciesOf(plugin, item);
        if (species == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        boolean alreadyOwned = plugin.getWingManager().getOwned(player.getUniqueId()).contains(species);
        if (alreadyOwned) {
            player.sendMessage(Branding.chatPrefix() + "§7Masz już te skrzydła odblokowane.");
            return;
        }

        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().removeItem(item);
        }

        plugin.getWingManager().grant(player, species);
        WingSpecies.Info info = WingSpecies.of(species);
        player.sendMessage(Branding.chatPrefix() + "§aOdblokowano skrzydła: "
                + (info != null ? info.displayName() : species) + " §f§lSkrzydła §a! Wybierz je w §f/wings§a.");
    }
}
