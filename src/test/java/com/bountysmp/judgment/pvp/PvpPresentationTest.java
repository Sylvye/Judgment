package com.bountysmp.judgment.pvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.scoreboard.Team;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class PvpPresentationTest {
    @TempDir Path directory;
    ServerMock server;
    PlayerMock on, off;
    AtomicReference<PvpSettings> settings;
    PvpService service;

    @BeforeEach void setup() throws Exception {
        server = MockBukkit.mock();
        on = server.addPlayer("Sylvye");
        off = server.addPlayer("Sylvyea");
        var board = server.getScoreboardManager().getMainScoreboard();
        on.setScoreboard(board);
        off.setScoreboard(board);
        var store = new PvpStore(directory.resolve("pvp-players.yml"));
        store.save(Map.of(
            on.getUniqueId(), new PvpState(true, -1, -1, Map.of()),
            off.getUniqueId(), new PvpState(false, -1, -1, Map.of())));
        settings = new AtomicReference<>(new PvpSettings(true, false, 0, 0, false, false));
        service = new PvpService(store, settings::get, () -> 1_000L, id -> false, Logger.getAnonymousLogger());
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void staleTeamsAreRemovedBeforeApplyingSavedPreferences() {
        var board = server.getScoreboardManager().getMainScoreboard();
        Team staleOn = oldTeam(on);
        Team staleOff = oldTeam(off);
        String staleOnName = staleOn.getName();
        String staleOffName = staleOff.getName();
        Team unrelated = board.registerNewTeam("unrelated");
        unrelated.prefix(Component.text("[PvP] "));
        Team collidingName = board.registerNewTeam("judpabcdef123456");
        collidingName.prefix(Component.text("[Other] "));

        PvpPresentation presentation = new PvpPresentation(service);
        presentation.refreshAll();

        assertNotSame(staleOn, board.getTeam(staleOnName));
        assertNull(board.getTeam(staleOffName));
        assertNull(board.getEntryTeam(off.getName()));
        assertEquals(off.getName(), plain(off.playerListName()));
        assertEquals("[PvP] " + on.getName(), plain(on.playerListName()));
        assertEquals("[PvP] ", plain(board.getEntryTeam(on.getName()).prefix()));
        assertSame(unrelated, board.getTeam("unrelated"));
        assertSame(collidingName, board.getTeam("judpabcdef123456"));

        settings.set(new PvpSettings(false, false, 0, 0, false, false));
        presentation.refreshAll();
        assertNull(board.getEntryTeam(on.getName()));
        assertEquals(on.getName(), plain(on.playerListName()));
        assertEquals(off.getName(), plain(off.playerListName()));
        assertTrue(service.canAttack(on.getUniqueId(), off.getUniqueId()));
    }

    @Test void staleTeamsAreRemovedWhenModuleStartsDisabled() {
        var board = server.getScoreboardManager().getMainScoreboard();
        Team stale = oldTeam(off);
        String staleName = stale.getName();
        settings.set(new PvpSettings(false, false, 0, 0, false, false));

        new PvpPresentation(service).refreshAll();

        assertNull(board.getTeam(staleName));
        assertNull(board.getEntryTeam(off.getName()));
        assertEquals(off.getName(), plain(off.playerListName()));
    }

    private Team oldTeam(PlayerMock player) {
        var board = server.getScoreboardManager().getMainScoreboard();
        String name = "judp" + player.getUniqueId().toString().replace("-", "").substring(0, 12);
        Team team = board.registerNewTeam(name);
        team.prefix(Component.text("[", NamedTextColor.GRAY)
            .append(Component.text("PvP", NamedTextColor.RED))
            .append(Component.text("] ", NamedTextColor.GRAY)));
        team.addEntry(player.getName());
        return team;
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
