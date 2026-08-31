package com.bountysmp.judgment.pvp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Access on the server thread. All eligibility timestamps are wall-clock milliseconds. */
public final class PvpService {
    public enum Outcome { CHANGED, UNCHANGED, WAIT, STORAGE_ERROR }
    public record Result(Outcome outcome, boolean enabled, long waitMillis) {}

    private final PvpStore store;
    private final Supplier<PvpSettings> settings;
    private final LongSupplier clock;
    private final Predicate<UUID> activelyTagged;
    private final Logger logger;
    private Map<UUID, PvpState> states;
    private boolean storageHealthy = true;

    public PvpService(PvpStore store, Supplier<PvpSettings> settings, LongSupplier clock,
                      Predicate<UUID> activelyTagged, Logger logger) {
        this.store = store;
        this.settings = settings;
        this.clock = clock;
        this.activelyTagged = activelyTagged;
        this.logger = logger;
        try {
            states = store.load();
        } catch (IOException exception) {
            states = Map.of();
            storageHealthy = false;
            logger.log(Level.SEVERE, "Cannot load PvP data. PvP attacks and toggles are blocked; repair the file and restart.", exception);
        }
    }

    public boolean initialize(UUID id) {
        if (!storageHealthy) return false;
        if (states.containsKey(id)) return true;
        var updated = new HashMap<>(states);
        updated.put(id, new PvpState(settings.get().defaultEnabled(), -1, -1, Map.of()));
        return commit(updated);
    }

    public boolean isPvpEnabled(UUID id) {
        return initialize(id) && states.get(id).enabled();
    }

    public boolean canAttack(UUID attacker, UUID victim) {
        return attacker.equals(victim) || (isPvpEnabled(attacker) && isPvpEnabled(victim));
    }

    public Result change(UUID id, Boolean requested) {
        if (!initialize(id)) return new Result(Outcome.STORAGE_ERROR, savedEnabled(id), 0);
        PvpState old = states.get(id);
        boolean enabled = requested == null ? !old.enabled() : requested;
        if (enabled == old.enabled()) return new Result(Outcome.UNCHANGED, enabled, 0);
        long now = clock.getAsLong();
        long wait = remaining(old.lastToggleMillis(), settings.get().toggleCooldownMillis(), now);
        if (!enabled) {
            wait = Math.max(wait, remaining(old.combatEndMillis(), settings.get().postCombatDelayMillis(), now));
            // Even a configured zero post-combat delay cannot bypass an active tag.
            if (activelyTagged.test(id) || old.combatEndMillis() > now) wait = Math.max(1L, wait);
        }
        if (wait > 0) return new Result(Outcome.WAIT, old.enabled(), wait);
        var updated = new HashMap<>(states);
        updated.put(id, new PvpState(enabled, now, old.combatEndMillis(), old.opponents()));
        return commit(updated) ? new Result(Outcome.CHANGED, enabled, 0)
            : new Result(Outcome.STORAGE_ERROR, old.enabled(), 0);
    }

    /** Administrator correction: deliberately bypasses player cooldown and combat waits. */
    public Result adminSet(UUID id, boolean enabled) {
        if (!initialize(id)) return new Result(Outcome.STORAGE_ERROR, savedEnabled(id), 0);
        PvpState old = states.get(id);
        if (old.enabled() == enabled) return new Result(Outcome.UNCHANGED, enabled, 0);
        var updated = new HashMap<>(states);
        updated.put(id, new PvpState(enabled, old.lastToggleMillis(), old.combatEndMillis(), old.opponents()));
        return commit(updated) ? new Result(Outcome.CHANGED, enabled, 0)
            : new Result(Outcome.STORAGE_ERROR, old.enabled(), 0);
    }

    public void recordCombat(UUID a, UUID b, long durationMillis) {
        if (a.equals(b) || !initialize(a) || !initialize(b)) return;
        long now = clock.getAsLong();
        long end = add(now, Math.max(0, durationMillis));
        var updated = new HashMap<>(states);
        recordParticipant(updated, a, b, now, end);
        recordParticipant(updated, b, a, now, end);
        commit(updated);
    }

    private void recordParticipant(Map<UUID, PvpState> updated, UUID id, UUID opponent, long now, long end) {
        PvpState old = states.get(id);
        var opponents = new HashMap<>(old.opponents());
        opponents.values().removeIf(expiry -> expiry <= now);
        // A shorter tag setting must not erase an already-running reciprocal tag
        // or a combat deadline retained across a reconnect/restart.
        opponents.merge(opponent, end, Math::max);
        long latest = opponents.values().stream().mapToLong(Long::longValue).max().orElse(end);
        updated.put(id, new PvpState(old.enabled(), old.lastToggleMillis(), latest, opponents));
    }

    public void handleDeath(UUID dead) {
        if (!storageHealthy) return;
        long now = clock.getAsLong();
        var updated = new HashMap<>(states);
        states.forEach((id, old) -> {
            var opponents = new HashMap<>(old.opponents());
            opponents.values().removeIf(expiry -> expiry <= now);
            boolean ended = false;
            if (id.equals(dead) && old.combatEndMillis() > now) {
                opponents.clear();
                ended = true;
            } else if (opponents.remove(dead) != null) {
                ended = true;
            }
            if (ended) {
                long end = opponents.values().stream().mapToLong(Long::longValue).max().orElse(now);
                updated.put(id, new PvpState(old.enabled(), old.lastToggleMillis(), end, opponents));
            }
        });
        if (!updated.equals(states)) commit(updated);
    }

    private boolean savedEnabled(UUID id) {
        return states.containsKey(id) && states.get(id).enabled();
    }

    private boolean commit(Map<UUID, PvpState> updated) {
        try {
            store.save(updated);
            states = updated;
            return true;
        } catch (IOException exception) {
            storageHealthy = false;
            logger.log(Level.SEVERE, "Cannot save PvP data. PvP attacks and toggles are blocked until restart; repair storage first.", exception);
            return false;
        }
    }

    private static long remaining(long start, long duration, long now) {
        return start < 0 ? 0 : Math.max(0, add(start, duration) - now);
    }

    private static long add(long a, long b) {
        return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b;
    }
}
