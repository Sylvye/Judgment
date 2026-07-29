package com.bountysmp.judgment.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingKillStoreTest {
    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsPendingKills() throws Exception {
        Path path = tempDir.resolve("pending-kills.yml");
        UUID offenderId = UUID.randomUUID();
        UUID killerId = UUID.randomUUID();

        PendingKillStore store = new PendingKillStore(path);
        store.put(new PendingKill(offenderId, "Logger", killerId, "Killer", 123L));
        store.save();

        PendingKillStore loaded = new PendingKillStore(path);
        loaded.load();

        PendingKill pendingKill = loaded.get(offenderId).orElseThrow();
        assertEquals("Logger", pendingKill.offenderName());
        assertEquals(killerId, pendingKill.killerId());
        assertEquals("Killer", pendingKill.killerName());
        assertEquals(123L, pendingKill.approvedAtMillis());
        assertTrue(loaded.values().contains(pendingKill));
    }
}
