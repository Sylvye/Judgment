package com.bountysmp.judgment.config;

import com.bountysmp.judgment.model.PunishmentMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgmentSettingsTest {
    @Test
    void loadsDefaultsForMissingValues() {
        JudgmentSettings settings = JudgmentSettings.fromConfig(new YamlConfiguration(), Logger.getLogger("test"));

        assertEquals(30_000L, settings.combatTagMillis());
        assertEquals(10_000L, settings.promptTimeoutMillis());
        assertEquals(PunishmentMode.RELOG, settings.punishmentMode());
        assertFalse(settings.invisibleKillerObfuscation());
        assertFalse(settings.itemCooldownBossBars());
        assertFalse(settings.combatTimerBossBar());
    }

    @Test
    void invisibleKillerObfuscationCanBeEnabled() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("invisible-killer-obfuscation", true);

        assertTrue(JudgmentSettings.fromConfig(config, Logger.getLogger("test")).invisibleKillerObfuscation());
    }

    @Test
    void bossBarsCanBeEnabledIndependently() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("bossbars.item-cooldowns", true);
        JudgmentSettings settings = JudgmentSettings.fromConfig(config, Logger.getLogger("test"));
        assertTrue(settings.itemCooldownBossBars());
        assertFalse(settings.combatTimerBossBar());
    }

    @Test
    void instantModeIsParsedButEffectiveBehaviorRemainsRelog() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("punishment-mode", "instant");

        JudgmentSettings settings = JudgmentSettings.fromConfig(config, Logger.getLogger("test"));

        assertEquals(PunishmentMode.INSTANT, settings.punishmentMode());
        assertEquals(PunishmentMode.RELOG, settings.effectivePunishmentMode());
    }
}
