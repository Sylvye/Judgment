package com.bountysmp.judgment.pvp;

import com.bountysmp.judgment.util.DurationParser;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class PvpServiceTest {
    @TempDir Path directory;
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    final AtomicLong now = new AtomicLong(1_000);
    final AtomicBoolean active = new AtomicBoolean();
    final AtomicBoolean inEnd = new AtomicBoolean();
    final AtomicBoolean inNether = new AtomicBoolean();
    final AtomicReference<PvpSettings> settings = new AtomicReference<>(new PvpSettings(false, 86_400_000, 600_000));
    PvpStore store;
    PvpService service;

    @BeforeEach void setup() {
        store = new PvpStore(directory.resolve("players.yml"));
        service = reopen();
    }

    PvpService reopen() {
        return new PvpService(store, settings::get, now::get, id -> active.get(), ignored -> false,
            id -> inEnd.get(), id -> inNether.get(), Logger.getAnonymousLogger());
    }

    @Test void firstChangeIsImmediateAndBothDirectionsShareCooldown() {
        assertFalse(service.isPvpEnabled(a));
        assertEquals(PvpService.Outcome.CHANGED, service.change(a, null).outcome());
        assertEquals(PvpService.Outcome.UNCHANGED, service.change(a, true).outcome());
        assertEquals(86_400_000, service.change(a, false).waitMillis());
        now.addAndGet(86_399_999);
        assertEquals(1, service.change(a, false).waitMillis());
        now.incrementAndGet();
        assertEquals(PvpService.Outcome.CHANGED, service.change(a, false).outcome());
        assertEquals(PvpService.Outcome.WAIT, service.change(a, true).outcome());
    }

    @Test void changingDefaultDoesNotOverwriteInitializedPlayers() {
        assertFalse(service.isPvpEnabled(a));
        settings.set(new PvpSettings(true, 0, 0));
        assertFalse(service.isPvpEnabled(a));
        assertTrue(service.isPvpEnabled(b));
        service = reopen();
        assertFalse(service.isPvpEnabled(a));
        assertTrue(service.isPvpEnabled(b));
    }

    @Test void permissionMatrixAndSelfDamage() {
        settings.set(new PvpSettings(false, 0, 0));
        assertFalse(service.canAttack(a, b));
        assertTrue(service.canAttack(a, a));
        service.change(a, true);
        assertFalse(service.canAttack(a, b));
        assertFalse(service.canAttack(b, a));
        service.change(b, true);
        assertTrue(service.canAttack(a, b));
        service.change(a, false);
        assertFalse(service.canAttack(a, b));
    }

    @Test void combatWaitStartsAtExpiryAndSurvivesRestart() {
        settings.set(new PvpSettings(true, 0, 600_000));
        service.recordCombat(a, b, 30_000);
        assertEquals(630_000, service.change(a, false).waitMillis());
        now.addAndGet(30_000);
        service = reopen();
        assertEquals(600_000, service.change(a, false).waitMillis());
        now.addAndGet(599_999);
        assertEquals(1, service.change(a, false).waitMillis());
        now.incrementAndGet();
        assertEquals(PvpService.Outcome.CHANGED, service.change(a, false).outcome());
    }

    @Test void renewedCombatAndMultipleOpponentsPostponeEligibility() {
        settings.set(new PvpSettings(true, 0, 600_000));
        service.recordCombat(a, b, 30_000);
        now.addAndGet(10_000);
        service.recordCombat(a, c, 30_000);
        now.addAndGet(1_000);
        service.handleDeath(b);
        assertEquals(629_000, service.change(a, false).waitMillis());
        now.addAndGet(1_000);
        service.handleDeath(c);
        assertEquals(600_000, service.change(a, false).waitMillis());
        assertEquals(600_000, service.change(c, false).waitMillis());
    }

    @Test void deathAfterRestartEndsPersistedCombatForBothParticipants() {
        settings.set(new PvpSettings(true, 0, 600_000));
        service.recordCombat(a, b, 30_000);
        now.addAndGet(5_000);
        service = reopen();
        service.handleDeath(a);
        assertEquals(600_000, service.change(a, false).waitMillis());
        assertEquals(600_000, service.change(b, false).waitMillis());
        now.addAndGet(600_000);
        assertEquals(PvpService.Outcome.CHANGED, reopen().change(b, false).outcome());
    }

    @Test void deathOutsideCombatDoesNotResetOldWait() {
        settings.set(new PvpSettings(true, 0, 600_000));
        service.recordCombat(a, b, 30_000);
        now.addAndGet(40_000);
        service.handleDeath(a);
        assertEquals(590_000, service.change(a, false).waitMillis());
    }

    @Test void changedDurationsApplyAndZeroStillCannotBypassActiveCombat() {
        service.change(a, true);
        service.recordCombat(a, b, 30_000);
        settings.set(new PvpSettings(false, 0, 0));
        assertEquals(30_000, service.change(a, false).waitMillis());
        now.addAndGet(30_000);
        active.set(true);
        assertEquals(PvpService.Outcome.WAIT, service.change(a, false).outcome());
        active.set(false);
        assertEquals(PvpService.Outcome.CHANGED, service.change(a, false).outcome());
    }

    @Test void longerToggleCooldownWinsOverCombatWait() {
        service.change(a, true);
        service.recordCombat(a, b, 30_000);
        assertEquals(86_400_000, service.change(a, false).waitMillis());
        now.addAndGet(1_000);
        service = reopen();
        assertEquals(86_399_000, service.change(a, false).waitMillis());
    }

    @Test void failedSaveDoesNotAcknowledgeOrOverwritePreference() throws Exception {
        settings.set(new PvpSettings(false, 0, 0));
        service.initialize(a);
        Path path = directory.resolve("players.yml");
        Path backup = directory.resolve("backup.yml");
        Files.move(path, backup);
        Files.createDirectory(path);
        Files.writeString(path.resolve("blocker"), "prevent replacement");
        assertEquals(PvpService.Outcome.STORAGE_ERROR, service.change(a, true).outcome());
        assertFalse(new PvpStore(backup).load().get(a).enabled());
        assertFalse(service.canAttack(a, b));
    }

    @Test void corruptedStorageFailsClosedWithoutResettingFile() throws Exception {
        Path path = directory.resolve("players.yml");
        String corrupt = "players: [invalid";
        Files.writeString(path, corrupt);
        assertThrows(java.io.IOException.class, store::load);
        service = reopen();
        assertFalse(service.canAttack(a, b));
        assertEquals(PvpService.Outcome.STORAGE_ERROR, service.change(a, true).outcome());
        assertEquals(corrupt, Files.readString(path));
    }

    @Test void invalidAndOverflowingDurationsAreRejected() {
        assertNull(DurationParser.parseMillis("9223372036854775807h"));
        assertNull(DurationParser.parseMillis("-1h"));
        assertEquals(86_400_000L, DurationParser.parseMillis("24h"));
        YamlConfiguration config = new YamlConfiguration();
        config.set("pvp.toggle-cooldown-seconds", Double.POSITIVE_INFINITY);
        config.set("pvp.post-combat-delay-seconds", -1);
        assertEquals(new PvpSettings(false, 86_400_000, 600_000), PvpSettings.fromConfig(config, Logger.getAnonymousLogger()));
    }

    @Test void configuredEndLockPreventsOnlyActualStatusChanges() {
        settings.set(new PvpSettings(false, 0, 0, true));
        inEnd.set(true);
        assertEquals(PvpService.Outcome.UNCHANGED, service.change(a, false).outcome());
        assertEquals(PvpService.Outcome.END_DIMENSION, service.change(a, true).outcome());
        inEnd.set(false);
        assertEquals(PvpService.Outcome.CHANGED, service.change(a, true).outcome());
    }

    @Test void configuredNetherLockPreventsOnlyActualStatusChanges() {
        settings.set(new PvpSettings(false, 0, 0, false, true));
        inNether.set(true);
        assertEquals(PvpService.Outcome.UNCHANGED, service.change(a, false).outcome());
        assertEquals(PvpService.Outcome.NETHER_DIMENSION, service.change(a, true).outcome());
        assertEquals(PvpService.Outcome.CHANGED, service.adminSet(a, true).outcome());
        assertTrue(service.isPvpEnabled(a));
    }
}
