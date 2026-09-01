package com.bountysmp.judgment.pvp;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;

public record PvpSettings(boolean defaultEnabled, long toggleCooldownMillis, long postCombatDelayMillis,
                          boolean preventToggleInEnd, boolean preventToggleInNether) {
    /** Backwards-compatible constructor for modules that do not configure the End lock. */
    public PvpSettings(boolean defaultEnabled, long toggleCooldownMillis, long postCombatDelayMillis) {
        this(defaultEnabled, toggleCooldownMillis, postCombatDelayMillis, false, false);
    }

    public PvpSettings(boolean defaultEnabled, long toggleCooldownMillis, long postCombatDelayMillis,
                       boolean preventToggleInEnd) {
        this(defaultEnabled, toggleCooldownMillis, postCombatDelayMillis, preventToggleInEnd, false);
    }

    public static PvpSettings fromConfig(FileConfiguration config, Logger logger) {
        return new PvpSettings(config.getBoolean("pvp.default-enabled", false),
            duration(config, "pvp.toggle-cooldown-seconds", 86_400L, logger),
            duration(config, "pvp.post-combat-delay-seconds", 600L, logger),
            config.getBoolean("pvp.prevent-toggle-in-end", false),
            config.getBoolean("pvp.prevent-toggle-in-nether", false));
    }

    private static long duration(FileConfiguration config, String key, long fallback, Logger logger) {
        Object value = config.get(key);
        if (value == null) return fallback * 1_000L;
        if (value instanceof Number number) {
            double seconds = number.doubleValue();
            if (Double.isFinite(seconds) && seconds >= 0 && seconds < Long.MAX_VALUE / 1_000.0) {
                return Math.round(seconds * 1_000.0);
            }
        }
        logger.warning("Invalid " + key + "; using " + fallback + " seconds.");
        return fallback * 1_000L;
    }
}
