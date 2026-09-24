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
     * Znak "przesuwający kursor" (font provider typu space, +200px) - używany do przesunięcia
     * tekstu bossbara (domyślnie wyśrodkowany na górze ekranu) w stronę prawego rogu, tak żeby
     * BalanceHudManager mógł zbudować w ten sposób stałe "GUI" w rogu ekranu z samego tekstu.
     * Wartość dobrana w ciemno (bez testów w grze) - może wymagać korekty.
     */
    public static final String SPACE_POS200 = "";

    /**
     * Obrazkowe glify (bitmap font providers, resource pack) używane przez BalanceHudManager do
     * zbudowania napisu "Kasa: X, XX monety" wyłącznie z własnych tekstur (nie zwykłym kolorowym
     * tekstem). Litery/cyfry wycięte z prawdziwej czcionki Minecrafta
     * (assets/minecraft/textures/font/ascii.png z gry) - nie generyczny font. Kodpointy
     * 0xE84F-0xE85D.
     */
    public static final String KASA_COIN = "";
    public static final String KASA_LABEL = "";
    private static final String[] KASA_DIGITS = {
            "", "", "", "", "",
            "", "", "", "", ""
    };
    /** Przecinek (polski separator dziesiętny) - podmienia kropkę z String.format. */
    public static final String KASA_COMMA = "";
    /** Napis "monety" (zastępuje symbol $) - doklejany po saldzie. */
    public static final String KASA_MONETY_LABEL = "";
    /** Mały odstęp (space provider) między saldem a KASA_MONETY_LABEL. */
    public static final String KASA_GAP = "";

    /** Zamienia znak cyfry '0'-'9' na jego obrazkowy glif; kropkę zamienia na przecinek. */
    public static String kasaDigit(char c) {
        if (c >= '0' && c <= '9') {
            return KASA_DIGITS[c - '0'];
        }
        if (c == '.') {
            return KASA_COMMA;
        }
        return String.valueOf(c);
    }

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
