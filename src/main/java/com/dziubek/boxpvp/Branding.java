package com.dziubek.boxpvp;

import net.kyori.adventure.text.Component;
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
     * Technika "custom GUI przez font" (test dla skrzynek): jeden znak font-glyph o celowo
     * duzej wysokosci (height:256, ascent:19 z resource packa) renderuje sie jako CALE tlo
     * menu zamiast zwyklej tekstury skrzyni - nadmiar wysokosci wychodzi poza okno i jest
     * ucinany przez klienta. Dziala u KAZDEGO gracza (w przeciwienstwie do OptiFine), a pliki
     * moga miec dowolna nazwe (crate_1.png, crate_2.png) - to jedyny sposob na to bez OptiFine.
     * SPACE_NEG8 cofa kursor o 8px (domyslny margines tekstu w tytule GUI).
     */
    private static final String SPACE_NEG8 = "";
    public static final String GUI_BG_CRATE_1 = "";
    public static final String GUI_BG_CRATE_2 = "";

    /** Buduje tytul GUI z wlasnym tlem (patrz GUI_BG_* powyzej) zamiast zwyklego tekstu. */
    public static Component customGuiTitle(String backgroundGlyph) {
        return Component.text(SPACE_NEG8 + backgroundGlyph);
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
