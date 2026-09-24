package com.dziubek.boxpvp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Konfiguracja własnego HUD-u (Kasa + eksperymentalne TEST1/TEST2) pobierana NA ŻYWO z pliku
 * hud-config.json w repo na GitHubie (raw.githubusercontent.com) - NIE jest zaszyta w jarze.
 * Dzięki temu włączanie/wyłączanie wierszy i drobne poziome dostrojenie (nudgeX) nie wymaga
 * przebudowania i wgrania nowego jara - wystarczy zmienić plik w repo i wykonać /reloadhud.
 *
 * Rzeczy wymagające zmiany OBRAZKÓW w resource packu (wysokość/ascent glifów) nadal wymagają
 * pełnego rebuildu paczki - ten config obejmuje tylko to, co da się dostroić samym tekstem/liczbą.
 */
public class HudConfigLoader {

    private static final String URL =
            "https://raw.githubusercontent.com/vcbgf5/boxpvp-plugin/main/hud-config.json";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public volatile boolean kasaEnabled = true;
    public volatile int kasaNudgeX = 0;
    public volatile boolean test1Enabled = false;
    public volatile int test1NudgeX = 0;
    public volatile boolean test2Enabled = false;
    public volatile int test2NudgeX = 0;
    /** Gdy true, HUD pokazuje minimalny test diagnostyczny zamiast normalnej zawartości. */
    public volatile boolean debugMode = false;

    /** Synchroniczne pobranie - wołać TYLKO z wątku async (sieć). Zwraca false przy błędzie. */
    public boolean reload() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
            kasaEnabled = getBool(obj, "kasaEnabled", kasaEnabled);
            kasaNudgeX = getInt(obj, "kasaNudgeX", kasaNudgeX);
            test1Enabled = getBool(obj, "test1Enabled", test1Enabled);
            test1NudgeX = getInt(obj, "test1NudgeX", test1NudgeX);
            test2Enabled = getBool(obj, "test2Enabled", test2Enabled);
            test2NudgeX = getInt(obj, "test2NudgeX", test2NudgeX);
            debugMode = getBool(obj, "debugMode", debugMode);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean getBool(JsonObject obj, String key, boolean fallback) {
        return obj.has(key) ? obj.get(key).getAsBoolean() : fallback;
    }

    private static int getInt(JsonObject obj, String key, int fallback) {
        return obj.has(key) ? obj.get(key).getAsInt() : fallback;
    }
}
