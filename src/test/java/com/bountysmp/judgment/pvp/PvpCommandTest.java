package com.bountysmp.judgment.pvp;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PvpCommandTest {
    @TempDir Path directory;

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void rejectedToggleStatesThatNothingChangedAndSuccessfulTogglePersists() throws Exception {
        var server = MockBukkit.mock();
        var player = server.addPlayer("Sylvye");
        var clock = new AtomicLong(1_000);
        var store = new PvpStore(directory.resolve("pvp-players.yml"));
        var service = new PvpService(store, () -> new PvpSettings(true, false, 0, 600_000, false, false),
            clock::get, id -> false, Logger.getAnonymousLogger());
        assertEquals(PvpService.Outcome.CHANGED, service.change(player.getUniqueId(), true).outcome());
        service.recordCombat(player.getUniqueId(), server.addPlayer("Opponent").getUniqueId(), 30_000);
        var command = new PvpCommand(service, ignored -> { });

        assertTrue(command.onCommand(player, null, "pvp", new String[0]));
        assertEquals("PvP remains ON. No change was made. You can change it in 0h 10m 30s.",
            PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()));
        assertTrue(store.load().get(player.getUniqueId()).enabled());

        clock.set(631_000);
        assertTrue(command.onCommand(player, null, "pvp", new String[0]));
        assertEquals("PvP is OFF.", PlainTextComponentSerializer.plainText().serialize(player.nextComponentMessage()));
        assertFalse(store.load().get(player.getUniqueId()).enabled());
    }
}
