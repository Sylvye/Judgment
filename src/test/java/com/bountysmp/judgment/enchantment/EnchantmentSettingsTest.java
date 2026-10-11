package com.bountysmp.judgment.enchantment;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EnchantmentSettingsTest {
    @BeforeEach void setup() { MockBukkit.mock(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void defaultsAreEnabledAndUnrestricted() {
        var settings = EnchantmentSettings.fromConfig(new YamlConfiguration(), Logger.getAnonymousLogger());
        assertTrue(settings.enabled());
        assertFalse(settings.active());
        assertEquals(5, settings.allowedLevel(Enchantment.SHARPNESS, 5));
    }

    @Test void readsNamespacedRulesAndIgnoresMalformedValues() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
            enchantment-rules:
              enabled: true
              limits:
                'minecraft:sharpness': 3
                'minecraft:wind_burst': -1
                'minecraft:unbreaking': 2.5
                'minecraft:mending': 256
                'minecraft:protection': '3'
                'unknown:enchantment': 1
            """);
        var settings = EnchantmentSettings.fromConfig(config, Logger.getAnonymousLogger());
        assertEquals(2, settings.limits().size());
        assertEquals(3, settings.allowedLevel(Enchantment.SHARPNESS, 5));
        assertEquals(2, settings.allowedLevel(Enchantment.SHARPNESS, 2));
        assertEquals(0, settings.allowedLevel(Enchantment.WIND_BURST, 1));
        assertEquals(3, settings.allowedLevel(Enchantment.UNBREAKING, 3));
    }

    @Test void settingsAreImmutableAndAcceptEverySupportedRule() {
        var original = new EnchantmentSettings(true, Map.of());
        var updated = original.with(Enchantment.SHARPNESS.getKey(), 255);
        assertEquals(0, original.limit(Enchantment.SHARPNESS));
        assertEquals(255, updated.limit(Enchantment.SHARPNESS));
        assertThrows(UnsupportedOperationException.class, () -> updated.limits().clear());
        assertThrows(IllegalArgumentException.class, () -> original.with(Enchantment.SHARPNESS.getKey(), -2));
        assertThrows(IllegalArgumentException.class, () -> original.with(Enchantment.SHARPNESS.getKey(), 256));
        assertEquals(300, updated.withEnabled(false).allowedLevel(Enchantment.SHARPNESS, 300));
    }

    @Test void dottedCustomIdsSurviveYamlAndInMemoryMaps() throws Exception {
        var key = new org.bukkit.NamespacedKey("custom", "sharp.edge");
        EnchantmentFixtures.register(key);
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
            enchantment-rules:
              limits:
                'custom:sharp.edge': 2
                'unknown:future.enchantment': -1
            """);
        assertEquals(2, EnchantmentSettings.fromConfig(config, Logger.getAnonymousLogger()).limits().get(key));
        var limits = EnchantmentSettings.configuredLimits(config);
        assertEquals(-1, limits.get("unknown:future.enchantment"));
        config.set("enchantment-rules.limits", limits);
        assertEquals(2, EnchantmentSettings.fromConfig(config, Logger.getAnonymousLogger()).limits().get(key));
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(config.saveToString());
        assertEquals(limits, EnchantmentSettings.configuredLimits(reloaded));
    }
}
