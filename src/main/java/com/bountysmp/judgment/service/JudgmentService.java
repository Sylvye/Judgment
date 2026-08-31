package com.bountysmp.judgment.service;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.model.CombatLogCase;
import com.bountysmp.judgment.model.CombatTag;
import com.bountysmp.judgment.model.CombatTagDirection;
import com.bountysmp.judgment.model.CombatTagStack;
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
import java.util.ArrayList;
import java.util.List;
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
    private final Map<UUID, CombatTagStack> combatStacks = new ConcurrentHashMap<>();
    private final Map<String, CombatLogCase> openCases = new ConcurrentHashMap<>();
    private volatile JudgmentSettings settings;
    private final java.util.Set<UUID> punishmentTargets = new java.util.HashSet<>();

    public boolean isExecutingPunishment(UUID playerId) {
        return punishmentTargets.contains(playerId);
    }

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

    public Plugin getPlugin() {
        return plugin;
    }

    public void recordPvpDamage(Player victim, Player attacker) {
        if (victim.getUniqueId().equals(attacker.getUniqueId()) || settings.combatTagMillis() <= 0L) {
            return;
        }

        long expiresAt = clock.getAsLong() + settings.combatTagMillis();
        long updatedAt = clock.getAsLong();
        tagParticipant(victim, attacker, attacker, CombatTagDirection.INCOMING, expiresAt, updatedAt);
        tagParticipant(attacker, victim, victim, CombatTagDirection.OUTGOING, expiresAt, updatedAt);
    }

    public Optional<CombatTag> getCombatTag(UUID playerId) {
        CombatTagStack stack = combatStacks.get(playerId);
        if (stack == null) {
            return Optional.empty();
        }
        return stack.topActive(clock.getAsLong());
    }

    public List<CombatTag> getCombatStack(UUID playerId) {
        CombatTagStack stack = combatStacks.get(playerId);
        if (stack == null) {
            return List.of();
        }
        List<CombatTag> activeTags = stack.activeTags(clock.getAsLong());
        if (activeTags.isEmpty()) {
            combatStacks.remove(playerId);
        }
        return activeTags;
    }

    public Optional<CombatLogCase> getOpenCase(String caseId) {
        return Optional.ofNullable(openCases.get(caseId));
    }

    public java.util.List<CombatLogCase> openCases() {
        return java.util.List.copyOf(openCases.values());
    }

    public void handleQuit(Player player) {
        long now = clock.getAsLong();
        CombatTagStack stack = combatStacks.remove(player.getUniqueId());
        if (stack == null) {
            return;
        }
        Optional<CombatTag> topTag = stack.topActive(now);
        if (topTag.isEmpty()) {
            return;
        }

        CombatTag tag = topTag.get();
        Player killer = Bukkit.getPlayer(tag.creditedId());
        if (killer == null) {
            return;
        }

        String caseId = UUID.randomUUID().toString();
        CombatLogCase combatLogCase = new CombatLogCase(
            caseId,
            tag.ownerId(),
            tag.ownerName(),
            tag.creditedId(),
            tag.creditedName(),
            now + settings.promptTimeoutMillis()
        );
        openCases.put(caseId, combatLogCase);
        sendPrompt(killer, combatLogCase);

        long ticks = Math.max(1L, (settings.promptTimeoutMillis() + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> expireCase(caseId), ticks);
    }

    public void handleDeath(Player player) {
        combatStacks.remove(player.getUniqueId());
        for (Map.Entry<UUID, CombatTagStack> entry : new ArrayList<>(combatStacks.entrySet())) {
            CombatTagStack stack = entry.getValue();
            if (stack.removeReferencing(player.getUniqueId())) {
                notifyNoLongerInCombat(entry.getKey(), player.getName());
            }
            if (stack.isEmpty()) {
                combatStacks.remove(entry.getKey());
            }
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
        boolean killed;
        punishmentTargets.add(offenderId);
        try {
            killed = killer != null
                ? punishmentExecutor.execute(offender, killer)
                : uncreditedPunishmentExecutor.execute(offender);
        } finally {
            punishmentTargets.remove(offenderId);
        }
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

    public List<Component> debugStack(Player target) {
        List<CombatTag> tags = getCombatStack(target.getUniqueId());
        if (tags.isEmpty()) {
            return List.of(Component.text("No active combat tags.", NamedTextColor.YELLOW));
        }

        long now = clock.getAsLong();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("Combat stack for " + target.getName() + ":", NamedTextColor.GOLD));
        for (int index = 0; index < tags.size(); index++) {
            CombatTag tag = tags.get(index);
            boolean promptEligible = Bukkit.getPlayer(tag.creditedId()) != null;
            lines.add(Component.text(
                "#" + (index + 1)
                    + " " + tag.direction().displayName()
                    + " opponent=" + tag.opponentName()
                    + " credit=" + tag.creditedName()
                    + " remaining=" + formatSeconds(Math.max(0L, tag.expiresAtMillis() - now)) + "s"
                    + " promptEligible=" + promptEligible,
                index == 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY
            ));
        }
        return lines;
    }

    private void tagParticipant(Player player, Player opponent, Player credited, CombatTagDirection direction, long expiresAt, long updatedAt) {
        long now = clock.getAsLong();
        CombatTagStack stack = combatStacks.computeIfAbsent(player.getUniqueId(), CombatTagStack::new);
        stack.pruneExpired(now);
        boolean enteringCombat = stack.isEmpty();

        stack.addOrRefresh(new CombatTag(
            player.getUniqueId(),
            player.getName(),
            opponent.getUniqueId(),
            opponent.getName(),
            credited.getUniqueId(),
            credited.getName(),
            direction,
            expiresAt,
            updatedAt
        ));

        if (enteringCombat) {
            player.sendActionBar(Component.text(
                "You are now in combat for " + formatSeconds(settings.combatTagMillis()) + " seconds!",
                NamedTextColor.RED
            ));
        }

        long ticks = Math.max(1L, ((expiresAt - now) + 49L) / 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> expireCombatTag(player.getUniqueId(), opponent.getUniqueId(), direction, expiresAt), ticks);
    }

    private void expireCombatTag(UUID playerId, UUID opponentId, CombatTagDirection direction, long expiresAt) {
        CombatTagStack stack = combatStacks.get(playerId);
        if (stack == null) {
            return;
        }
        Optional<CombatTag> currentTag = stack.get(opponentId, direction);
        if (currentTag.isEmpty() || currentTag.get().expiresAtMillis() != expiresAt) {
            return;
        }

        long now = clock.getAsLong();
        if (currentTag.get().isActive(now)) {
            long remainingTicks = Math.max(1L, ((expiresAt - now) + 49L) / 50L);
            Bukkit.getScheduler().runTaskLater(plugin, () -> expireCombatTag(playerId, opponentId, direction, expiresAt), remainingTicks);
            return;
        }

        stack.remove(opponentId, direction);
        if (!stack.isEmpty()) {
            return;
        }

        combatStacks.remove(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            player.sendActionBar(Component.text("You have left combat, you may now log out.", NamedTextColor.GREEN));
        }
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
