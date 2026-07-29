package com.bountysmp.judgment.model;

import java.util.UUID;

public record CombatTag(
    UUID ownerId,
    String ownerName,
    UUID opponentId,
    String opponentName,
    UUID creditedId,
    String creditedName,
    CombatTagDirection direction,
    long expiresAtMillis,
    long updatedAtMillis
) {
    public boolean isActive(long nowMillis) {
        return expiresAtMillis > nowMillis;
    }

    public boolean sameTag(UUID opponentId, CombatTagDirection direction) {
        return this.opponentId.equals(opponentId) && this.direction == direction;
    }

    public int priority() {
        return direction.priority();
    }

    public CombatTag refresh(String ownerName, String opponentName, String creditedName, long expiresAtMillis, long updatedAtMillis) {
        return new CombatTag(ownerId, ownerName, opponentId, opponentName, creditedId, creditedName, direction, expiresAtMillis, updatedAtMillis);
    }
}
