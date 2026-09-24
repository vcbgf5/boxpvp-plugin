package com.dziubek.boxpvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

/**
 * Wysyła resource pack graczom bezpośrednio z pluginu (Player#setResourcePack), zamiast polegać
 * na ręcznie wklejanych liniach resource-pack/resource-pack-sha1 w server.properties. Adres URL
 * wskazuje na "latest" release GitHuba (przekierowanie zawsze na najnowsze wydanie).
 *
 * SHA-1 NIE jest wpisany na sztywno ani nawet zaszyty w jarze przy buildzie - jest liczony NA ŻYWO
 * (refreshHash()) przez pobranie AKTUALNEJ zawartości "latest" paczki i policzenie jej hasha w
 * locie. Dzięki temu nawet zmiana samej paczki w repo (bez nowego builda jara) jest w pełni
 * obsługiwana - /reloadhud wywołuje refreshHash() i wysyła paczkę z jej PRAWDZIWYM, aktualnym
 * hashem, więc klient zawsze poprawnie wykrywa czy ma pobrać coś nowego (zgodnie ze specyfikacją
 * protokołu Minecrafta - to hash decyduje, nie sama zmiana URL).
 */
public final class ResourcePackPusher {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final String URL =
            "https://github.com/vcbgf5/boxpvp-plugin/releases/latest/download/BoxPvP-ResourcePack.zip";
    private static final Component PROMPT = LEGACY.deserialize("§7Zainstaluj pack, zeby zobaczyc pelny wyglad serwera!");

    private static volatile byte[] hash;

    private ResourcePackPusher() {
    }

    public static void push(Player player) {
        player.setResourcePack(URL, hash, PROMPT, false);
    }

    /** Sieciowe - wołać TYLKO z wątku async. Pobiera aktualną paczkę i liczy jej SHA-1 na nowo. */
    public static boolean refreshHash() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return false;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            hash = digest.digest(response.body());
            return true;
        } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
            return false;
        }
    }
}
