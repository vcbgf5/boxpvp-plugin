package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Trzyma dane potrzebne do wystartowania losowania (z animacją albo bez) po tym,
 * jak gracz wybierze opcję w menu CrateOpenChoiceGuiManager. keyItem - dokładna instancja
 * klucza z ekwipunku gracza w momencie kliknięcia bloku, zużywana DOPIERO gdy gracz faktycznie
 * wybierze sposób otwarcia (nie od razu przy kliknięciu) - null przy darmowym otwarciu bez klucza.
 */
public class CrateOpenChoiceGuiHolder implements InventoryHolder {

    private final String crateName;
    private final List<CrateReward> rewards;
    private final Location crateBlockLocation;
    private final ItemStack keyItem;

    public CrateOpenChoiceGuiHolder(String crateName, List<CrateReward> rewards, Location crateBlockLocation,
                                     ItemStack keyItem) {
        this.crateName = crateName;
        this.rewards = rewards;
        this.crateBlockLocation = crateBlockLocation;
        this.keyItem = keyItem;
    }

    public String getCrateName() {
        return crateName;
    }

    public List<CrateReward> getRewards() {
        return rewards;
    }

    public Location getCrateBlockLocation() {
        return crateBlockLocation;
    }

    public ItemStack getKeyItem() {
        return keyItem;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
