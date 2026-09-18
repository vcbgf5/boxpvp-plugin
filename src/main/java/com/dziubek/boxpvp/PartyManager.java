package com.dziubek.boxpvp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drużyny - czysto w pamięci (nie przeżywają restartu serwera, tak jak TpaManager/CombatManager).
 * partyByMember mapuje KAŻDEGO członka na ten sam obiekt Party (O(1) "w jakiej jestem drużynie").
 */
public class PartyManager {

    private final BoxPvpPlugin plugin;

    private final Map<UUID, Party> partyByMember = new HashMap<>();
    // UUID zaproszonego -> UUID lidera, który zaprosił
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();
    private final Map<UUID, Long> inviteExpiry = new HashMap<>();

    public PartyManager(BoxPvpPlugin plugin) {
        this.plugin = plugin;
    }

    public long inviteTimeoutSeconds() {
        return plugin.getConfig().getLong("party.invite-timeout-seconds", 60);
    }

    public Party getParty(UUID uuid) {
        return partyByMember.get(uuid);
    }

    public boolean inParty(UUID uuid) {
        return partyByMember.containsKey(uuid);
    }

    public boolean sameParty(UUID a, UUID b) {
        Party party = partyByMember.get(a);
        return party != null && party.getMembers().contains(b);
    }

    public Party createParty(UUID leader) {
        Party party = new Party(leader);
        partyByMember.put(leader, party);
        return party;
    }

    public void invite(UUID leader, UUID target) {
        pendingInvites.put(target, leader);
        inviteExpiry.put(target, System.currentTimeMillis() + inviteTimeoutSeconds() * 1000L);
    }

    /**
     * Zwraca UUID lidera, który zaprosił danego gracza (nie wygasła prośba), albo null.
     */
    public UUID getInviter(UUID target) {
        Long exp = inviteExpiry.get(target);
        if (exp == null || System.currentTimeMillis() > exp) {
            clearInvite(target);
            return null;
        }
        return pendingInvites.get(target);
    }

    public void clearInvite(UUID target) {
        pendingInvites.remove(target);
        inviteExpiry.remove(target);
    }

    public void addMember(Party party, UUID uuid) {
        party.getMembers().add(uuid);
        partyByMember.put(uuid, party);
    }

    /**
     * Usuwa gracza z jego drużyny. Zwraca drużynę, która została (z ewentualnie nowym liderem),
     * albo null jeśli drużyna się rozwiązała (był ostatnim członkiem).
     */
    public Party removeMember(UUID uuid) {
        Party party = partyByMember.remove(uuid);
        if (party == null) {
            return null;
        }
        party.getMembers().remove(uuid);
        if (party.getMembers().isEmpty()) {
            return null;
        }
        if (party.isLeader(uuid)) {
            party.setLeader(party.getMembers().iterator().next());
        }
        return party;
    }

    /** Wywoływane przy wyjściu gracza z serwera - czyści zaproszenia i członkostwo naraz. */
    public Party disconnect(UUID uuid) {
        clearInvite(uuid);
        return removeMember(uuid);
    }
}
