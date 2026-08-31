package com.bountysmp.judgment.pvp;

import java.util.Map;
import java.util.UUID;

public record PvpState(boolean enabled, long lastToggleMillis, long combatEndMillis,
                       Map<UUID, Long> opponents) {
    public PvpState {
        opponents = Map.copyOf(opponents);
    }
}
