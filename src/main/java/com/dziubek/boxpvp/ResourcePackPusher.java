package com.dziubek.boxpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Wysyła resource pack graczom bezpośrednio z pluginu (Player#setResourcePack), zamiast polegać
 * na ręcznie wklejanych liniach resource-pack/resource-pack-sha1 w server.properties. Adres URL
 * wskazuje na "latest" release GitHuba (przekierowanie zawsze na najnowsze wydanie) - CELOWO bez
 * hasha, żeby nigdy nie trzeba było pamiętać o ręcznej aktualizacji tej stałej przy każdym nowym
 * buildzie paczki (wcześniejsza wersja z hardkodowanym URL+SHA1 konkretnej wersji powodowała, że
 * gracze utykali na starej, nieaktualnej paczce po kolejnych zmianach - admin po prostu podmienia
 * jar, bez ryzyka rozjazdu wersji).
 */
public final class ResourcePackPusher {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final String URL =
            "https://github.com/vcbgf5/boxpvp-plugin/releases/latest/download/BoxPvP-ResourcePack.zip";
    private static final Component PROMPT = LEGACY.deserialize("§7Zainstaluj pack, zeby zobaczyc pelny wyglad serwera!");

    private ResourcePackPusher() {
    }

    public static void push(Player player) {
        player.setResourcePack(URL, null, PROMPT, false);
    }
}
