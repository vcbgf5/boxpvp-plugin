package com.dziubek.boxpvp;

import org.bukkit.inventory.ItemStack;

/**
 * Pojedyncza wymiana u handlarza: oddajesz "cost", dostajesz "reward" - przedmiot za
 * przedmiot, bez pieniędzy (jak handel z wieśniakiem).
 */
public record Trade(ItemStack cost, ItemStack reward) {
}
