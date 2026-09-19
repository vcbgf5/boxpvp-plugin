package com.dziubek.boxpvp;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import org.bukkit.entity.Player;

/**
 * Miękka integracja z LuckPerms - jeśli plugin nie jest zainstalowany, getPrefix() zawsze
 * zwraca null (scoreboard po prostu nie pokazuje wtedy linijki z rangą).
 */
public class LuckPermsHook {

    private final boolean available;
    private LuckPerms api;

    public LuckPermsHook(BoxPvpPlugin plugin) {
        this.available = plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null;
        if (available) {
            try {
                this.api = LuckPermsProvider.get();
                plugin.getLogger().info("Wykryto LuckPerms - ranga na scoreboardzie aktywna.");
            } catch (IllegalStateException e) {
                this.api = null;
            }
        }
    }

    public boolean isAvailable() {
        return api != null;
    }

    /** Kolorowy prefix gracza z LuckPerms (np. "§c[Owner]") albo null, jeśli niedostępny. */
    public String getPrefix(Player player) {
        if (!isAvailable()) {
            return null;
        }
        User user = api.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return null;
        }
        String prefix = user.getCachedData().getMetaData().getPrefix();
        return prefix != null ? org.bukkit.ChatColor.translateAlternateColorCodes('&', prefix) : null;
    }
}
