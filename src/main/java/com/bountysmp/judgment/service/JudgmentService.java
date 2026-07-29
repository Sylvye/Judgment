package com.bountysmp.judgment.service;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.model.CombatLogCase;
import com.bountysmp.judgment.model.CombatTag;
import com.bountysmp.judgment.storage.PendingKill;
import com.bountysmp.judgment.storage.PendingKillStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;

public final class JudgmentService {
    @FunctionalInterface
    public interface PunishmentExecutor {
        boolean execute(Player offender, Player killer);
    }

    @FunctionalInterface
    public interface UncreditedPunishmentExecutor {
        boolean execute(Player offender);
    }

    private final Plugin plugin;
    private final PendingKillStore pendingKillStore;
    private final LongSupplier clock;
    private final PunishmentExecutor punishmentExecutor;
    private final UncreditedPunishmentExecutor uncreditedPunishmentExecutor;
    private final Map<UUID, CombatTag> combatTags = new ConcurrentHashMap<>();
    private final Map<String, CombatLogCase> openCases = new ConcurrentHashMap<>();
    private volatile JudgmentSettings settings;

    public JudgmentService(Plugin plugin, JudgmentSettings settings, PendingKillStore pendingKillStore, LongSupplier clock) {
        this(plugin, settings, pendingKillStore, clock, JudgmentService::executePlayerCausedKill, JudgmentService::executeUncreditedKill);
    }

    public JudgmentService(
        Plugin plugin,
        JudgmentSettings settings,
        PendingKillStore pendingKillStore,
        LongSupplier clock,
        PunishmentExecutor punishmentExecutor
    ) {
        this(plugin, settings, pendingKillStore, clock, punishmentExecutor, JudgmentService::executeUncreditedKill);
    }

    public JudgmentService(
        Plugin plugin,
        JudgmentSettings settings,
        PendingKillStore pendingKillStore,
        LongSupplier clock,
        PunishmentExecutor punishmentExecutor,
        UncreditedPunishmentExecutor uncreditedPunishmentExecutor
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.pendingKillStore = pendingKillStore;
        this.clock = clock;
        this.punishmentExecutor = punishmentExecutor;
        this.uncreditedPunishmentExecutor = uncreditedPunishmentExecutor;
    }

    public void updateSettings(JudgmentSettings settings) {
        this.settings = settings;
    }

    public JudgmentSettings settings() {
        return settings;
    }

    public void recordPvpDamage(Player victim, Player attacker) {
        if (victim.getUniqueId().equals(attacker.getUniqueId()) || settings.combatTagMillis() <= 0L) {
            return;
        }

        long expiresAt = clock.getAsLong() + settings.combatTagMillis();
        tagParticipant(victim, attacker, expiresAt);
        tagParticipant(attacker, victim, expiresAt);
    }

    public Optional<CombatTag> getCombatTag(UUID playerId) {
        return Optional.ofNullable(combatTags.get(playerId));
    }

    public Optional<CombatLogCase> getOpenCase(String caseId) {
        return Optional.ofNullable(openCases.get(caseId));
    }

    public java.util.List<CombatLogCase> openCases() {
        return java.util.List.copyOf(openCases.values());
    }

