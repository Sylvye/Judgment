package com.bountysmp.judgment.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CombatTagStack {
    private static final Comparator<CombatTag> STACK_ORDER = Comparator
        .comparingInt(CombatTag::priority).reversed()
        .thenComparing(Comparator.comparingLong(CombatTag::updatedAtMillis).reversed());

    private final UUID ownerId;
    private final List<CombatTag> tags = new ArrayList<>();

    public CombatTagStack(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public void addOrRefresh(CombatTag tag) {
        for (int index = 0; index < tags.size(); index++) {
            CombatTag existing = tags.get(index);
            if (existing.sameTag(tag.opponentId(), tag.direction())) {
                tags.set(index, existing.refresh(tag.ownerName(), tag.opponentName(), tag.creditedName(), tag.expiresAtMillis(), tag.updatedAtMillis()));
                sort();
                return;
            }
        }
        tags.add(tag);
        sort();
    }

    public Optional<CombatTag> topActive(long nowMillis) {
        pruneExpired(nowMillis);
        if (tags.isEmpty()) {
            return Optional.empty();
        }
        sort();
        return Optional.of(tags.getFirst());
    }

    public List<CombatTag> activeTags(long nowMillis) {
        pruneExpired(nowMillis);
        sort();
        return List.copyOf(tags);
    }

    public Optional<CombatTag> get(UUID opponentId, CombatTagDirection direction) {
        return tags.stream()
            .filter(tag -> tag.sameTag(opponentId, direction))
            .findFirst();
    }

    public boolean remove(UUID opponentId, CombatTagDirection direction) {
        return tags.removeIf(tag -> tag.sameTag(opponentId, direction));
    }

    public boolean removeReferencing(UUID playerId) {
        return tags.removeIf(tag -> tag.opponentId().equals(playerId) || tag.creditedId().equals(playerId));
    }

    public void pruneExpired(long nowMillis) {
        tags.removeIf(tag -> !tag.isActive(nowMillis));
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    private void sort() {
        tags.sort(STACK_ORDER);
    }
}
