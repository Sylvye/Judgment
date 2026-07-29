package com.bountysmp.judgment.model;

import java.util.UUID;

public record CombatTag(UUID offenderId, String offenderName, UUID killerId, String killerName, long expiresAtMillis) {
    public boolean isActive(long nowMillis) {
        return expiresAtMillis > nowMillis;
    }
}
