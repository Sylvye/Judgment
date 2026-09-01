package com.bountysmp.judgment.combatitem;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CombatItemSettingsTest {
    @Test void defaultsAllActionsToUnrestricted() {
        CombatItemSettings settings = CombatItemSettings.fromConfig(new YamlConfiguration(), Logger.getLogger("test"));
        for (CombatItemAction action : CombatItemAction.values()) assertEquals(0.0, settings.seconds(action));
    }

    @Test void loadsBansAndDecimalCooldowns() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("combat-item-cooldowns.elytra", -1);
        config.set("combat-item-cooldowns.ender-pearls", 2.75);
        CombatItemSettings settings = CombatItemSettings.fromConfig(config, Logger.getLogger("test"));
        assertEquals(-1.0, settings.seconds(CombatItemAction.ELYTRA));
        assertEquals(2.75, settings.seconds(CombatItemAction.ENDER_PEARLS));
    }

    @Test void invalidValuesFallBackToZero() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("combat-item-cooldowns.riptide", -2);
        config.set("combat-item-cooldowns.lunge", Double.POSITIVE_INFINITY);
        CombatItemSettings settings = CombatItemSettings.fromConfig(config, Logger.getLogger("test"));
        assertEquals(0.0, settings.seconds(CombatItemAction.RIPTIDE));
        assertEquals(0.0, settings.seconds(CombatItemAction.LUNGE));
    }
}
