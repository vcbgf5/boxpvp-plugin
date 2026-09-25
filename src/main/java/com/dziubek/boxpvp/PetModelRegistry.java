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
 * Ładuje przy starcie wszystkie 3D modele petów (skonwertowane z Blockbencha, paczka "Cubees")
 * zaszyte jako zasoby pluginu w src/main/resources/models/pet_*.model.json.
 */
public class PetModelRegistry {

    public static final List<String> SPECIES = List.of(
            "evil", "fire", "good", "grass", "skeleton", "stone", "tnt", "water"
    );

    private final Map<String, PetModel> models = new LinkedHashMap<>();
    private final BoxPvpPlugin plugin;

    public PetModelRegistry(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        for (String species : SPECIES) {
            String name = "pet_" + species;
            try (InputStream in = plugin.getResource("models/" + name + ".model.json")) {
                if (in == null) {
                    plugin.getLogger().warning("Brak zasobu modelu peta: " + name);
                    continue;
                }
                String json = readAll(in);
                Map<String, Object> root = SimpleJson.parseObject(json);
                models.put(species, PetModel.fromJson(name, root));
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().warning("Nie udało się wczytać modelu peta '" + name + "': " + e);
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

    /** Model 3D dla nazwy gatunku peta (np. "good", "fire") - bez prefixu "pet_". */
    public PetModel get(String species) {
        return models.get(species);
    }

    public boolean exists(String species) {
        return models.containsKey(species);
    }
}
