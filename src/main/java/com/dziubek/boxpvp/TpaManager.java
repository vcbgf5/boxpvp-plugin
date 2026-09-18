package com.dziubek.boxpvp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TpaManager {

    private final BoxPvpPlugin plugin;

    // UUID celu -> UUID gracza, który wysłał prośbę
    private final Map<UUID, UUID> pending = new HashMap<>();
    private final Map<UUID, Long> expiry = new HashMap<>();

    public TpaManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public long timeoutSeconds() {
        return plugin.getConfig().getLong("tpa-timeout-seconds", 60);
    }

    public void createRequest(UUID requester, UUID target) {
        pending.put(target, requester);
        expiry.put(target, System.currentTimeMillis() + timeoutSeconds() * 1000L);
    }

    /**
     * Zwraca UUID gracza który wysłał aktualną (nie wygasłą) prośbę do danego celu, albo null.
     */
    public UUID getRequester(UUID target) {
        Long exp = expiry.get(target);
        if (exp == null || System.currentTimeMillis() > exp) {
            pending.remove(target);
            expiry.remove(target);
            return null;
        }
        return pending.get(target);
    }

    public void clear(UUID target) {
        pending.remove(target);
        expiry.remove(target);
    }
}
