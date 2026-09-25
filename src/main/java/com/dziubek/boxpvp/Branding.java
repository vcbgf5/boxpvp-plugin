package com.dziubek.boxpvp;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;

/** Marka "VantaNet" - fioletowy gradient zastępujący dawny akcent "§6§l" (złoty). */
public final class Branding {

    private Branding() {
    }

    public static final String NAME = "VantaNet";

    /**
     * Własne ikonki (font glyphy z resource packa, kodpointy 0xE820-0xE824) - zastępują
     * "chińskie znaczki" (brak glifu w domyślnym foncie dla zwykłych symboli unicode typu
     * ///) na własne, narysowane w stylu Minecrafta pixel-artowe ikonki. Działają
     * wszędzie, gdzie działa zwykły tekst (scoreboard, czat, lore, tytuły GUI).
     */
    public static final String ICON_STAR = "";
    public static final String ICON_DIAMOND = "";
    public static final String ICON_CHECK = "";
    public static final String ICON_HOURGLASS = "";
    public static final String ICON_POTION = "";

    /**
     * Te same ikonki jako ItemStacki (custom model na nieużywanych gdzie indziej materiałach -
     * NAUTILUS_SHELL/AMETHYST_SHARD/PRISMARINE_CRYSTALS/GHAST_TEAR/PHANTOM_MEMBRANE) - do użycia
     * jako dekoracyjne/informacyjne itemy w GUI (np. sklep, menu).
     */
    public enum Icon {
        STAR(Material.NAUTILUS_SHELL, "Gwiazdka"),
        DIAMOND(Material.AMETHYST_SHARD, "Diament"),
        CHECK(Material.PRISMARINE_CRYSTALS, "Ptaszek"),
        HOURGLASS(Material.GHAST_TEAR, "Klepsydra"),
        POTION(Material.PHANTOM_MEMBRANE, "Fiolka");

        final Material material;
        final String label;

        Icon(Material material, String label) {
            this.material = material;
            this.label = label;
        }
    }

