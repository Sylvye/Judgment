package com.bountysmp.judgment.combatitem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FlightLandingStoreTest {
    @TempDir Path directory;

    @Test void missingStateDefaultsEmptyAndBothFlightCausesRoundTrip() throws IOException {
        FlightLandingStore store = new FlightLandingStore(directory.resolve("flight.yml"));
        assertTrue(store.load().isEmpty());
        Map<UUID, EnumSet<CombatItemAction>> pending = Map.of(UUID.randomUUID(),
            EnumSet.of(CombatItemAction.ELYTRA, CombatItemAction.FIREWORKS));
        store.save(pending);
        assertEquals(pending, store.load());
        store.save(Map.of());
        assertTrue(store.load().isEmpty());
    }

    @Test void corruptStateIsRejectedInsteadOfErasingRestrictions() throws IOException {
        Path path = directory.resolve("flight.yml");
        FlightLandingStore store = new FlightLandingStore(path);
        Files.writeString(path, "pending: [invalid");
        assertThrows(IOException.class, store::load);
        Files.writeString(path, "pending: invalid");
        assertThrows(IOException.class, store::load);
        Files.writeString(path, "pending:\n  " + UUID.randomUUID() + ": ELYTRA\n");
        assertThrows(IOException.class, store::load);
        Files.writeString(path, "pending:\n  " + UUID.randomUUID() + ": [123]\n");
        assertThrows(IOException.class, store::load);
        Files.writeString(path, "pending:\n  " + UUID.randomUUID() + ": [TNT]\n");
        assertThrows(IOException.class, store::load);
    }
}
