package com.dziubek.boxpvp;

import java.util.List;
import java.util.Map;

/**
 * Statyczny katalog 12 kosmetycznych skrzydeł z paczki "Wing Cosmetics" (Artillex-Studios) -
 * czysto wizualne, bez rzadkości/efektów, każdy gracz może wybrać dowolne w każdej chwili.
 */
public final class WingSpecies {

    /** Stała kolejność 12 skrzydeł - używana m.in. do przypisania slotów w WingGuiManager. */
    public static final List<String> ORDER = List.of(
            "angel", "demon", "dragon", "phoenix", "eagle", "butterfly",
            "astronaut", "golden", "purple", "bluefire", "ocean", "brown"
    );

    public record Info(String species, String displayName) {
    }

    private static final Map<String, Info> ALL = Map.ofEntries(
            Map.entry("angel", new Info("angel", "§fAnielskie")),
            Map.entry("demon", new Info("demon", "§cDemoniczne")),
            Map.entry("dragon", new Info("dragon", "§2Smocze")),
            Map.entry("phoenix", new Info("phoenix", "§6Feniksa")),
            Map.entry("eagle", new Info("eagle", "§eOrle")),
            Map.entry("butterfly", new Info("butterfly", "§dMotyle")),
            Map.entry("astronaut", new Info("astronaut", "§7Kosmonauty")),
            Map.entry("golden", new Info("golden", "§6Złote")),
            Map.entry("purple", new Info("purple", "§5Fioletowe")),
            Map.entry("bluefire", new Info("bluefire", "§bNiebieski Ogień")),
            Map.entry("ocean", new Info("ocean", "§3Oceaniczne")),
            Map.entry("brown", new Info("brown", "§cBrązowe"))
    );

    private WingSpecies() {
    }

    public static Info of(String species) {
        return ALL.get(species);
    }

    public static Map<String, Info> all() {
        return ALL;
    }
}
