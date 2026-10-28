package com.bountysmp.judgment.event;

import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.Nullable;

/**
 * Synchronous veto point for an eligible ordinary PvP status change, before it is saved.
 * Unchanged requests, administrator corrections, and requests rejected by built-in rules
 * do not fire this event. Cancellation does not start or reset any cooldown.
 */
public final class PvpToggleEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final boolean currentEnabled;
    private final boolean requestedEnabled;
    private boolean cancelled;
    private Component denialMessage;

    public PvpToggleEvent(UUID playerId, boolean currentEnabled, boolean requestedEnabled) {
        this.playerId = Objects.requireNonNull(playerId);
        this.currentEnabled = currentEnabled;
        this.requestedEnabled = requestedEnabled;
    }

    public UUID getPlayerId() { return playerId; }
    public boolean isCurrentEnabled() { return currentEnabled; }
    public boolean isRequestedEnabled() { return requestedEnabled; }
    public @Nullable Component getDenialMessage() { return denialMessage; }
    public void setDenialMessage(@Nullable Component message) { denialMessage = message; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
