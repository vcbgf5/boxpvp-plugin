package com.dziubek.boxpvp;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

/**
 * Trzyma dane potrzebne do wystartowania losowania (z animacją albo bez) po tym,
 * jak gracz wybierze opcję w menu CrateOpenChoiceGuiManager.
 */
public class CrateOpenChoiceGuiHolder implements InventoryHolder {

    private final String crateName;
    private final List<CrateReward> rewards;
    private final Location crateBlockLocation;

    public CrateOpenChoiceGuiHolder(String crateName, List<CrateReward> rewards, Location crateBlockLocation) {
        this.crateName = crateName;
        this.rewards = rewards;
        this.crateBlockLocation = crateBlockLocation;
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

    @Override
    public Inventory getInventory() {
        return null;
    }
}
