package com.dziubek.boxpvp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ShopConfigManager {

    private final Map<UUID, ShopConfigSession> sessions = new HashMap<>();

    public void startSession(UUID uuid, ShopConfigSession session) {
        sessions.put(uuid, session);
    }

    public ShopConfigSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public void clearSession(UUID uuid) {
        sessions.remove(uuid);
    }
}