    public static ItemStack icon(Icon icon, String displayName) {
        ItemStack item = new ItemStack(icon.material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName != null ? displayName : accent(icon.label));
            meta.setLore(Collections.emptyList());
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Obrazkowe glify (bitmap font providers, resource pack) używane przez BalanceHudManager do
     * zbudowania napisu "Kasa: X,XXK/M/B/T" wyłącznie z własnych tekstur (nie zwykłym kolorowym
     * tekstem). Litery/cyfry wycięte z prawdziwej czcionki Minecrafta
     * (assets/minecraft/textures/font/ascii.png z gry) - nie generyczny font, dzięki czemu
     * wszystkie glify (litery, cyfry, przecinek) dzielą tę samą siatkę pikseli co reszta gry i są
     * wzajemnie wyrównane (wspólna linia bazowa). Kodpointy 0xE850-0xE865.
     */
    public static final String KASA_LABEL = "";
    private static final String[] KASA_DIGITS = {
            "", "", "", "", "",
            "", "", "", "", ""
    };
    /** Przecinek (polski separator dziesiętny) - podmienia kropkę z String.format. */
    public static final String KASA_COMMA = "";
    /** Sufiksy skróconego zapisu salda (12,27K / 3,40M / ...). */
    private static final String KASA_K = "";
    private static final String KASA_M = "";
    private static final String KASA_B = "";
    private static final String KASA_T = "";

    /** Zamienia znak cyfry '0'-'9' na jego obrazkowy glif; kropkę/przecinek/K,M,B,T też. */
    public static String kasaDigit(char c) {
        if (c >= '0' && c <= '9') {
            return KASA_DIGITS[c - '0'];
        }
        if (c == '.' || c == ',') {
            return KASA_COMMA;
        }
        switch (c) {
            case 'K': return KASA_K;
            case 'M': return KASA_M;
            case 'B': return KASA_B;
            case 'T': return KASA_T;
            default: return String.valueOf(c);
        }
    }

    /**
     * Skraca kwotę do formatu K/M/B/T (tysiąc/milion/miliard/bilion), np. 12270 -> "12,27K",
     * 3400000 -> "3,40M". Poniżej 1000 zwraca zwykłe dwa miejsca po przecinku bez sufiksu.
     */
    public static String formatCompact(double value) {
        double abs = Math.abs(value);
        double divided = value;
        String suffix = "";
        if (abs >= 1_000_000_000_000.0) {
            divided = value / 1_000_000_000_000.0;
            suffix = "T";
        } else if (abs >= 1_000_000_000.0) {
            divided = value / 1_000_000_000.0;
            suffix = "B";
        } else if (abs >= 1_000_000.0) {
            divided = value / 1_000_000.0;
            suffix = "M";
        } else if (abs >= 1_000.0) {
            divided = value / 1_000.0;
            suffix = "K";
        }
        return String.format(java.util.Locale.ROOT, "%.2f", divided) + suffix;
    }

    /**
     * Pełnoekranowe tło GUI skrzyni "Srebna" (bitmap font providers, resource pack) - jeden
     * glif obrazkowy zakrywający cały panel ekwipunku (włącznie z zawsze widocznym plecakiem
     * gracza pod spodem), zamiast zwykłego tła kontenera + szklanych paneli jako wypełniacza.
     * Tytuł GUI to: niewidoczna spacja o ujemnym advance -8 (przesuwa kursor z domyślnego x=8 na
     * x=0, bo tekst tytułu w vanilla renderuje się zaczynając od x=8), potem §f (BIAŁY) - tytuły
     * kontenerów w vanilla są renderowane w stałym ciemnoszarym kolorze (0x404040), a ten kolor
     * MNOŻY się z kolorami bitmapowego glifu tak samo jak z każdym innym znakiem - bez §f nasz
     * cały obrazek wychodził przyciemniony/czarny, mimo że sama tekstura była poprawna - potem
     * sam obrazkowy glif (ascent=13=6+7 - domyślny y tytułu to 6, +7 to wysokość baseline
     * zwykłej czcionki - ustawia górną krawędź obrazka dokładnie na y=0 panelu). Kodpointy
     * 0xE870-0xE872.
     */
    private static final String SREBNA_SHIFT = "";
    private static final String SREBNA_ROLL_TITLE = SREBNA_SHIFT + "§f" + "";
    private static final String SREBNA_CHOICE_TITLE = SREBNA_SHIFT + "§f" + "";
    /** Tylko dla podglądu przy dokładnie 9 slotach (1 rząd) - większe pule nagród wracają do
     * zwykłego wyglądu, bo ten obrazek pokrywa tylko 1-rzędowy panel. */
    private static final String SREBNA_PREVIEW_TITLE = SREBNA_SHIFT + "§f" + "";
    /** Pełnoekranowe tło GUI matchmakingu (/duel, "Otwórz kolejkę"), ten sam mechanizm co Srebna. */
    public static final String DUELE_TITLE = SREBNA_SHIFT + "§f" + "";

    private static final String ZLOTA_ROLL_TITLE = SREBNA_SHIFT + "§f" + "";
    private static final String ZLOTA_CHOICE_TITLE = SREBNA_SHIFT + "§f" + "";
    private static final String ZLOTA_PREVIEW_TITLE = SREBNA_SHIFT + "§f" + "";

    /**
     * Zwraca własny obrazkowy tytuł/tło dla danej skrzyni i rodzaju ekranu, albo {@code null}
     * jeśli ta skrzynia nie ma customowej tekstury (wtedy GUI ma zwykły tekstowy tytuł + szklany
     * filler jak każda inna skrzynia). Jedno miejsce zamiast powielania warunków po nazwie
     * skrzyni w każdym z 3 GUI managerów (roll/choice/preview).
     */
    public static String customCrateTitle(String crateName, CrateScreen screen) {
        if ("Srebna".equalsIgnoreCase(crateName)) {
            return switch (screen) {
                case ROLL -> SREBNA_ROLL_TITLE;
                case CHOICE -> SREBNA_CHOICE_TITLE;
                case PREVIEW -> SREBNA_PREVIEW_TITLE;
            };
        }
        if ("Zlota".equalsIgnoreCase(crateName)) {
            return switch (screen) {
                case ROLL -> ZLOTA_ROLL_TITLE;
                case CHOICE -> ZLOTA_CHOICE_TITLE;
                case PREVIEW -> ZLOTA_PREVIEW_TITLE;
            };
        }
        return null;
    }

    public enum CrateScreen { ROLL, CHOICE, PREVIEW }

    static final int DARK_PURPLE = 0x4B0082;    // indigo / ciemny fiolet
    static final int LIGHT_LAVENDER = 0xD8B4FE; // jasny fiolet / lawenda

    /** Pogrubiony gradient - odpowiednik dawnego "§6§l" dla nagłówków/tytułów/etykiet. */
    public static String accent(String text) {
        return GradientText.apply(text, DARK_PURPLE, LIGHT_LAVENDER, true);
    }

    public static String accent() {
        return accent(NAME);
    }

    /** Wersja bez pogrubienia (np. na podtytuły). */
    public static String accentLight(String text) {
        return GradientText.apply(text, DARK_PURPLE, LIGHT_LAVENDER, false);
    }

    /** Prefiks "[VantaNet] " do ogłoszeń na czacie (broadcastMessage). */
    public static String chatPrefix() {
        return "§8[" + accent(NAME) + "§8] §f";
    }
}
