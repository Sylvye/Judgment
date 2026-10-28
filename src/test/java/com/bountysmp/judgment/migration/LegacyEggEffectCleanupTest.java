package com.bountysmp.judgment.migration;

import com.bountysmp.judgment.JudgmentPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import static org.junit.jupiter.api.Assertions.*;

class LegacyEggEffectCleanupTest {
    ServerMock server;
    PlayerMock player;
    @BeforeEach void setup() { server = MockBukkit.mock(); player = server.addPlayer(); }
    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private void markLegacy() {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
        player.getPersistentDataContainer().set(new NamespacedKey("judgment", "dragon_egg_strength"), PersistentDataType.BYTE, (byte) 1);
    }

    @Test void upgradeAndJoinRemoveLegacyMarkedEffectsWithoutDragonEggInstalled() {
        markLegacy();
        MockBukkit.load(JudgmentPlugin.class);
        assertFalse(player.hasPotionEffect(PotionEffectType.STRENGTH));
        assertTrue(player.getPersistentDataContainer().getKeys().isEmpty());
        markLegacy();
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
        assertFalse(player.hasPotionEffect(PotionEffectType.STRENGTH));
        assertTrue(player.getPersistentDataContainer().getKeys().isEmpty());
    }

    @Test void cleanupPreservesUnmarkedEffectsAndNewPluginMarkers() {
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 400, 1));
        var newKey = new NamespacedKey("dragonegg", "dragon_egg_strength");
        player.getPersistentDataContainer().set(newKey, PersistentDataType.BYTE, (byte) 1);
        MockBukkit.load(JudgmentPlugin.class);
        assertTrue(player.hasPotionEffect(PotionEffectType.STRENGTH));
        assertTrue(player.getPersistentDataContainer().has(newKey));
    }
}
