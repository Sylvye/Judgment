package com.bountysmp.judgment.combatitem;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.model.PunishmentMode;
import com.bountysmp.judgment.service.JudgmentService;
import com.bountysmp.judgment.storage.PendingKillStore;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CombatBossBarControllerTest {
    @TempDir Path directory;
    ServerMock server;
    PlayerMock attacker;
    PlayerMock victim;
    CombatBossBarController controller;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        Plugin plugin = MockBukkit.createMockPlugin();
        attacker = server.addPlayer("Attacker");
        victim = server.addPlayer("Victim");
        JudgmentSettings display = new JudgmentSettings(30_000, 10_000, PunishmentMode.RELOG,
            false, true, true);
        JudgmentService combat = new JudgmentService(plugin, display,
            new PendingKillStore(directory.resolve("pending.yml")), System::currentTimeMillis);
        CombatItemSettings items = new CombatItemSettings(Map.of(CombatItemAction.MACE_SMASH, 20.0));
        CombatItemCooldownManager cooldowns = new CombatItemCooldownManager(
            new CombatItemCooldownStore(directory.resolve("cooldowns.yml")), () -> items,
            id -> combat.getCombatTag(id).isPresent(), System::currentTimeMillis, plugin.getLogger());
        combat.recordPvpDamage(victim, attacker);
        cooldowns.recordFirstCombatUse(attacker.getUniqueId(), CombatItemAction.MACE_SMASH);
        controller = new CombatBossBarController(plugin, combat, cooldowns, () -> items, () -> display);
    }

    @AfterEach void cleanup() {
        if (controller != null) controller.close();
        MockBukkit.unmock();
    }

    @Test void combatAndItemBarsStack() {
        controller.refresh();
        assertEquals(2, count(attacker.activeBossBars()));
        assertEquals(1, count(victim.activeBossBars()));
    }

    private static int count(Iterable<?> values) {
        int count = 0;
        for (Object ignored : values) count++;
        return count;
    }
}
