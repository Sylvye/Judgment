package com.bountysmp.judgment.combatitem;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CombatItemCooldownManager {
    public enum Outcome { ALLOWED, BANNED, COOLDOWN }
    public record Result(Outcome outcome, long remainingMillis) {
        public boolean allowed() { return outcome == Outcome.ALLOWED; }
    }

    private final CombatItemCooldownStore store;
    private final Supplier<CombatItemSettings> settings;
    private final Predicate<UUID> inCombat;
    private final LongSupplier clock;
    private final Logger logger;
    private final Map<UUID, Map<CombatItemAction, Long>> lastUses;

    public CombatItemCooldownManager(CombatItemCooldownStore store, Supplier<CombatItemSettings> settings,
                                     Predicate<UUID> inCombat, LongSupplier clock, Logger logger) {
        this.store = store;
        this.settings = settings;
        this.inCombat = inCombat;
        this.clock = clock;
        this.logger = logger;
        Map<UUID, Map<CombatItemAction, Long>> loaded;
        try {
            loaded = store.load();
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Failed to load combat item cooldowns; starting with no active timers.", exception);
            loaded = new HashMap<>();
        }
        this.lastUses = new HashMap<>();
        loaded.forEach((uuid, actions) -> this.lastUses.put(uuid, new EnumMap<>(actions)));
        boolean cleared = false;
        for (CombatItemAction action : CombatItemAction.values()) {
            if (settings.get().seconds(action) <= 0.0)
                cleared |= clear(action);
        }
        cleared |= clearPlayersOutsideCombat();
        if (cleared) save();
    }

    public Result attempt(UUID playerId, CombatItemAction action) {
        if (!applies(playerId, action)) return new Result(Outcome.ALLOWED, 0L);
        double seconds = settings.get().seconds(action);
        if (seconds == -1.0) return new Result(Outcome.BANNED, 0L);
        if (seconds == 0.0) return new Result(Outcome.ALLOWED, 0L);
        long duration = Math.max(1L, Math.round(seconds * 1_000.0));
        long now = clock.getAsLong();
        Long lastUse = lastUses.getOrDefault(playerId, Map.of()).get(action);
        if (lastUse != null) {
            long elapsed = Math.max(0L, now - lastUse);
            if (elapsed < duration) return new Result(Outcome.COOLDOWN, duration - elapsed);
        }
        lastUses.computeIfAbsent(playerId, ignored -> new EnumMap<>(CombatItemAction.class)).put(action, now);
        save();
        return new Result(Outcome.ALLOWED, 0L);
    }

    /** Starts a cooldown after an action that itself created the player's combat tag. */
    public void recordFirstCombatUse(UUID playerId, CombatItemAction action) {
        if (settings.get().scope(action) != CombatItemScope.PVP_ONLY || !inCombat.test(playerId)) return;
        double seconds = settings.get().seconds(action);
        if (seconds <= 0.0) return;
        Map<CombatItemAction, Long> actions = lastUses.computeIfAbsent(playerId,
            ignored -> new EnumMap<>(CombatItemAction.class));
        if (!actions.containsKey(action)) {
            actions.put(action, clock.getAsLong());
            save();
        }
    }

    public Map<CombatItemAction, Long> activeCooldowns(UUID playerId) {
        long now = clock.getAsLong();
        EnumMap<CombatItemAction, Long> active = new EnumMap<>(CombatItemAction.class);
        Map<CombatItemAction, Long> actions = lastUses.getOrDefault(playerId, Map.of());
        actions.forEach((action, lastUse) -> {
            if (!applies(playerId, action)) return;
            double seconds = settings.get().seconds(action);
            if (seconds <= 0.0) return;
            long duration = Math.max(1L, Math.round(seconds * 1_000.0));
            long remaining = duration - Math.max(0L, now - lastUse);
            if (remaining > 0L) active.put(action, remaining);
        });
        return Map.copyOf(active);
    }

    public void clearPvpCooldowns(UUID playerId) {
        Map<CombatItemAction, Long> actions = lastUses.get(playerId);
        if (actions == null) return;
        boolean changed = actions.keySet().removeIf(action -> settings.get().scope(action) == CombatItemScope.PVP_ONLY);
        if (actions.isEmpty()) lastUses.remove(playerId);
        if (changed) save();
    }

    public void clearPlayersOutsideCombatAndSave() {
        if (clearPlayersOutsideCombat()) save();
    }

    public void settingChanged(CombatItemAction action, double value) {
        if (value > 0.0) return;
        if (clear(action)) save();
    }

    public void scopeChanged(CombatItemAction action) {
        if (clear(action)) save();
    }

    public CombatItemScope scope(CombatItemAction action) { return settings.get().scope(action); }

    Map<UUID, Map<CombatItemAction, Long>> snapshot() {
        Map<UUID, Map<CombatItemAction, Long>> copy = new HashMap<>();
        lastUses.forEach((uuid, actions) -> copy.put(uuid, Map.copyOf(actions)));
        return Map.copyOf(copy);
    }

    private void save() {
        try {
            store.save(lastUses);
        } catch (IOException exception) {
            logger.log(Level.SEVERE, "Failed to save combat item cooldowns.", exception);
        }
    }

    private boolean clear(CombatItemAction action) {
        boolean changed = false;
        for (Map<CombatItemAction, Long> actions : lastUses.values())
            changed |= actions.remove(action) != null;
        lastUses.values().removeIf(Map::isEmpty);
        return changed;
    }

    private boolean clearPlayersOutsideCombat() {
        boolean changed = false;
        for (Map.Entry<UUID, Map<CombatItemAction, Long>> entry : lastUses.entrySet()) {
            if (inCombat.test(entry.getKey())) continue;
            changed |= entry.getValue().keySet().removeIf(
                action -> settings.get().scope(action) == CombatItemScope.PVP_ONLY);
        }
        lastUses.values().removeIf(Map::isEmpty);
        return changed;
    }

    private boolean applies(UUID playerId, CombatItemAction action) {
        return settings.get().scope(action) == CombatItemScope.GLOBAL || inCombat.test(playerId);
    }
}
