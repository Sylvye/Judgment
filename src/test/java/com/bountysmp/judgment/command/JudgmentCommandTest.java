package com.bountysmp.judgment.command;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.model.PunishmentMode;
import com.bountysmp.judgment.service.JudgmentService;
import com.bountysmp.judgment.storage.PendingKillStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgmentCommandTest {
    @TempDir
    Path tempDir;

    private PlayerMock admin;
    private JudgmentCommand command;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        var plugin = MockBukkit.createMockPlugin();
        var store = new PendingKillStore(tempDir.resolve("pending-kills.yml"));
        store.load();
        var service = new JudgmentService(
            plugin,
            new JudgmentSettings(30_000L, 10_000L, PunishmentMode.RELOG),
            store,
            System::currentTimeMillis,
            (offender, killer) -> true,
            offender -> true
        );
        command = new JudgmentCommand(service, null);
        admin = MockBukkit.getMock().addPlayer("Admin");
        admin.addAttachment(plugin, "judgment.admin", true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void tabCompletesDebugStackPath() {
        assertEquals(List.of("debug"), command.onTabComplete(admin, null, "judgment", new String[] {"d"}));
        assertEquals(List.of("stack"), command.onTabComplete(admin, null, "judgment", new String[] {"debug", "s"}));
    }

    @Test
    void tabCompletesOnlinePlayersForDebugStack() {
        MockBukkit.getMock().addPlayer("Sylvye");
        MockBukkit.getMock().addPlayer("Other");

        List<String> completions = command.onTabComplete(admin, null, "judgment", new String[] {"debug", "stack", "S"});

        assertEquals(List.of("Sylvye"), completions);
        assertTrue(command.onTabComplete(admin, null, "judgment", new String[] {"debug", "stack", ""}).contains("Other"));
    }
}
