package com.bountysmp.judgment.pvp;

import com.bountysmp.judgment.JudgmentPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.scoreboard.Team;
import org.bukkit.event.inventory.*;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import java.util.HashSet;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PvpIntegrationTest {
    ServerMock server;
    JudgmentPlugin plugin;
    PlayerMock player;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        player = server.addPlayer("Explorer");
        plugin = MockBukkit.load(JudgmentPlugin.class);
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void commandRegistersTogglesAndHonorsCooldown() {
        assertNotNull(plugin.getCommand("pvp"));
        assertFalse(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        assertTrue(server.dispatchCommand(player, "pvp on"));
        assertTrue(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        assertTrue(server.dispatchCommand(player, "pvp off"));
        assertTrue(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        assertEquals(List.of("on", "off"), plugin.getCommand("pvp").tabComplete(player, "pvp", new String[]{""}));
        assertTrue(server.dispatchCommand(server.getConsoleSender(), "pvp"));
        assertTrue(server.dispatchCommand(player, "pvp invalid"));
        assertTrue(server.dispatchCommand(player, "judgment settings"));
        player.setOp(true);
        player.addAttachment(plugin, "judgment.admin", true);
        assertTrue(server.dispatchCommand(player, "judgment pvp Explorer on"));
        assertTrue(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        assertTrue(server.dispatchCommand(player, "judgment pvp Explorer off"));
        assertFalse(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        assertEquals(List.of("Explorer"), plugin.getCommand("judgment").tabComplete(player, "judgment", new String[]{"pvp", "E"}));
        assertEquals(List.of("on", "off"), plugin.getCommand("judgment").tabComplete(player, "judgment", new String[]{"pvp", "Explorer", "o"}));
    }

    @Test void prefixesPreserveAndRestoreOriginalPresentation() {
        var board = server.getScoreboardManager().getMainScoreboard();
        player.setScoreboard(board);
        Team original = board.registerNewTeam("builders");
        original.prefix(Component.text("[Builder] ", NamedTextColor.GOLD));
        original.suffix(Component.text("!"));
        original.color(NamedTextColor.AQUA);
        for (Team.Option option : Team.Option.values()) original.setOption(option, Team.OptionStatus.ALWAYS);
        original.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        original.addEntry(player.getName());
        Component tabName = Component.text("Custom Name", NamedTextColor.AQUA);
        player.playerListName(tabName);
        server.dispatchCommand(player, "pvp on");
        Team derived = board.getEntryTeam(player.getName());
        assertNotEquals(original, derived);
        assertEquals("[PvP] [Builder] ", plain(derived.prefix()));
        assertEquals(original.suffix(), derived.suffix());
        assertEquals(original.color(), derived.color());
        assertEquals(Team.OptionStatus.NEVER, derived.getOption(Team.Option.COLLISION_RULE));
        assertEquals("[PvP] Custom Name", plain(player.playerListName()));
        server.dispatchCommand(player, "pvp on");
        assertEquals("[PvP] Custom Name", plain(player.playerListName()));
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(original, board.getEntryTeam(player.getName()));
        assertEquals(tabName, player.playerListName());
        assertEquals("[Builder] ", plain(original.prefix()));
    }

    @Test void adminGuiEditsAllPvpSettingsAndTurningOffRemovesPrefixes() {
        player.setOp(true);
        server.dispatchCommand(player, "judgment");
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(10));
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(12));
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(14));
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(21));
        assertNotNull(player.getOpenInventory().getTopInventory().getItem(23));
        click(21);
        assertEquals(45, player.getOpenInventory().getTopInventory().getSize());
        for (int slot : new int[] {11, 13, 15, 20, 22, 24, 29, 31, 33, 40})
            assertNotNull(player.getOpenInventory().getTopInventory().getItem(slot));
        click(11, ClickType.RIGHT);
        assertEquals("global", plugin.getConfig().getString("combat-item-scopes.elytra"));
        click(11, ClickType.RIGHT);
        assertEquals("pvp", plugin.getConfig().getString("combat-item-scopes.elytra"));
        click(40);
        click(23);
        for (int slot : new int[] {10, 12, 14, 16, 22})
            assertNotNull(player.getOpenInventory().getTopInventory().getItem(slot));
        click(22);
        click(10);
        assertTrue(plugin.getConfig().getBoolean("pvp.default-enabled"));
        assertFalse(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        click(12);
        chat("9223372036854775807h");
        assertEquals(86400, plugin.getConfig().getDouble("pvp.toggle-cooldown-seconds"));
        chat("0s");
        assertEquals(0, plugin.getConfig().getDouble("pvp.toggle-cooldown-seconds"));
        click(14);
        chat("5m");
        assertEquals(300, plugin.getConfig().getDouble("pvp.post-combat-delay-seconds"));
        server.dispatchCommand(player, "pvp on");
        assertTrue(plain(player.playerListName()).startsWith("[PvP] "));
        server.dispatchCommand(player, "pvp off");
        assertFalse(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        assertFalse(plain(player.playerListName()).contains("[PvP]"));
        assertNull(player.getScoreboard().getEntryTeam(player.getName()));
    }

    private void click(int slot) {
        click(slot, ClickType.LEFT);
    }

    private void click(int slot, ClickType clickType) {
        var event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER,
            slot, clickType, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    private void chat(String text) {
        var event = new AsyncChatEvent(false, player, new HashSet<>(), ChatRenderer.defaultRenderer(),
            Component.text(text), Component.text(text), null);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        server.getScheduler().performOneTick();
    }

    private String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
}
