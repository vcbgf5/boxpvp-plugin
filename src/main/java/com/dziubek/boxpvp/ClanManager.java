package com.dziubek.boxpvp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Klany - TRWAŁE (w przeciwieństwie do /party, które jest tylko w pamięci), z tagiem doklejanym
 * do czatu (ClanChatListener) i wspólnym bankiem. Tag jest kluczem w konfiguracji, przechowywany
 * małymi literami (case-insensitive), a wyświetlany dokładnie tak, jak podał założyciel.
 */
public class ClanManager {

    private static final long FLUSH_INTERVAL_TICKS = 20L * 30;

    private final BoxPvpPlugin plugin;
    private final File file;
    private final FileConfiguration data;
    private volatile boolean dirty = false;

    private final Map<UUID, String> memberOf = new HashMap<>();
    private final Map<UUID, String> pendingInvites = new HashMap<>();

    public ClanManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "clans.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Nie udało się utworzyć clans.yml: " + e.getMessage());
            }
        }
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
    }

    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    public boolean clanExists(String tag) {
        return data.contains("clans." + key(tag));
    }

    public String getClanTag(UUID uuid) {
        return memberOf.get(uuid);
    }

    public boolean inClan(UUID uuid) {
        return memberOf.containsKey(uuid);
    }

    public boolean sameClan(UUID a, UUID b) {
        String tagA = memberOf.get(a);
        return tagA != null && tagA.equals(memberOf.get(b));
    }

    public boolean isLeader(UUID uuid) {
        String tag = memberOf.get(uuid);
        return tag != null && uuid.equals(getLeader(tag));
    }

    public UUID getLeader(String tag) {
        String raw = data.getString("clans." + key(tag) + ".leader");
        return raw == null ? null : UUID.fromString(raw);
    }

    public String getDisplayTag(String tag) {
        return data.getString("clans." + key(tag) + ".display-tag", tag);
    }

    public Set<UUID> getMembers(String tag) {
        Set<UUID> members = new HashSet<>();
        for (String uuidStr : data.getStringList("clans." + key(tag) + ".members")) {
            try {
                members.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
                // klucz spoza formatu UUID - pomiń
            }
        }
        return members;
    }

    public double getBankBalance(String tag) {
        return data.getDouble("clans." + key(tag) + ".bank", 0);
    }

    public List<String> listClanTags() {
        ConfigurationSection section = data.getConfigurationSection("clans");
        if (section == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(section.getKeys(false));
    }

    /** Zakłada nowy klan - false, jeśli gracz już jest w klanie albo tag zajęty. */
    public boolean create(Player leader, String tag) {
        if (inClan(leader.getUniqueId()) || clanExists(tag)) {
            return false;
        }
        String base = "clans." + key(tag);
        data.set(base + ".display-tag", tag);
        data.set(base + ".leader", leader.getUniqueId().toString());
        data.set(base + ".members", List.of(leader.getUniqueId().toString()));
        data.set(base + ".bank", 0.0);
        memberOf.put(leader.getUniqueId(), key(tag));
        markDirty();
        return true;
    }

    public boolean disband(String tag) {
        String k = key(tag);
        if (!clanExists(k)) {
            return false;
        }
        for (UUID member : getMembers(k)) {
            memberOf.remove(member);
        }
        data.set("clans." + k, null);
        markDirty();
        return true;
    }

    public void invite(UUID leaderUuid, UUID targetUuid) {
        String tag = memberOf.get(leaderUuid);
        if (tag != null) {
            pendingInvites.put(targetUuid, tag);
        }
    }

    public String getInvite(UUID targetUuid) {
        return pendingInvites.get(targetUuid);
    }

    public void clearInvite(UUID targetUuid) {
        pendingInvites.remove(targetUuid);
    }

    /** Dołącza gracza do klanu, do którego był zaproszony - false, jeśli zaproszenie nieaktualne. */
    public boolean acceptInvite(Player player) {
        String tag = pendingInvites.remove(player.getUniqueId());
        if (tag == null || !clanExists(tag) || inClan(player.getUniqueId())) {
            return false;
        }
        List<String> members = new ArrayList<>(data.getStringList("clans." + tag + ".members"));
        members.add(player.getUniqueId().toString());
        data.set("clans." + tag + ".members", members);
        memberOf.put(player.getUniqueId(), tag);
        markDirty();
        return true;
    }

    /**
     * Wychodzi z klanu - jeśli był liderem i zostali inni, przywództwo automatycznie przechodzi
     * na kolejnego członka (jak PartyManager#removeMember). Zwraca false, jeśli nie był w klanie.
     */
    public boolean leaveClan(UUID uuid) {
        String tag = memberOf.remove(uuid);
        if (tag == null) {
            return false;
        }
        List<String> members = new ArrayList<>(data.getStringList("clans." + tag + ".members"));
        members.remove(uuid.toString());
        if (members.isEmpty()) {
            data.set("clans." + tag, null);
        } else {
            data.set("clans." + tag + ".members", members);
            if (uuid.equals(getLeader(tag))) {
                data.set("clans." + tag + ".leader", members.get(0));
            }
        }
        markDirty();
        return true;
    }

    public boolean kick(String tag, UUID target) {
        if (!getMembers(tag).contains(target)) {
            return false;
        }
        return leaveClan(target);
    }

    public void deposit(String tag, double amount) {
        double current = getBankBalance(tag);
        data.set("clans." + key(tag) + ".bank", current + amount);
        markDirty();
    }

    public boolean withdraw(String tag, double amount) {
        double current = getBankBalance(tag);
        if (amount > current) {
            return false;
        }
        data.set("clans." + key(tag) + ".bank", current - amount);
        markDirty();
        return true;
    }

    private static String key(String tag) {
        return tag.toLowerCase();
    }

    private void markDirty() {
        dirty = true;
    }

    private void load() {
        ConfigurationSection section = data.getConfigurationSection("clans");
        if (section == null) {
            return;
        }
        for (String tag : section.getKeys(false)) {
            for (String uuidStr : data.getStringList("clans." + tag + ".members")) {
                try {
                    memberOf.put(UUID.fromString(uuidStr), tag);
                } catch (IllegalArgumentException ignored) {
                    // klucz spoza formatu UUID - pomiń
                }
            }
        }
    }

    public void flush() {
        if (!dirty) {
            return;
        }
        dirty = false;
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Nie udało się zapisać clans.yml: " + e.getMessage());
        }
    }
}
