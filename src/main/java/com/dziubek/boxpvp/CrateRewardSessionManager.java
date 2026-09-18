package com.dziubek.boxpvp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrateRewardSessionManager {

    private final Map<UUID, CrateRewardSession> sessions = new HashMap<>();

    public void startSession(UUID uuid, CrateRewardSession session) {
        sessions.put(uuid, session);
    }

    public CrateRewardSession getSession(UUID uuid) {
        return sessions.get(uuid);
    }

    public void clearSession(UUID uuid) {
        sessions.remove(uuid);
    }
}