    public void handleQuit(Player player) {
        CombatTag tag = combatTags.remove(player.getUniqueId());
        long now = clock.getAsLong();
        if (tag == null || !tag.isActive(now)) {
            return;
        }

        Player killer = Bukkit.getPlayer(tag.killerId());
        if (killer == null) {
            return;
        }

        String caseId = UUID.randomUUID().toString();
        CombatLogCase combatLogCase = new CombatLogCase(
            caseId,
            tag.offenderId(),
            tag.offenderName(),
            tag.killerId(),
            tag.killerName(),
            now + settings.promptTimeoutMillis()
        );
        openCases.put(caseId, combatLogCase);
        sendPrompt(killer, combatLogCase);

        long ticks = Math.max(1L, (settings.promptTimeoutMillis() + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> expireCase(caseId), ticks);
    }

    public void handleDeath(Player player) {
        CombatTag tag = combatTags.remove(player.getUniqueId());
        if (tag == null) {
            clearTagsTargeting(player.getUniqueId(), player.getName());
            return;
        }

        CombatTag opponentTag = combatTags.get(tag.killerId());
        if (opponentTag != null && opponentTag.killerId().equals(player.getUniqueId())) {
            combatTags.remove(tag.killerId());
            notifyNoLongerInCombat(tag.killerId(), player.getName());
        }
    }

    public ChoiceResult handleChoice(Player responder, String caseId, boolean kill) {
        CombatLogCase combatLogCase = openCases.get(caseId);
        long now = clock.getAsLong();
        if (combatLogCase == null || combatLogCase.isExpired(now)) {
            openCases.remove(caseId);
            responder.sendMessage(Component.text("That combat log prompt has expired.", NamedTextColor.YELLOW));
            return ChoiceResult.EXPIRED;
        }
        if (!combatLogCase.killerId().equals(responder.getUniqueId())) {
            responder.sendMessage(Component.text("That combat log prompt is not yours to answer.", NamedTextColor.RED));
            return ChoiceResult.NOT_YOURS;
        }

        openCases.remove(caseId);
        if (!kill) {
            responder.sendMessage(Component.text("You spared " + combatLogCase.offenderName() + ".", NamedTextColor.YELLOW));
            return ChoiceResult.FORGIVEN;
        }

        PendingKill pendingKill = new PendingKill(
            combatLogCase.offenderId(),
            combatLogCase.offenderName(),
            combatLogCase.killerId(),
            combatLogCase.killerName(),
            now
        );
        pendingKillStore.put(pendingKill);
        savePendingKills();
        boolean killedNow = attemptPendingKill(combatLogCase.offenderId());
        if (killedNow) {
            responder.sendMessage(Component.text(combatLogCase.offenderName() + " was killed since they are currently online.", NamedTextColor.GREEN));
        } else {
            responder.sendMessage(Component.text(combatLogCase.offenderName() + " will be killed on relog.", NamedTextColor.GREEN));
        }
        return ChoiceResult.APPROVED;
    }

    public void handleJoin(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> attemptPendingKillsForParticipant(player.getUniqueId()), 1L);
    }

    public boolean attemptPendingKill(UUID offenderId) {
        Optional<PendingKill> pending = pendingKillStore.get(offenderId);
        if (pending.isEmpty()) {
            return false;
        }

        Player offender = Bukkit.getPlayer(pending.get().offenderId());
        if (offender == null) {
            return false;
        }

        Player killer = Bukkit.getPlayer(pending.get().killerId());
        boolean killed = killer != null
            ? punishmentExecutor.execute(offender, killer)
            : uncreditedPunishmentExecutor.execute(offender);
        if (!killed) {
            return false;
        }

        pendingKillStore.remove(offenderId);
        savePendingKills();
        if (killer == null) {
            plugin.getLogger().info("Killed " + pending.get().offenderName()
                + " without combat log credit because " + pending.get().killerName() + " was offline.");
        }
        return true;
    }

    public boolean attemptPendingKillsForParticipant(UUID participantId) {
        boolean attempted = attemptPendingKill(participantId);
        for (PendingKill pendingKill : pendingKillStore.values()) {
            if (pendingKill.killerId().equals(participantId)) {
                attempted |= attemptPendingKill(pendingKill.offenderId());
            }
        }
        return attempted;
    }

    public void expireExpiredCases() {
        long now = clock.getAsLong();
        for (CombatLogCase combatLogCase : openCases.values()) {
            if (combatLogCase.isExpired(now)) {
                expireCase(combatLogCase.caseId());
            }
        }
    }

    private void expireCase(String caseId) {
        CombatLogCase combatLogCase = openCases.get(caseId);
        if (combatLogCase != null && combatLogCase.isExpired(clock.getAsLong())) {
            openCases.remove(caseId);
            Player killer = Bukkit.getPlayer(combatLogCase.killerId());
            if (killer != null) {
                killer.sendMessage(Component.text("Your prompt to kill " + combatLogCase.offenderName() + " has expired.", NamedTextColor.YELLOW));
            }
        }
    }

