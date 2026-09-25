package com.dziubek.boxpvp;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * BetterModel generuje WŁASNY resourcepack (plugins/BetterModel/build.zip) z tekstur modeli
 * petów, o którym nasz zwykły ResourcePackPusher (link do GitHuba) nic nie wie - stąd różowo-
 * czarny brak tekstury w grze mimo że sam model (geometria) się ładuje.
 *
 * Ta klasa łączy oba pack'i w jeden (nasz z GitHuba + build.zip z lokalnego dysku serwera) i
 * wystawia go małym wbudowanym serwerem HTTP (bez dodatkowych zależności - com.sun.net.httpserver
 * jest wbudowane w JDK), żeby ResourcePackPusher mógł wysłać graczom JEDEN, kompletny pack.
 *
 * WAŻNE: wymaga otwartego/przekierowanego portu {@link #PORT} na serwerze (ten sam publiczny
 * adres co do gry, PUBLIC_HOST) - bez tego klienci nie będą w stanie pobrać paczki stąd.
 */
public final class PetResourcePackMerger {

    public static final int PORT = 25566;
    private static final String PUBLIC_HOST = "vantanet.pl";

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static volatile byte[] mergedBytes;
    private static volatile byte[] mergedHash;
    private static volatile boolean serverStarted = false;
    private static HttpServer server;

    private PetResourcePackMerger() {
    }

    public static void start(BoxPvpPlugin plugin) {
        start(plugin, 0);
    }

    /**
     * Próbuje wystartować, a jeśli port jest jeszcze zajęty (typowo: stary/zombie proces
     * poprzedniego uruchomienia serwera jeszcze go trzyma tuż po restarcie), próbuje ponownie
     * co 10s, maks. 6 razy (~1 minuta) - zanim się podda i zostanie przy zwykłej paczce z GitHuba.
     */
    private static void start(BoxPvpPlugin plugin, int attempt) {
        if (server != null) {
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/pack.zip", exchange -> {
                byte[] body = mergedBytes;
                if (body == null) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, body.length);
                try (var out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.setExecutor(null);
            server.start();
            serverStarted = true;
            plugin.getLogger().info("Serwer scalonego resourcepacku petow wystartowal na porcie " + PORT + ".");
        } catch (IOException e) {
            server = null;
            serverStarted = false;
            if (attempt < 6) {
                plugin.getLogger().warning("Port " + PORT + " jeszcze zajety (proba " + (attempt + 1)
                        + "/6, prawdopodobnie stary proces serwera jeszcze go trzyma) - ponawiam za 10s.");
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> start(plugin, attempt + 1), 200L);
            } else {
                plugin.getLogger().warning("Nie udalo sie wystartowac serwera resourcepacku petow (port " + PORT
                        + " nadal zajety po 6 probach) - gracze dostana zwykla (GitHubowa) paczke bez "
                        + "petow, dopoki port sie nie zwolni i serwer nie zostanie zrestartowany: " + e.getMessage());
            }
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        serverStarted = false;
    }

    /** True TYLKO gdy serwer HTTP faktycznie nasłuchuje I paczka jest scalona - inaczej
     * ResourcePackPusher musi wrócić do zwykłego linku z GitHuba, żeby gracze dostali chociaż
     * to, co zawsze działało (a nie "Connection refused" na martwym serwerze). */
    public static boolean isRunning() {
        return serverStarted && mergedBytes != null;
    }

    public static byte[] hash() {
        return mergedHash;
    }

    public static String url() {
        return "http://" + PUBLIC_HOST + ":" + PORT + "/pack.zip";
    }

    /**
     * Sieciowe/I-O - wołać TYLKO z wątku async. Pobiera nasz pack z GitHuba, dokleja lokalny
     * plugins/BetterModel/build.zip i liczy SHA-1 scalonej paczki na nowo. Zwraca false (bez
     * żadnego efektu) gdy BetterModel jeszcze nie wygenerował build.zip - stary/GitHubowy pack
     * dalej działa jak wcześniej.
     */
    public static boolean refresh(BoxPvpPlugin plugin) {
        File betterModelBuild = new File(plugin.getDataFolder().getParentFile(),
                "BetterModel" + File.separator + "build.zip");
        if (!betterModelBuild.exists()) {
            return false;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ResourcePackPusher.URL))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                return false;
            }

            byte[] merged = merge(response.body(), betterModelBuild);
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            mergedHash = digest.digest(merged);
            mergedBytes = merged;
            plugin.getLogger().info("Scalono resourcepack petow (" + merged.length + " bajtow)"
                    + (serverStarted ? " - gotowy do wyslania graczom." : " - ALE serwer HTTP nie dziala, "
                    + "wiec zostanie wyslana zwykla paczka z GitHuba zamiast tej scalonej."));
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("Nie udalo sie scalic resourcepacku petow: " + e.getMessage());
            return false;
        }
    }

    private static byte[] merge(byte[] basePack, File betterModelBuild) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Set<String> written = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(buffer)) {
            try (ZipInputStream baseZis = new ZipInputStream(new ByteArrayInputStream(basePack))) {
                copyEntries(baseZis, zos, written);
            }
            try (InputStream fileIn = new FileInputStream(betterModelBuild);
                 ZipInputStream extraZis = new ZipInputStream(fileIn)) {
                copyEntries(extraZis, zos, written);
            }
        }
        return buffer.toByteArray();
    }

    private static void copyEntries(ZipInputStream zis, ZipOutputStream zos, Set<String> written) throws IOException {
        ZipEntry entry;
        byte[] buf = new byte[8192];
        while ((entry = zis.getNextEntry()) != null) {
            if (entry.isDirectory() || !written.add(entry.getName())) {
                continue;
            }
            zos.putNextEntry(new ZipEntry(entry.getName()));
            int len;
            while ((len = zis.read(buf)) > 0) {
                zos.write(buf, 0, len);
            }
            zos.closeEntry();
        }
    }
}
