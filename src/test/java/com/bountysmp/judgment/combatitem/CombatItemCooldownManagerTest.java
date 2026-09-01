package com.bountysmp.judgment.combatitem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class CombatItemCooldownManagerTest {
    @TempDir Path tempDir;
    private final UUID player = UUID.randomUUID();
    private final AtomicLong now = new AtomicLong(1_000L);
    private final AtomicReference<CombatItemSettings> settings = new AtomicReference<>(CombatItemSettings.defaults());

    @Test void unrestrictedAndOutOfCombatActionsDoNotCreateTimers() {
        CombatItemCooldownManager manager = manager(true);
        assertTrue(manager.attempt(player, CombatItemAction.ELYTRA).allowed());
        assertTrue(manager.snapshot().isEmpty());
        settings.set(settings.get().with(CombatItemAction.ELYTRA, 10));
        CombatItemCooldownManager outsideCombat = manager(false);
        assertTrue(outsideCombat.attempt(player, CombatItemAction.ELYTRA).allowed());
        assertTrue(outsideCombat.snapshot().isEmpty());
    }

    @Test void bansAndPositiveCooldownsAreIndependent() {
        settings.set(settings.get().with(CombatItemAction.ELYTRA, -1).with(CombatItemAction.ENDER_PEARLS, 2.5));
        CombatItemCooldownManager manager = manager(true);
        assertEquals(CombatItemCooldownManager.Outcome.BANNED, manager.attempt(player, CombatItemAction.ELYTRA).outcome());
        assertTrue(manager.attempt(player, CombatItemAction.ENDER_PEARLS).allowed());
        assertEquals(2_500L, manager.attempt(player, CombatItemAction.ENDER_PEARLS).remainingMillis());
        assertTrue(manager.attempt(UUID.randomUUID(), CombatItemAction.ENDER_PEARLS).allowed());
        now.addAndGet(2_500L);
        assertTrue(manager.attempt(player, CombatItemAction.ENDER_PEARLS).allowed());
    }

    @Test void timersSurviveReloadWhileCombatRemainsActive() {
        settings.set(settings.get().with(CombatItemAction.RIPTIDE, 10));
        assertTrue(manager(true).attempt(player, CombatItemAction.RIPTIDE).allowed());
        now.addAndGet(4_000L);
        CombatItemCooldownManager reloaded = manager(true);
        assertEquals(6_000L, reloaded.attempt(player, CombatItemAction.RIPTIDE).remainingMillis());
        now.addAndGet(6_000L);
        assertTrue(reloaded.attempt(player, CombatItemAction.RIPTIDE).allowed());
    }

    @Test void positiveSettingChangesRecalculateAndDisabledRulesClearTimers() {
        settings.set(settings.get().with(CombatItemAction.LUNGE, 10));
        CombatItemCooldownManager manager = manager(true);
        manager.attempt(player, CombatItemAction.LUNGE);
        now.addAndGet(2_000L);
        settings.set(settings.get().with(CombatItemAction.LUNGE, 3));
        assertEquals(1_000L, manager.attempt(player, CombatItemAction.LUNGE).remainingMillis());
        settings.set(settings.get().with(CombatItemAction.LUNGE, 0));
        manager.settingChanged(CombatItemAction.LUNGE, 0);
        assertTrue(manager.snapshot().isEmpty());
    }

    @Test void recordsTheActionThatCreatesCombat() {
        AtomicBoolean combat = new AtomicBoolean(false);
        settings.set(settings.get().with(CombatItemAction.MACE_SMASH, 20));
        CombatItemCooldownManager manager = new CombatItemCooldownManager(
            new CombatItemCooldownStore(tempDir.resolve("first-hit.yml")), settings::get,
            ignored -> combat.get(), now::get, Logger.getLogger("test"));
        assertTrue(manager.attempt(player, CombatItemAction.MACE_SMASH).allowed());
        combat.set(true);
        manager.recordFirstCombatUse(player, CombatItemAction.MACE_SMASH);
        assertEquals(20_000L, manager.attempt(player, CombatItemAction.MACE_SMASH).remainingMillis());
    }

    @Test void exitingCombatClearsEveryActionForThePlayer() {
        AtomicBoolean combat = new AtomicBoolean(true);
        settings.set(settings.get().with(CombatItemAction.MACE_SMASH, 20).with(CombatItemAction.LUNGE, 10));
        CombatItemCooldownManager manager = new CombatItemCooldownManager(
            new CombatItemCooldownStore(tempDir.resolve("exit.yml")), settings::get,
            ignored -> combat.get(), now::get, Logger.getLogger("test"));
        manager.attempt(player, CombatItemAction.MACE_SMASH);
        manager.attempt(player, CombatItemAction.LUNGE);
        assertEquals(2, manager.snapshot().get(player).size());
        combat.set(false);
        manager.clearPlayersOutsideCombatAndSave();
        assertTrue(manager.snapshot().isEmpty());
        combat.set(true);
        assertTrue(manager.attempt(player, CombatItemAction.MACE_SMASH).allowed());
    }

    private CombatItemCooldownManager manager(boolean inCombat) {
        return new CombatItemCooldownManager(new CombatItemCooldownStore(tempDir.resolve("cooldowns.yml")),
            settings::get, ignored -> inCombat, now::get, Logger.getLogger("test"));
    }
}
