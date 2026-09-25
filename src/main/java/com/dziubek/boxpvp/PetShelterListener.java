package com.dziubek.boxpvp;

import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * PPM na NPC Schroniska Petów z Kluczem do Peta w ręce -> zużywa klucz, losuje peta ważonego
 * rzadkością (PetManager#rollAndGrant) i pokazuje graczowi tytuł ekranowy z wynikiem.
 */
public class PetShelterListener implements Listener {

    private final BoxPvpPlugin plugin;

    public PetShelterListener(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.isCancelled() && event.getEntityType() == EntityType.VILLAGER
                && plugin.getPetShelters().isSpawningShelter()) {
            event.setCancelled(false);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!plugin.getPetShelters().isShelter(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!PetKeyItem.isPetKey(plugin, held)) {
            player.sendMessage("§cPotrzebujesz §d§lKlucza do Peta§c, aby wylosować towarzysza!");
            return;
        }

        if (held.getAmount() > 1) {
            held.setAmount(held.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        String species = plugin.getPetManager().rollAndGrant(player.getUniqueId());
        PetSpecies.Info info = PetSpecies.of(species);
        if (info == null) {
            return;
        }

        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
        TitleUtil.show(player, info.rarity().displayName(), "§f" + info.displayName());
        player.sendMessage(Branding.chatPrefix() + "§7Wylosowałeś peta: " + info.coloredName()
                + " §7- wybierz go komendą §f/pet§7!");

        if (info.rarity() == PetRarity.LEGENDARY || info.rarity() == PetRarity.MYTHIC) {
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
            String broadcast = Branding.chatPrefix() + "§e" + player.getName()
                    + " §dwylosował(a) w Schronisku Petów: " + info.coloredName() + "§d!";
            plugin.getServer().broadcastMessage(broadcast);
        }
    }
}
