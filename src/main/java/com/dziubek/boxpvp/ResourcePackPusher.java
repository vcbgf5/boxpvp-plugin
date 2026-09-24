package com.dziubek.boxpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

/**
 * Wysyła resource pack graczom bezpośrednio z pluginu (Player#setResourcePack), zamiast polegać
 * na ręcznie wklejanych liniach resource-pack/resource-pack-sha1 w server.properties. Adres URL i
 * SHA-1 poniżej są aktualizowane w kodzie przy każdym wydaniu nowej paczki (razem z rebuildem jara)
 * - admin po prostu podmienia jar na serwerze, bez edytowania configów.
 */
public final class ResourcePackPusher {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final String URL =
            "https://github.com/vcbgf5/boxpvp-plugin/releases/download/v1.0.86/BoxPvP-ResourcePack.zip";
    private static final String SHA1_HEX = "478fa18faa26f94796cd0729f05f2e8db56f31a5";
    private static final Component PROMPT = LEGACY.deserialize("§7Zainstaluj pack, zeby zobaczyc pelny wyglad serwera!");

    private ResourcePackPusher() {
    }

    public static void push(Player player) {
        player.setResourcePack(URL, hexToBytes(SHA1_HEX), PROMPT, false);
    }

    private static byte[] hexToBytes(String hex) {
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
