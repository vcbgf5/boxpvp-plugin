package com.dziubek.boxpvp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Statyczne hologramy "jak to działa" dla graczy - admin stawia je gdzie chce przez
 * /bpvp infohologram <temat>, treść jest z góry ustalona w kodzie (per temat). Tak jak
 * LeaderboardManager - jeśli DecentHolograms jest zainstalowany, korzysta z niego (trwałe same z
 * siebie), inaczej stawia własny TextDisplay odtwarzany przy starcie pluginu z zapisanych lokacji.
 */
public class InfoHologramManager {

    private static final String BOARD_TAG = "bpvp_info_hologram";
    private static final Map<String, List<String>> TOPICS = buildTopics();

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private final Map<Integer, TextDisplay> boards = new LinkedHashMap<>();
    private int nextId = 0;

    public InfoHologramManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "info-holograms.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć info-holograms.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        purgeOrphans();
        ConfigurationSection section = data.getConfigurationSection("holograms");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            int id;
            try {
                id = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            nextId = Math.max(nextId, id + 1);
            String topic = data.getString("holograms." + key + ".topic");
            Location location = loadLocation("holograms." + key);
            if (topic == null || location == null || !TOPICS.containsKey(topic)) {
                continue;
            }
            spawn(id, topic, location);
        }
    }

    public static List<String> topics() {
        return new ArrayList<>(TOPICS.keySet());
    }

    /** Stawia nowy hologram danego tematu w podanym miejscu - zwraca jego ID (do /bpvp infohologram remove) albo null, jeśli temat nie istnieje. */
    public Integer place(String topic, Location location) {
        String key = topic.toLowerCase();
        if (!TOPICS.containsKey(key)) {
            return null;
        }
        int id = nextId++;
        data.set("holograms." + id + ".topic", key);
        data.set("holograms." + id + ".world", location.getWorld().getName());
        data.set("holograms." + id + ".x", location.getX());
        data.set("holograms." + id + ".y", location.getY());
        data.set("holograms." + id + ".z", location.getZ());
        save();
        spawn(id, key, location);
        return id;
    }

    public boolean remove(int id) {
        if (!data.contains("holograms." + id)) {
            return false;
        }
        data.set("holograms." + id, null);
        save();
        if (plugin.getDecentHolograms().isAvailable()) {
            plugin.getDecentHolograms().removeHologram(hologramId(id));
        }
        TextDisplay board = boards.remove(id);
        if (board != null && board.isValid()) {
            board.remove();
        }
        return true;
    }

    private void spawn(int id, String topic, Location location) {
        List<String> lines = TOPICS.get(topic);
        if (plugin.getDecentHolograms().isAvailable()) {
            plugin.getDecentHolograms().createInfoHologram(hologramId(id), location, lines);
            return;
        }
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setPersistent(false);
            e.setInvulnerable(true);
            e.setText(String.join("\n", lines));
            e.addScoreboardTag(BOARD_TAG);
        });
        boards.put(id, display);
    }

    private String hologramId(int id) {
        return "bpvp_info_" + id;
    }

    private Location loadLocation(String base) {
        String worldName = data.getString(base + ".world");
        if (worldName == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, data.getDouble(base + ".x"), data.getDouble(base + ".y"), data.getDouble(base + ".z"));
    }

    private void purgeOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(TextDisplay.class)) {
                if (entity.getScoreboardTags().contains(BOARD_TAG)) {
                    entity.remove();
                }
            }
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać info-holograms.yml: " + e.getMessage());
        }
    }

    private static Map<String, List<String>> buildTopics() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("duel", List.of(
                "§5§l⚔ Pojedynki",
                "§7Wpisz §f/duel §7- dobierze Ci przeciwnika",
                "§7albo §f/duel invite <gracz> <stawka>",
                "§7Wygrana = zgarniasz stawkę + ELO",
                "§7Ekwipunek zawsze wraca po walce"
        ));
        map.put("generator", List.of(
                "§a§l⛏ Generatory bloków",
                "§7Kop blok generatora - odnawia się sam",
                "§7Auto-sprzedaż sprzedaje to co wykopiesz",
                "§7Zarabiaj pasywnie, farmiąc generatory!"
        ));
        map.put("crate", List.of(
                "§6§l★ Skrzynie",
                "§7Zdobądź klucz i kliknij fizyczną skrzynię",
                "§7Otwórz, żeby wylosować nagrodę",
                "§7MEGA-skrzynie dają lepsze nagrody!"
        ));
        map.put("kit", List.of(
                "§b§l★ Kity",
                "§7/kit <nazwa> - odbierz gotowy zestaw",
                "§7Część kitów ma cooldown między odbiorami"
        ));
        map.put("bank", List.of(
                "§e§l★ Bank (Kantor)",
                "§7Kupuj/sprzedawaj fizyczne banknoty u NPC",
                "§7Płać nimi innym graczom bez komend"
        ));
        map.put("rynek", List.of(
                "§d§l★ Rynek graczy",
                "§7/wymiana <cena> - wystaw trzymany item na 3 dni",
                "§7/rynek - kup coś wystawione przez innych"
        ));
        map.put("party", List.of(
                "§3§l★ Drużyny",
                "§7/party create - stwórz drużynę",
                "§7/party invite <gracz> - zaproś kogoś",
                "§7Członkowie nie zadają sobie obrażeń"
        ));
        map.put("missions", List.of(
                "§2§l★ Misje",
                "§7/missions - dzienne i tygodniowe zadania",
                "§7Ukończ zadanie, żeby odebrać nagrodę"
        ));
        map.put("clan", List.of(
                "§6§l★ Klany",
                "§7/clan create <tag> - załóż własny klan",
                "§7/clan invite <gracz> - zaproś do klanu",
                "§7/clan bank deposit|withdraw - wspólna kasa klanu"
        ));
        map.put("friends", List.of(
                "§b§l★ Znajomi",
                "§7/friend add <gracz> - dodaj do znajomych",
                "§7/friend list - zobacz kto jest online",
                "§7Dostaniesz powiadomienie gdy znajomy dołączy!"
        ));
        map.put("ranked", List.of(
                "§d§l★ Ranked",
                "§7Wpisz §f/duel §7i wybierz §bRanked",
                "§7Dobiera przeciwnika o zbliżonym ELO",
                "§7Co 14 dni koniec sezonu - TOP 3 dostaje nagrodę!"
        ));
        map.put("rotshop", List.of(
                "§b§l⟳ Rotujący sklep",
                "§7Wpisz §f/rotshop §7- zobacz aktualną rotację",
                "§73 losowe przedmioty z rabatem 20-50%",
                "§7Rotacja zmienia się co godzinę!"
        ));
        map.put("sklep", List.of(
                "§a§l$ Sklep",
                "§7Wpisz §f/sklep §7- kup broń, zbroję i bloki",
                "§7Zarobione pieniądze wydawaj tutaj"
        ));
        map.put("daily", List.of(
                "§e§l★ Nagroda dzienna",
                "§7Wpisz §f/daily §7raz na dobę",
                "§7Kolejne dni z rzędu = lepsze nagrody!"
        ));
        map.put("prestige", List.of(
                "§d§l✦ Prestiż",
                "§7Wpisz §f/prestige §7- sprawdź koszt i mnożnik",
                "§7Zapłać całą gotówkę, żeby zdobyć poziom",
                "§7Każdy poziom to wyższy mnożnik zarobków"
        ));
        map.put("booster", List.of(
                "§6§l⚡ Booster zarobków",
                "§7Kup booster w §f/sklep §7- mnoży Twoje zarobki",
                "§7Wpisz §f/booster §7- sprawdź ile czasu zostało"
        ));
        map.put("lms", List.of(
                "§4§l☠ Ostatni Ocalały",
                "§7Wpisz §f/lms join §7- zapisz się gdy zapisy otwarte",
                "§7/lms leave §7- wypisz się przed startem",
                "§7Ostatnia żywa osoba wygrywa!"
        ));
        map.put("trade", List.of(
                "§3§l⇄ Wymiana",
                "§7Wpisz §f/trade <gracz> §7- zaproś do wymiany",
                "§7/trade accept §7- zaakceptuj, §7/trade cancel §7- anuluj",
                "§7Bezpieczna wymiana itemów z innym graczem"
        ));
        return map;
    }
}
