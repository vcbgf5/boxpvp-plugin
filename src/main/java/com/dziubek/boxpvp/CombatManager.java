package com.dziubek.boxpvp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatManager {

    // UUID gracza -> timestamp (ms) kiedy jego combat tag wygasa
    private final Map<UUID, Long> tagged = new HashMap<>();

    public void tag(UUID uuid, long durationSeconds) {
        tagged.put(uuid, System.currentTimeMillis() + durationSeconds * 1000L);
    }

    public boolean isTagged(UUID uuid) {
        Long expires = tagged.get(uuid);
        if (expires == null) {
            return false;
        }
        if (System.currentTimeMillis() >= expires) {
            tagged.remove(uuid);
            return false;
        }
        return true;
    }

    public long secondsLeft(UUID uuid) {
        Long expires = tagged.get(uuid);
        if (expires == null) {
            return 0;
        }
        long left = (expires - System.currentTimeMillis()) / 1000L;
        return Math.max(left, 0);
    }

    public void clear(UUID uuid) {
        tagged.remove(uuid);
    }
}
