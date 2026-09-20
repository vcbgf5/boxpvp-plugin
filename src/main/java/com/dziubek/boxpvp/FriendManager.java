package com.dziubek.boxpvp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lista znajomych - osobista i jednostronna (nie wymaga potwierdzenia drugiej strony, jak
 * "obserwowani"), z powiadomieniem na czacie gdy ktoś z listy dołącza do gry (FriendJoinListener).
 */
public class FriendManager {

    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private volatile boolean dirty = false;

    public FriendManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "friends.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć friends.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    public Set<UUID> getFriends(UUID uuid) {
        Set<UUID> friends = new HashSet<>();
        for (String uuidStr : data.getStringList("players." + uuid + ".friends")) {
            try {
                friends.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
                // klucz spoza formatu UUID - pomiń
            }
        }
        return friends;
    }

    public boolean addFriend(UUID uuid, UUID friendUuid) {
        if (uuid.equals(friendUuid)) {
            return false;
        }
        Set<UUID> friends = getFriends(uuid);
        if (!friends.add(friendUuid)) {
            return false;
        }
        save(uuid, friends);
        return true;
    }

    public boolean removeFriend(UUID uuid, UUID friendUuid) {
        Set<UUID> friends = getFriends(uuid);
        if (!friends.remove(friendUuid)) {
            return false;
        }
        save(uuid, friends);
        return true;
    }

    /** Kto ma tego gracza na liście znajomych - do powiadomienia przy dołączeniu (FriendJoinListener). */
    public List<UUID> whoHasFriend(UUID friendUuid) {
        List<UUID> result = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("players");
        if (section == null) {
            return result;
        }
        for (String uuidStr : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (getFriends(uuid).contains(friendUuid)) {
                result.add(uuid);
            }
        }
        return result;
    }

    private void save(UUID uuid, Set<UUID> friends) {
        List<String> list = new ArrayList<>();
        for (UUID friend : friends) {
            list.add(friend.toString());
        }
        data.set("players." + uuid + ".friends", list);
        dirty = true;
    }

    public void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać friends.yml: " + e.getMessage());
        }
    }
}
