package com.bountysmp.judgment.model;

import java.util.UUID;

public record CombatLogCase(
    String caseId,
    UUID offenderId,
    String offenderName,
    UUID killerId,
    String killerName,
    long expiresAtMillis
) {
    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}
