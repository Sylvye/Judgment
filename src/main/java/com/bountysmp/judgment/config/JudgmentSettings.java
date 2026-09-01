package com.bountysmp.judgment.config;

import com.bountysmp.judgment.model.PunishmentMode;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

public record JudgmentSettings(long combatTagMillis, long promptTimeoutMillis, PunishmentMode punishmentMode,
                               boolean invisibleKillerObfuscation, boolean itemCooldownBossBars,
                               boolean combatTimerBossBar) {
    private static final long DEFAULT_COMBAT_TAG_MILLIS = 30_000L;
    private static final long DEFAULT_PROMPT_TIMEOUT_MILLIS = 10_000L;

    public static JudgmentSettings fromConfig(FileConfiguration config, Logger logger) {
        long combatTagMillis = secondsToMillis(config.getDouble("combat-tag-seconds", 30.0), DEFAULT_COMBAT_TAG_MILLIS);
        long promptTimeoutMillis = secondsToMillis(config.getDouble("prompt-timeout-seconds", 10.0), DEFAULT_PROMPT_TIMEOUT_MILLIS);
        PunishmentMode mode = PunishmentMode.parse(config.getString("punishment-mode", "relog"));
        if (mode == PunishmentMode.INSTANT) {
            logger.warning("punishment-mode=instant is reserved for a future release; using relog behavior for now.");
        }
        boolean invisibleKillerObfuscation = config.getBoolean("invisible-killer-obfuscation", false);
        boolean itemCooldownBossBars = config.getBoolean("bossbars.item-cooldowns", false);
        boolean combatTimerBossBar = config.getBoolean("bossbars.combat-timer", false);
        return new JudgmentSettings(combatTagMillis, promptTimeoutMillis, mode, invisibleKillerObfuscation,
            itemCooldownBossBars, combatTimerBossBar);
    }

    public JudgmentSettings(long combatTagMillis, long promptTimeoutMillis, PunishmentMode punishmentMode) {
        this(combatTagMillis, promptTimeoutMillis, punishmentMode, false, false, false);
    }

    public JudgmentSettings withCombatTagMillis(long millis) {
        return new JudgmentSettings(Math.max(0L, millis), promptTimeoutMillis, punishmentMode,
            invisibleKillerObfuscation, itemCooldownBossBars, combatTimerBossBar);
    }

    public JudgmentSettings withPromptTimeoutMillis(long millis) {
        return new JudgmentSettings(combatTagMillis, Math.max(0L, millis), punishmentMode,
            invisibleKillerObfuscation, itemCooldownBossBars, combatTimerBossBar);
    }

    public JudgmentSettings withInvisibleKillerObfuscation(boolean enabled) {
        return new JudgmentSettings(combatTagMillis, promptTimeoutMillis, punishmentMode, enabled,
            itemCooldownBossBars, combatTimerBossBar);
    }

    public JudgmentSettings withItemCooldownBossBars(boolean enabled) {
        return new JudgmentSettings(combatTagMillis, promptTimeoutMillis, punishmentMode,
            invisibleKillerObfuscation, enabled, combatTimerBossBar);
    }

    public JudgmentSettings withCombatTimerBossBar(boolean enabled) {
        return new JudgmentSettings(combatTagMillis, promptTimeoutMillis, punishmentMode,
            invisibleKillerObfuscation, itemCooldownBossBars, enabled);
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
