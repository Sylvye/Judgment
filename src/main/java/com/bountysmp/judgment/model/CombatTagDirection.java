package com.bountysmp.judgment.model;

public enum CombatTagDirection {
    INCOMING(2),
    OUTGOING(1);

    private final int priority;

    CombatTagDirection(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }

    public String displayName() {
        return switch (this) {
            case INCOMING -> "INCOMING";
            case OUTGOING -> "OUTGOING";
        };
    }
}
