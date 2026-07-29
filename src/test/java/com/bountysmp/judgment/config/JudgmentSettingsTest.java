package com.bountysmp.judgment.config;

import com.bountysmp.judgment.model.PunishmentMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgmentSettingsTest {
    @Test
    void loadsDefaultsForMissingValues() {
        JudgmentSettings settings = JudgmentSettings.fromConfig(new YamlConfiguration(), Logger.getLogger("test"));

        assertEquals(30_000L, settings.combatTagMillis());
        assertEquals(10_000L, settings.promptTimeoutMillis());
        assertEquals(PunishmentMode.RELOG, settings.punishmentMode());
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
