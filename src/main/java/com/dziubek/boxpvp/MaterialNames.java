package com.dziubek.boxpvp;

import org.bukkit.Material;

/** Zamienia nazwy materiałów Bukkita (np. "DIAMOND_SWORD") na czytelne etykiety ("Diamond sword"). */
public final class MaterialNames {

    private MaterialNames() {
    }

    public static String humanize(Material material) {
        String name = material.name().replace('_', ' ').toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
