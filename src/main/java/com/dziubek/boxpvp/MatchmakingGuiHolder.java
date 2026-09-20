package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MatchmakingGuiHolder implements InventoryHolder {

    public enum Kind {
        JOIN_PROMPT,
        PREVIEW
    }

    private final Kind kind;

    public MatchmakingGuiHolder(Kind kind) {
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
