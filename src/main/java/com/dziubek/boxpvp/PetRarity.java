package com.dziubek.boxpvp;

/** Rzadkość peta - używana przy losowaniu w schronisku (wyższa waga = częściej wypada). */
public enum PetRarity {
    COMMON("§7Zwykły", 45),
    UNCOMMON("§aNiezwykły", 28),
    EPIC("§5Epicki", 15),
    LEGENDARY("§6Legendarny", 8),
    MYTHIC("§d§lMityczny", 4);

    private final String displayName;
    private final int weight;

    PetRarity(String displayName, int weight) {
        this.displayName = displayName;
        this.weight = weight;
    }

    public String displayName() {
        return displayName;
    }

    public int weight() {
        return weight;
    }
}
