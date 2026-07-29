package com.bountysmp.judgment.storage;

import java.util.UUID;

public record PendingKill(UUID offenderId, String offenderName, UUID killerId, String killerName, long approvedAtMillis) {
}
