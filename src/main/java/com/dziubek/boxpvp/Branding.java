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
     * zbudowania napisu "Kasa: X,XX" wyłącznie z własnych tekstur (nie zwykłym kolorowym tekstem).
     * Litery/cyfry wycięte z prawdziwej czcionki Minecrafta
     * (assets/minecraft/textures/font/ascii.png z gry) - nie generyczny font, dzięki czemu
     * wszystkie glify (litery, cyfry, przecinek) dzielą tę samą siatkę pikseli co reszta gry i są
     * wzajemnie wyrównane (wspólna linia bazowa). Kodpointy 0xE84F-0xE85B.
     */
    public static final String KASA_COIN = "";
    public static final int KASA_COIN_WIDTH = 22;
    public static final String KASA_LABEL = "";
    public static final int KASA_LABEL_WIDTH = 52;
    private static final String[] KASA_DIGITS = {
            "", "", "", "", "",
            "", "", "", "", ""
    };
    private static final int KASA_DIGIT_WIDTH = 12;
    /** Przecinek (polski separator dziesiętny) - podmienia kropkę z String.format. */
    public static final String KASA_COMMA = "";
    private static final int KASA_COMMA_WIDTH = 4;

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

    /** Szerokość w px glifu zwróconego przez kasaDigit() dla znaku c (0 dla nieobsługiwanych). */
    public static int kasaDigitWidth(char c) {
        if (c >= '0' && c <= '9') {
            return KASA_DIGIT_WIDTH;
        }
        if (c == '.') {
            return KASA_COMMA_WIDTH;
        }
        return 0;
    }

    /**
     * Eksperyment: "TEST 1"/"TEST 2" wbudowane w wysokie, w większości przezroczyste obrazy
     * (screentest_1.png/screentest_2.png), tak żeby wystawały daleko w dół ekranu ponad zwykłą
     * pozycję bossbara. Doklejone do tego samego bossbara/tytułu co Kasa HUD (BalanceHudManager),
     * każdy własnym "wierszem" wyśrodkowanym niezależnie (patrz centeredRow()). Kodpointy
     * 0xE860-0xE861.
     */
    public static final String SCREEN_TEST_1 = "";
    public static final int SCREEN_TEST_1_WIDTH = 104;
    public static final String SCREEN_TEST_2 = "";
    public static final int SCREEN_TEST_2_WIDTH = 104;

    /**
     * Technika z prawdziwego kodu BetterHud (kr.toxicity.hud.component.LayoutComponentContainer):
     * żeby doczepić kolejny "wiersz" (o znanej szerokości w px) do WSPÓLNEGO bossbara i mimo to
     * wyśrodkować go NIEZALEŻNIE od pozostałych, owija się go spacją -polowaSzerokosc PRZED i
     * -polowaSzerokosc PO. Suma tych dwóch spacji i szerokości wiersza wynosi zero, więc kursor
     * wraca do wspólnego punktu zerowego przed kolejnym wierszem - a ponieważ Minecraft centruje
     * CAŁY tytuł bossbara na bazie sumy zaawansowań (tutaj: zero), ten wspólny punkt zerowy
     * pokrywa się ze środkiem ekranu, więc każdy wiersz, wyśrodkowany wokół zera, wyśrodkowuje się
     * tym samym na ekranie - niezależnie od szerokości pozostałych wierszy.
     */
    public static String centeredRow(String content, int widthPx) {
        int left = widthPx / 2;
        int right = widthPx - left;
        return spaceOffset(-left) + content + spaceOffset(-right);
    }

    /**
     * Zwraca sekwencję znaków "space" (font providery o zadeklarowanym przesunięciu będącym
     * potęgą dwójki, kodpointy 0xE870-0xE883) sumujących się dokładnie do zadanej liczby pikseli -
     * standardowy rozkład dwójkowy, pozwala uzyskać DOWOLNE całkowite przesunięcie (do ±1023px)
     * za pomocą co najwyżej 10 znaków.
     */
    private static final int[] SPACE_BITS = {512, 256, 128, 64, 32, 16, 8, 4, 2, 1};
    private static final String[] SPACE_POS = {
            "", "", "", "", "",
            "", "", "", "", ""
    };
    private static final String[] SPACE_NEG = {
            "", "", "", "", "",
            "", "", "", "", ""
    };

    public static String spaceOffset(int pixels) {
        if (pixels == 0) {
            return "";
        }
        boolean negative = pixels < 0;
        int remaining = Math.abs(pixels);
        String[] table = negative ? SPACE_NEG : SPACE_POS;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SPACE_BITS.length; i++) {
            if (remaining >= SPACE_BITS[i]) {
                sb.append(table[i]);
                remaining -= SPACE_BITS[i];
            }
        }
        return sb.toString();
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
