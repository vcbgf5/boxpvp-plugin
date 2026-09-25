package com.dziubek.boxpvp;

import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Kopiuje surowe pliki .bbmodel (paczka "Cubees") do plugins/BetterModel/models/ i odświeża
 * BetterModel, żeby je wczytał. BetterModel to ZEWNĘTRZNY plugin (nie jest zbudowany razem z
 * nami) - musi być zainstalowany na serwerze, żeby pety w ogóle się wyrenderowały; bez niego
 * po prostu nic tu nie zadziała (miękka zależność, sprawdzana zanim spróbujemy cokolwiek zrobić).
 */
public final class BetterModelInstaller {

    private static final String[] SPECIES = {"evil", "fire", "good", "grass", "skeleton", "stone", "tnt", "water"};

    private BetterModelInstaller() {
    }

    public static boolean isBetterModelPresent() {
        return Bukkit.getPluginManager().getPlugin("BetterModel") != null;
    }

    public static void installModels(BoxPvpPlugin plugin) {
        if (!isBetterModelPresent()) {
            plugin.getLogger().warning("BetterModel nie jest zainstalowany - system petów (3D modele) nie "
                    + "będzie działać, dopóki nie dodasz go do /plugins/.");
            return;
        }

        File pluginsDir = plugin.getDataFolder().getParentFile();
        File modelsDir = new File(pluginsDir, "BetterModel" + File.separator + "models");
        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            plugin.getLogger().warning("Nie udało się utworzyć plugins/BetterModel/models/");
            return;
        }

        boolean copiedAny = false;
        for (String species : SPECIES) {
            String resourceName = "bettermodel/cubee-" + species + ".bbmodel";
            File target = new File(modelsDir, "cubee-" + species + ".bbmodel");
            try (InputStream in = plugin.getResource(resourceName)) {
                if (in == null) {
                    plugin.getLogger().warning("Brak zasobu modelu peta: " + resourceName);
                    continue;
                }
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                copiedAny = true;
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się skopiować " + resourceName + ": " + e.getMessage());
            }
        }

        if (copiedAny) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "bettermodel reload");
                // BetterModel dopiero PO reloadzie zapisuje plugins/BetterModel/build.zip na
                // dysku - dajemy mu chwilę (5s) zanim spróbujemy go scalić z naszym packiem.
                Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                    if (PetResourcePackMerger.refresh(plugin)) {
                        Bukkit.getScheduler().runTask(plugin,
                                () -> Bukkit.getOnlinePlayers().forEach(ResourcePackPusher::push));
                    }
                }, 100L);
            });
        }
    }

    /** Nazwa modelu w BetterModel dla gatunku peta (np. "good" -> "cubee-good"). */
    public static String modelName(String species) {
        return "cubee-" + species;
    }
}
