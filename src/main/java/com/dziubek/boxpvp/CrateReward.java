package com.dziubek.boxpvp;

import org.bukkit.inventory.ItemStack;

/**
 * Pojedyncza nagroda w skrzyni: przedmiot (amount = ilość wypłaty) + procentowa szansa (0-100).
 * "locked" = admin ręcznie wpisał tę szansę na czacie (w przeciwieństwie do automatycznie wyliczonej).
 */
public record CrateReward(ItemStack item, double chance, boolean locked) {
}
