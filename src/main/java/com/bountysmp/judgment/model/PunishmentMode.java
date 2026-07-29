package com.bountysmp.judgment.model;

import java.util.Locale;

public enum PunishmentMode {
    RELOG,
    INSTANT;

    public static PunishmentMode parse(String raw) {
        if (raw == null) {
            return RELOG;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "instant" -> INSTANT;
            case "relog" -> RELOG;
            default -> RELOG;
        };
    }

    public String displayName() {
        return switch (this) {
            case RELOG -> "Relog";
            case INSTANT -> "Instant";
        };
    }
}
