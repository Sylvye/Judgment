package com.bountysmp.judgment.combatitem;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CombatItemSettingsTest {
    @Test void defaultsAllActionsToUnrestricted() {
        CombatItemSettings settings = CombatItemSettings.fromConfig(new YamlConfiguration(), Logger.getLogger("test"));
        for (CombatItemAction action : CombatItemAction.values()) assertEquals(0.0, settings.seconds(action));
        for (CombatItemAction action : CombatItemAction.values())
            if (action.explosive()) assertEquals(1.0, settings.damageModifier(action));
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

    @Test void scopesDefaultToPvpAndLoadGlobalValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("combat-item-scopes.fireworks", "global");
        CombatItemSettings settings = CombatItemSettings.fromConfig(config, Logger.getLogger("test"));
        assertEquals(CombatItemScope.GLOBAL, settings.scope(CombatItemAction.FIREWORKS));
        assertEquals(CombatItemScope.PVP_ONLY, settings.scope(CombatItemAction.ELYTRA));
    }

    @Test void invalidScopesFallBackToPvp() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("combat-item-scopes.elytra", "somewhere");
        assertEquals(CombatItemScope.PVP_ONLY,
            CombatItemSettings.fromConfig(config, Logger.getLogger("test")).scope(CombatItemAction.ELYTRA));
    }

    @Test void loadsAndUpdatesFiniteNonnegativeExplosiveDamageModifiers() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("combat-item-damage-modifiers.tnt", 0.5);
        config.set("combat-item-damage-modifiers.beds", 0);
        CombatItemSettings settings = CombatItemSettings.fromConfig(config, Logger.getLogger("test"));
        assertEquals(0.5, settings.damageModifier(CombatItemAction.TNT));
        assertEquals(0.0, settings.damageModifier(CombatItemAction.BEDS));
        settings = settings.withDamageModifier(CombatItemAction.END_CRYSTALS, 2.25);
        assertEquals(2.25, settings.damageModifier(CombatItemAction.END_CRYSTALS));
        CombatItemSettings updated = settings;
        assertThrows(IllegalArgumentException.class,
            () -> updated.withDamageModifier(CombatItemAction.ELYTRA, 1));
    }

    @Test void invalidDamageModifiersFallBackToVanilla() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("combat-item-damage-modifiers.tnt", -1);
        config.set("combat-item-damage-modifiers.beds", Double.POSITIVE_INFINITY);
        CombatItemSettings settings = CombatItemSettings.fromConfig(config, Logger.getLogger("test"));
        assertEquals(1.0, settings.damageModifier(CombatItemAction.TNT));
        assertEquals(1.0, settings.damageModifier(CombatItemAction.BEDS));
    }
}