    private void tagParticipant(Player player, Player opponent, long expiresAt) {
        long now = clock.getAsLong();
        CombatTag previous = combatTags.get(player.getUniqueId());
        boolean enteringCombat = previous == null || !previous.isActive(now);

        combatTags.put(player.getUniqueId(), new CombatTag(
            player.getUniqueId(),
            player.getName(),
            opponent.getUniqueId(),
            opponent.getName(),
            expiresAt
        ));

        if (enteringCombat) {
            player.sendActionBar(Component.text(
                "You are now in combat for " + formatSeconds(settings.combatTagMillis()) + " seconds!",
                NamedTextColor.RED
            ));
        }

        long ticks = Math.max(1L, ((expiresAt - now) + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> expireCombatTag(player.getUniqueId(), expiresAt), ticks);
    }

    private void expireCombatTag(UUID playerId, long expiresAt) {
        CombatTag tag = combatTags.get(playerId);
        if (tag == null || tag.expiresAtMillis() != expiresAt) {
            return;
        }

        long now = clock.getAsLong();
        if (tag.isActive(now)) {
            long remainingTicks = Math.max(1L, ((expiresAt - now) + 49L) / 50L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> expireCombatTag(playerId, expiresAt), remainingTicks);
            return;
        }

        combatTags.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendActionBar(Component.text("You have left combat, you may now log out.", NamedTextColor.GREEN));
        }
    }

    private void clearTagsTargeting(UUID playerId, String playerName) {
        combatTags.entrySet().removeIf(entry -> {
            if (!entry.getValue().killerId().equals(playerId)) {
                return false;
            }
            notifyNoLongerInCombat(entry.getKey(), playerName);
            return true;
        });
    }

    private void notifyNoLongerInCombat(UUID playerId, String opponentName) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Component.text("You are no longer in combat with " + opponentName + ".", NamedTextColor.GREEN));
        }
    }

    private void sendPrompt(Player killer, CombatLogCase combatLogCase) {
        Component yes = Component.text("[YES]", NamedTextColor.GREEN)
            .clickEvent(ClickEvent.runCommand("/judgment choice " + combatLogCase.caseId() + " yes"))
            .hoverEvent(HoverEvent.showText(Component.text("Kill on relog", NamedTextColor.GREEN)));
        Component no = Component.text("[NO]", NamedTextColor.RED)
            .clickEvent(ClickEvent.runCommand("/judgment choice " + combatLogCase.caseId() + " no"))
            .hoverEvent(HoverEvent.showText(Component.text("Forgive", NamedTextColor.RED)));

        killer.sendMessage(Component.text(combatLogCase.offenderName() + " combat logged. Would you like to kill them? ", NamedTextColor.YELLOW)
            .append(yes)
            .append(Component.space())
            .append(no)
            .append(Component.text(" This prompt expires in " + formatSeconds(settings.promptTimeoutMillis()) + " seconds.", NamedTextColor.GRAY)));
    }

    private void savePendingKills() {
        try {
            pendingKillStore.save();
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save pending combat log kills", exception);
        }
    }

    private static boolean executePlayerCausedKill(Player offender, Player killer) {
        double damage = Math.max(1_000.0, offender.getHealth() + offender.getAbsorptionAmount() + 100.0);
        offender.setNoDamageTicks(0);
        offender.setKiller(killer);
        offender.damage(damage, killer);
        if (!offender.isDead() && offender.getHealth() > 0.0) {
            offender.setNoDamageTicks(0);
            offender.setKiller(killer);
            offender.setHealth(0.0);
        }
        return offender.isDead() || offender.getHealth() <= 0.0;
    }

    private static boolean executeUncreditedKill(Player offender) {
        double damage = Math.max(1_000.0, offender.getHealth() + offender.getAbsorptionAmount() + 100.0);
        offender.setNoDamageTicks(0);
        offender.setKiller(null);
        offender.damage(damage);
        if (!offender.isDead() && offender.getHealth() > 0.0) {
            offender.setNoDamageTicks(0);
            offender.setKiller(null);
            offender.setHealth(0.0);
        }
        return offender.isDead() || offender.getHealth() <= 0.0;
    }

    private String formatSeconds(long millis) {
        return Long.toString(Math.max(1L, (millis + 999L) / 1_000L));
    }

    public enum ChoiceResult {
        APPROVED,
        FORGIVEN,
        EXPIRED,
        NOT_YOURS
    }
}
