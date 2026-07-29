package com.bountysmp.judgment.service;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.command.JudgmentCommand;
import com.bountysmp.judgment.model.CombatLogCase;
import com.bountysmp.judgment.model.CombatTag;
import com.bountysmp.judgment.model.CombatTagDirection;
import com.bountysmp.judgment.model.PunishmentMode;
import com.bountysmp.judgment.storage.PendingKill;
import com.bountysmp.judgment.storage.PendingKillStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgmentServiceTest {
    @TempDir
    Path tempDir;

    private Plugin plugin;
    private AtomicLong now;
    private List<String> executedKills;

    @BeforeEach
    void setUpBukkit() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        now = new AtomicLong(1_000L);
        executedKills = new ArrayList<>();
    }

    @AfterEach
    void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void pvpDamageStartsTagAndQuitPromptsLastDamager() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock offender = MockBukkit.getMock().addPlayer("Logger");
        PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");

        service.recordPvpDamage(offender, killer);
        service.handleQuit(offender);

        assertEquals(1, service.openCases().size());
        CombatLogCase combatLogCase = service.openCases().getFirst();
        assertEquals(offender.getUniqueId(), combatLogCase.offenderId());
        assertEquals(killer.getUniqueId(), combatLogCase.killerId());
        assertTrue(drainMessages(killer).stream().anyMatch(message -> message.contains("This prompt expires in 10 seconds.")));
    }

    @Test
    void pvpDamageCreatesOutgoingAndIncomingTags() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock attacker = MockBukkit.getMock().addPlayer("Attacker");
        PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");

        service.recordPvpDamage(victim, attacker);

        CombatTag attackerTag = service.getCombatStack(attacker.getUniqueId()).getFirst();
        assertEquals(CombatTagDirection.OUTGOING, attackerTag.direction());
        assertEquals(victim.getUniqueId(), attackerTag.opponentId());
        assertEquals(victim.getUniqueId(), attackerTag.creditedId());

        CombatTag victimTag = service.getCombatStack(victim.getUniqueId()).getFirst();
        assertEquals(CombatTagDirection.INCOMING, victimTag.direction());
        assertEquals(attacker.getUniqueId(), victimTag.opponentId());
        assertEquals(attacker.getUniqueId(), victimTag.creditedId());
    }

    @Test
    void reciprocalHitsKeepBothDirectionsAndIncomingRanksFirst() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock a = MockBukkit.getMock().addPlayer("A");
        PlayerMock b = MockBukkit.getMock().addPlayer("B");

        now.set(1_000L);
        service.recordPvpDamage(b, a);
        now.set(2_000L);
        service.recordPvpDamage(a, b);

        List<CombatTag> aStack = service.getCombatStack(a.getUniqueId());
        assertEquals(2, aStack.size());
        assertEquals(CombatTagDirection.INCOMING, aStack.get(0).direction());
        assertEquals(b.getUniqueId(), aStack.get(0).creditedId());
        assertEquals(CombatTagDirection.OUTGOING, aStack.get(1).direction());

        List<CombatTag> bStack = service.getCombatStack(b.getUniqueId());
        assertEquals(2, bStack.size());
        assertEquals(CombatTagDirection.INCOMING, bStack.get(0).direction());
        assertEquals(a.getUniqueId(), bStack.get(0).creditedId());
        assertEquals(CombatTagDirection.OUTGOING, bStack.get(1).direction());
    }

    @Test
    void duplicateTagRefreshesTimerWithoutGrowingStack() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock attacker = MockBukkit.getMock().addPlayer("Attacker");
        PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");

        now.set(1_000L);
        service.recordPvpDamage(victim, attacker);
        now.set(2_000L);
        service.recordPvpDamage(victim, attacker);

        List<CombatTag> attackerStack = service.getCombatStack(attacker.getUniqueId());
        assertEquals(1, attackerStack.size());
        assertEquals(32_000L, attackerStack.getFirst().expiresAtMillis());
        assertEquals(2_000L, attackerStack.getFirst().updatedAtMillis());
    }

    @Test
    void incomingTagOutranksOutgoingTagForLogoutCredit() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock a = MockBukkit.getMock().addPlayer("A");
        PlayerMock b = MockBukkit.getMock().addPlayer("B");
        PlayerMock c = MockBukkit.getMock().addPlayer("C");

        service.recordPvpDamage(b, a);
        service.recordPvpDamage(a, c);
        service.handleQuit(a);

        assertEquals(1, service.openCases().size());
        assertEquals(c.getUniqueId(), service.openCases().getFirst().killerId());
    }

    @Test
    void independentExpiryRemovesOnlyExpiredTag() {
        PendingKillStore store = new PendingKillStore(tempDir.resolve("pending-kills.yml"));
        store.load();
        JudgmentSettings settings = new JudgmentSettings(1_000L, 10_000L, PunishmentMode.RELOG);
        JudgmentService service = new JudgmentService(plugin, settings, store, now::get,
            (offender, killer) -> true,
            offender -> true);
        PlayerMock a = MockBukkit.getMock().addPlayer("A");
        PlayerMock b = MockBukkit.getMock().addPlayer("B");
        PlayerMock c = MockBukkit.getMock().addPlayer("C");

        now.set(1_000L);
        service.recordPvpDamage(b, a);
        now.set(1_500L);
        service.recordPvpDamage(a, c);
        now.set(2_000L);

        List<CombatTag> aStack = service.getCombatStack(a.getUniqueId());
        assertEquals(1, aStack.size());
        assertEquals(CombatTagDirection.INCOMING, aStack.getFirst().direction());
        assertEquals(c.getUniqueId(), aStack.getFirst().creditedId());
    }

    @Test
    void logoutUsesTopTagOnlyAndSkipsPromptWhenTopCreditedPlayerIsOffline() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock a = MockBukkit.getMock().addPlayer("A");
        PlayerMock b = MockBukkit.getMock().addPlayer("B");
        PlayerMock c = MockBukkit.getMock().addPlayer("C");

        service.recordPvpDamage(b, a);
        service.recordPvpDamage(a, c);
        c.disconnect();
        service.handleQuit(a);

        assertTrue(service.openCases().isEmpty());
    }

    @Test
    void pvpDamageTagsAttackerTooSoAttackerLoggingPromptsVictim() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock attacker = MockBukkit.getMock().addPlayer("Attacker");
        PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");

        service.recordPvpDamage(victim, attacker);
        service.handleQuit(attacker);

        assertEquals(1, service.openCases().size());
        CombatLogCase combatLogCase = service.openCases().getFirst();
        assertEquals(attacker.getUniqueId(), combatLogCase.offenderId());
        assertEquals(victim.getUniqueId(), combatLogCase.killerId());
    }

    @Test
    void quitAfterTagExpiresDoesNothing() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock offender = MockBukkit.getMock().addPlayer("Logger");
        PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");

        service.recordPvpDamage(offender, killer);
        now.set(32_000L);
        service.handleQuit(offender);

        assertTrue(service.openCases().isEmpty());
    }

    @Test
    void deathClearsCombatTagsForBothPlayers() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");
        PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");

        service.recordPvpDamage(victim, killer);
        service.handleDeath(victim);
        service.handleQuit(victim);
        service.handleQuit(killer);

        assertTrue(service.getCombatTag(victim.getUniqueId()).isEmpty());
        assertTrue(service.getCombatTag(killer.getUniqueId()).isEmpty());
        assertTrue(service.openCases().isEmpty());
        assertTrue(drainMessages(killer).stream().anyMatch(message -> message.contains("You are no longer in combat with Victim.")));
    }

    @Test
    void combatExitReschedulesWhenExpiryTaskRunsBeforeWallClockExpiry() {
        PendingKillStore store = new PendingKillStore(tempDir.resolve("pending-kills.yml"));
        store.load();
        JudgmentSettings settings = new JudgmentSettings(1_000L, 10_000L, PunishmentMode.RELOG);
        JudgmentService service = new JudgmentService(plugin, settings, store, now::get,
            (offender, killer) -> true,
            offender -> true);
        PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");
        PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");

        now.set(1_000L);
        service.recordPvpDamage(victim, killer);

        now.set(1_999L);
        MockBukkit.getMock().getScheduler().performTicks(20L);
        assertTrue(service.getCombatTag(victim.getUniqueId()).isPresent());
        assertTrue(service.getCombatTag(killer.getUniqueId()).isPresent());

        now.set(2_000L);
        MockBukkit.getMock().getScheduler().performOneTick();
        assertTrue(service.getCombatTag(victim.getUniqueId()).isEmpty());
        assertTrue(service.getCombatTag(killer.getUniqueId()).isEmpty());
    }

    @Test
    void debugStackCommandPrintsOrderedStack() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        JudgmentCommand command = new JudgmentCommand(service, null);
        PlayerMock admin = MockBukkit.getMock().addPlayer("Admin");
        PlayerMock target = MockBukkit.getMock().addPlayer("Target");
        PlayerMock attacker = MockBukkit.getMock().addPlayer("Attacker");
        PlayerMock victim = MockBukkit.getMock().addPlayer("Victim");
        admin.addAttachment(plugin, "judgment.admin", true);

        service.recordPvpDamage(victim, target);
        service.recordPvpDamage(target, attacker);

        assertTrue(command.onCommand(admin, null, "judgment", new String[] {"debug", "stack", "Target"}));
        List<String> messages = drainMessages(admin);

        assertTrue(messages.stream().anyMatch(message -> message.contains("Combat stack for Target:")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("#1 INCOMING opponent=Attacker credit=Attacker")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("#2 OUTGOING opponent=Victim credit=Victim")));
    }

    @Test
    void onlyCreditedOpponentCanAnswerPrompt() {
        JudgmentService service = service(new PendingKillStore(tempDir.resolve("pending-kills.yml")));
        PlayerMock offender = MockBukkit.getMock().addPlayer("Logger");
        PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");
        PlayerMock other = MockBukkit.getMock().addPlayer("Other");

        service.recordPvpDamage(offender, killer);
        service.handleQuit(offender);
        String caseId = service.openCases().getFirst().caseId();

        assertEquals(JudgmentService.ChoiceResult.NOT_YOURS, service.handleChoice(other, caseId, true));
        assertEquals(1, service.openCases().size());
        assertTrue(executedKills.isEmpty());
    }

    @Test
    void noChoiceClearsPromptWithoutPendingKill() {
        PendingKillStore store = new PendingKillStore(tempDir.resolve("pending-kills.yml"));
        JudgmentService service = service(store);
        PlayerMock offender = MockBukkit.getMock().addPlayer("Logger");
        PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");

        service.recordPvpDamage(offender, killer);
        service.handleQuit(offender);
        String caseId = service.openCases().getFirst().caseId();

        assertEquals(JudgmentService.ChoiceResult.FORGIVEN, service.handleChoice(killer, caseId, false));
        assertTrue(service.openCases().isEmpty());
        assertTrue(store.values().isEmpty());
    }

    @Test
    void expiredPromptCannotApproveKill() {
        PendingKillStore store = new PendingKillStore(tempDir.resolve("pending-kills.yml"));
        JudgmentService service = service(store);
        PlayerMock offender = MockBukkit.getMock().addPlayer("Logger");
        PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");

        service.recordPvpDamage(offender, killer);
        service.handleQuit(offender);
        String caseId = service.openCases().getFirst().caseId();
        now.set(12_000L);

        assertEquals(JudgmentService.ChoiceResult.EXPIRED, service.handleChoice(killer, caseId, true));
        assertTrue(store.values().isEmpty());
        assertTrue(executedKills.isEmpty());
    }

    @Test
    void yesChoiceExecutesImmediatelyIfBothPlayersAreOnline() {
        PendingKillStore store = new PendingKillStore(tempDir.resolve("pending-kills.yml"));
        JudgmentService service = service(store);
        PlayerMock offender = MockBukkit.getMock().addPlayer("Logger");
        PlayerMock killer = MockBukkit.getMock().addPlayer("Killer");

        service.recordPvpDamage(offender, killer);
        service.handleQuit(offender);
        String caseId = service.openCases().getFirst().caseId();

        assertEquals(JudgmentService.ChoiceResult.APPROVED, service.handleChoice(killer, caseId, true));
        assertEquals(List.of("Logger<-Killer"), executedKills);
        assertTrue(store.values().isEmpty());
        assertTrue(drainMessages(killer).stream().anyMatch(message -> message.contains("Logger was killed since they are currently online.")));
    }

    @Test
    void approvedKillRunsWithoutCreditWhenCreditedKillerIsOffline() {
        PendingKillStore store = new PendingKillStore(tempDir.resolve("pending-kills.yml"));
        JudgmentService service = service(store);
        UUID offenderId = UUID.randomUUID();
        UUID killerId = UUID.randomUUID();
        PlayerMock offender = new PlayerMock(MockBukkit.getMock(), "Logger", offenderId);
        MockBukkit.getMock().addPlayer(offender);
        store.put(new PendingKill(offenderId, "Logger", killerId, "Killer", 1_000L));

        assertTrue(service.attemptPendingKill(offenderId));
        assertTrue(store.values().isEmpty());
        assertEquals(List.of("Logger<-none"), executedKills);
    }

    @Test
    void pendingKillRemainsStoredWhenOffenderIsOffline() {
        PendingKillStore store = new PendingKillStore(tempDir.resolve("pending-kills.yml"));
        JudgmentService service = service(store);
        UUID offenderId = UUID.randomUUID();
        UUID killerId = UUID.randomUUID();
        store.put(new PendingKill(offenderId, "Logger", killerId, "Killer", 1_000L));

        assertFalse(service.attemptPendingKillsForParticipant(offenderId));
        assertTrue(store.get(offenderId).isPresent());
        assertTrue(executedKills.isEmpty());
    }

    @Test
    void killerJoiningLaterIsNotRequiredAfterOffenderWasKilledWithoutCredit() {
        PendingKillStore store = new PendingKillStore(tempDir.resolve("pending-kills.yml"));
        JudgmentService service = service(store);
        UUID offenderId = UUID.randomUUID();
        UUID killerId = UUID.randomUUID();
        PlayerMock offender = new PlayerMock(MockBukkit.getMock(), "Logger", offenderId);
        MockBukkit.getMock().addPlayer(offender);
        store.put(new PendingKill(offenderId, "Logger", killerId, "Killer", 1_000L));

        assertTrue(service.attemptPendingKillsForParticipant(offenderId));
        assertEquals(List.of("Logger<-none"), executedKills);
        assertTrue(store.values().isEmpty());

        PlayerMock killer = new PlayerMock(MockBukkit.getMock(), "Killer", killerId);
        MockBukkit.getMock().addPlayer(killer);

        assertFalse(service.attemptPendingKillsForParticipant(killerId));
        assertEquals(List.of("Logger<-none"), executedKills);
        assertTrue(store.values().isEmpty());
    }

    private JudgmentService service(PendingKillStore store) {
        store.load();
        JudgmentSettings settings = new JudgmentSettings(30_000L, 10_000L, PunishmentMode.RELOG);
        return new JudgmentService(plugin, settings, store, now::get,
            (offender, killer) -> {
                executedKills.add(offender.getName() + "<-" + killer.getName());
                return true;
            },
            offender -> {
                executedKills.add(offender.getName() + "<-none");
                return true;
            });
    }

    private List<String> drainMessages(PlayerMock player) {
        PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
        List<String> messages = new ArrayList<>();
        while (true) {
            try {
                Component message = player.nextComponentMessage();
                if (message == null) {
                    return messages;
                }
                messages.add(serializer.serialize(message));
            } catch (AssertionError exception) {
                return messages;
            }
        }
    }
}
