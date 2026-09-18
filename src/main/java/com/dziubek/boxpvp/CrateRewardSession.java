package com.dziubek.boxpvp;

import org.bukkit.inventory.Inventory;

/**
 * Stan konfiguracji nagród skrzyni dla jednego admina - trzyma TĘ SAMĄ instancję Inventory
 * między zamknięciami/otwarciami, żeby przedmioty i ustawione już procenty nie znikały
 * podczas dopytywania na czacie o kolejny procent.
 */
public class CrateRewardSession {

    public final String crateName;
    public final Inventory inventory;

    // null = nic nie czekamy; numer slotu = czekamy na wpisanie procentu dla przedmiotu w tym slocie
    public Integer awaitingChatForSlot = null;

    // true = to MY zamykamy GUI programowo (żeby zapytać o procent na czacie) - nie zapisuj przy tym zamknięciu
    public boolean suppressNextClose = false;

    public CrateRewardSession(String crateName, Inventory inventory) {
        this.crateName = crateName;
        this.inventory = inventory;
    }
}
