package com.bountysmp.judgment.config;

import com.bountysmp.judgment.model.PunishmentMode;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

public record JudgmentSettings(long combatTagMillis, long promptTimeoutMillis, PunishmentMode punishmentMode) {
    private static final long DEFAULT_COMBAT_TAG_MILLIS = 30_000L;
    private static final long DEFAULT_PROMPT_TIMEOUT_MILLIS = 10_000L;

    public static JudgmentSettings fromConfig(FileConfiguration config, Logger logger) {
        long combatTagMillis = secondsToMillis(config.getDouble("combat-tag-seconds", 30.0), DEFAULT_COMBAT_TAG_MILLIS);
        long promptTimeoutMillis = secondsToMillis(config.getDouble("prompt-timeout-seconds", 10.0), DEFAULT_PROMPT_TIMEOUT_MILLIS);
        PunishmentMode mode = PunishmentMode.parse(config.getString("punishment-mode", "relog"));
        if (mode == PunishmentMode.INSTANT) {
            logger.warning("punishment-mode=instant is reserved for a future release; using relog behavior for now.");
        }
        return new JudgmentSettings(combatTagMillis, promptTimeoutMillis, mode);
    }

    public JudgmentSettings withCombatTagMillis(long millis) {
        return new JudgmentSettings(Math.max(0L, millis), promptTimeoutMillis, punishmentMode);
    }

    public JudgmentSettings withPromptTimeoutMillis(long millis) {
        return new JudgmentSettings(combatTagMillis, Math.max(0L, millis), punishmentMode);
    }

    public PunishmentMode effectivePunishmentMode() {
        return PunishmentMode.RELOG;
    }

    private static long secondsToMillis(double seconds, long fallback) {
        if (Double.isNaN(seconds) || Double.isInfinite(seconds) || seconds < 0.0) {
            return fallback;
        }
        return Math.round(seconds * 1_000.0);
    }
}
