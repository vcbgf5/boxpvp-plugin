package com.dziubek.boxpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Wysyła resource pack graczom bezpośrednio z pluginu (Player#setResourcePack), zamiast polegać
 * na ręcznie wklejanych liniach resource-pack/resource-pack-sha1 w server.properties. Adres URL
 * wskazuje na "latest" release GitHuba (przekierowanie zawsze na najnowsze wydanie). SHA-1 NIE
 * jest hardkodowany w kodzie - CI (.github/workflows/build.yml) liczy go z resourcepack.zip PRZED
 * buildem jara i zapisuje do resourcepack.sha1 (pakowany razem z jarem), więc ta klasa zawsze ma
 * hash dokładnie pasujący do paczki wydanej RAZEM z tym jarem - nie da się już rozjechać wersji.
 * Prawdziwy hash (zamiast null) jest też ważny dla klienta Minecrafta - zgodnie ze specyfikacją
 * protokołu to WŁAŚNIE hash decyduje, czy klient ma coś ponownie pobrać, czy zaufać swojemu cache.
 */
public final class ResourcePackPusher {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private static final String URL =
            "https://github.com/vcbgf5/boxpvp-plugin/releases/latest/download/BoxPvP-ResourcePack.zip";
    private static final Component PROMPT = LEGACY.deserialize("§7Zainstaluj pack, zeby zobaczyc pelny wyglad serwera!");
    private static final byte[] SHA1 = loadPackSha1();

    private ResourcePackPusher() {
    }

    public static void push(Player player) {
        player.setResourcePack(URL, SHA1, PROMPT, false);
    }

    private static byte[] loadPackSha1() {
        try (InputStream in = ResourcePackPusher.class.getResourceAsStream("/resourcepack.sha1")) {
            if (in == null) {
                return null;
            }
            String hex = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            byte[] bytes = new byte[hex.length() / 2];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            }
            return bytes;
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }
}
