package com.dziubek.boxpvp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ładuje przy starcie wszystkie 3D modele skrzyń (skonwertowane z Blockbencha) zaszyte jako
 * zasoby pluginu w src/main/resources/models/*.model.json.
 */
public class CrateModelRegistry {

    private static final List<String> MODEL_NAMES = List.of(
            "common_crate", "rare_crate", "legendary_crate", "cosmetic_crate", "vote_crate"
    );

    private final Map<String, CrateModel> models = new LinkedHashMap<>();
    private final BoxPvpPlugin plugin;

    public CrateModelRegistry(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        for (String name : MODEL_NAMES) {
            try (InputStream in = plugin.getResource("models/" + name + ".model.json")) {
                if (in == null) {
                    plugin.getLogger().warning("Brak zasobu modelu skrzyni: " + name);
                    continue;
                }
                String json = readAll(in);
                Map<String, Object> root = SimpleJson.parseObject(json);
                models.put(name, CrateModel.fromJson(name, root));
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("Nie udało się wczytać modelu skrzyni '" + name + "': " + e);
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    public CrateModel get(String name) {
        return models.get(name);
    }

    public boolean exists(String name) {
        return models.containsKey(name);
    }

    public List<String> names() {
        return List.copyOf(models.keySet());
    }
}
