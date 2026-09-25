package com.dziubek.boxpvp;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Map;

/**
 * Statyczny katalog 8 gatunków petów z paczki "Cubees" - nazwa wyświetlana, rzadkość i pasywna
 * umiejętność (efekt eliksiru odświeżany co kilka sekund, dopóki pet jest aktywny).
 */
public final class PetSpecies {

    public record Info(String species, String displayName, PetRarity rarity, List<PotionEffectType> abilities) {
        public String coloredName() {
            return rarity.displayName() + " §f" + displayName;
        }
    }

    private static final Map<String, Info> ALL = Map.ofEntries(
            Map.entry("stone", new Info("stone", "Kamienny Cubee", PetRarity.COMMON, List.of(PotionEffectType.HASTE))),
            Map.entry("grass", new Info("grass", "Trawiasty Cubee", PetRarity.COMMON, List.of(PotionEffectType.REGENERATION))),
            Map.entry("water", new Info("water", "Wodny Cubee", PetRarity.UNCOMMON, List.of(PotionEffectType.WATER_BREATHING))),
            Map.entry("skeleton", new Info("skeleton", "Szkieletowy Cubee", PetRarity.UNCOMMON, List.of(PotionEffectType.SPEED))),
            Map.entry("evil", new Info("evil", "Złowrogi Cubee", PetRarity.EPIC, List.of(PotionEffectType.STRENGTH))),
            Map.entry("fire", new Info("fire", "Ognisty Cubee", PetRarity.EPIC, List.of(PotionEffectType.FIRE_RESISTANCE))),
            Map.entry("tnt", new Info("tnt", "Wybuchowy Cubee", PetRarity.LEGENDARY, List.of(PotionEffectType.ABSORPTION))),
            Map.entry("good", new Info("good", "Dobry Cubee", PetRarity.MYTHIC,
                    List.of(PotionEffectType.REGENERATION, PotionEffectType.ABSORPTION)))
    );

    private PetSpecies() {
    }

    public static Info of(String species) {
        return ALL.get(species);
    }

    public static Map<String, Info> all() {
        return ALL;
    }

    /** Efekty odświeżane co kilka sekund graczowi z aktywnym petem (amplifier 0 = poziom I). */
    public static List<PotionEffect> effects(Info info) {
        return info.abilities().stream()
                .map(type -> new PotionEffect(type, 220, info.rarity() == PetRarity.MYTHIC ? 1 : 0, true, false, true))
                .toList();
    }
}
