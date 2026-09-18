package com.dziubek.boxpvp;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Drużyna - lider + zbiór członków (lider jest też członkiem). Czysto w pamięci. */
public class Party {

    private UUID leader;
    private final Set<UUID> members = new LinkedHashSet<>();

    public Party(UUID leader) {
        this.leader = leader;
        members.add(leader);
    }

    public UUID getLeader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public Set<UUID> getMembers() {
        return members;
    }

    public boolean isLeader(UUID uuid) {
        return leader.equals(uuid);
    }
}
