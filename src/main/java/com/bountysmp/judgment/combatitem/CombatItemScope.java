package com.bountysmp.judgment.combatitem;

import java.util.Locale;

public enum CombatItemScope {
    PVP_ONLY("pvp", "PVP ONLY"),
    GLOBAL("global", "GLOBAL");

    private final String configValue;
    private final String displayName;

    CombatItemScope(String configValue, String displayName) {
        this.configValue = configValue;
        this.displayName = displayName;
    }

    public String configValue() { return configValue; }
    public String displayName() { return displayName; }

    public CombatItemScope toggled() { return this == PVP_ONLY ? GLOBAL : PVP_ONLY; }

    public static CombatItemScope parse(String raw) {
        if (raw == null) return PVP_ONLY;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "pvp", "pvp_only", "pvp-only" -> PVP_ONLY;
            case "global" -> GLOBAL;
            default -> null;
        };
    }
}
