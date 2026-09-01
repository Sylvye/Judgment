package com.bountysmp.judgment.pvp;

import com.bountysmp.judgment.JudgmentPlugin;
import com.bountysmp.judgment.gui.CombatItemMenuHolder;
import com.bountysmp.judgment.gui.CombatLogMenuHolder;
import com.bountysmp.judgment.gui.DragonEggMenuHolder;
import com.bountysmp.judgment.gui.PvpTagsMenuHolder;
import com.bountysmp.judgment.gui.SettingsMenuHolder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.scoreboard.Team;
import org.bukkit.World;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import java.util.HashSet;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
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
        player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(org.bukkit.Material.DRAGON_EGG));
        assertTrue(server.dispatchCommand(player, "pvp off"));
        assertTrue(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        player.getInventory().clear();
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

    @Test void onlyPvpEnabledPlayersCanPickupTheDragonEgg() {
        org.bukkit.entity.Item egg = player.getWorld().dropItem(player.getLocation(), new ItemStack(org.bukkit.Material.DRAGON_EGG));
        PlayerAttemptPickupItemEvent blocked = new PlayerAttemptPickupItemEvent(player, egg);
        server.getPluginManager().callEvent(blocked);
        assertTrue(blocked.isCancelled());
        assertFalse(blocked.getFlyAtPlayer());

        assertTrue(server.dispatchCommand(player, "pvp on"));
        PlayerAttemptPickupItemEvent allowed = new PlayerAttemptPickupItemEvent(player, egg);
        server.getPluginManager().callEvent(allowed);
        assertFalse(allowed.isCancelled());
    }

    @Test void adminGuiNavigatesModulesEditsSettingsAndReturnsToTheCorrectMenu() {
        player.setOp(true);
        server.dispatchCommand(player, "judgment");
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof SettingsMenuHolder);
        for (int slot : new int[] {10, 12, 14, 16})
            assertNotNull(player.getOpenInventory().getTopInventory().getItem(slot));

        click(10);
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof CombatLogMenuHolder);
        for (int slot : new int[] {10, 12, 14, 16, 22, 26})
            assertNotNull(player.getOpenInventory().getTopInventory().getItem(slot));
        click(10);
        chat("45s");
        assertEquals(45, plugin.getConfig().getDouble("combat-tag-seconds"));
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof CombatLogMenuHolder);
        click(26);

        click(12);
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof PvpTagsMenuHolder);
        for (int slot : new int[] {10, 12, 14, 16, 22, 26})
            assertNotNull(player.getOpenInventory().getTopInventory().getItem(slot));
        click(10);
        assertTrue(plugin.getConfig().getBoolean("pvp.default-enabled"));
        assertFalse(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        click(12);
        chat("9223372036854775807h");
        assertEquals(86400, plugin.getConfig().getDouble("pvp.toggle-cooldown-seconds"));
        chat("0s");
        assertEquals(0, plugin.getConfig().getDouble("pvp.toggle-cooldown-seconds"));
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof PvpTagsMenuHolder);
        click(14);
        chat("5m");
        assertEquals(300, plugin.getConfig().getDouble("pvp.post-combat-delay-seconds"));
        click(16);
        assertTrue(plugin.getConfig().getBoolean("pvp.prevent-toggle-in-end"));
        click(22);
        assertTrue(plugin.getConfig().getBoolean("pvp.prevent-toggle-in-nether"));
        click(26);

        click(14);
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof CombatItemMenuHolder);
        assertEquals(54, player.getOpenInventory().getTopInventory().getSize());
        for (int slot : new int[] {4, 9, 11, 12, 13, 14, 15, 16, 27, 29, 30, 31, 32, 33, 49})
            assertNotNull(player.getOpenInventory().getTopInventory().getItem(slot));
        click(11, ClickType.RIGHT);
        assertEquals("global", plugin.getConfig().getString("combat-item-scopes.elytra"));
        click(11, ClickType.RIGHT);
        assertEquals("pvp", plugin.getConfig().getString("combat-item-scopes.elytra"));
        click(29, ClickType.SHIFT_LEFT);
        chat("0.5");
        assertEquals(0.5, plugin.getConfig().getDouble("combat-item-damage-modifiers.tnt"));
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof CombatItemMenuHolder);
        click(49);

        click(16);
        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof DragonEggMenuHolder);
        for (int slot : new int[] {10, 12, 14, 16, 26})
            assertNotNull(player.getOpenInventory().getTopInventory().getItem(slot));
        click(26);

        WorldMock world = (WorldMock) player.getWorld();
        world.setEnvironment(World.Environment.THE_END);
        server.dispatchCommand(player, "pvp on");
        assertFalse(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        world.setEnvironment(World.Environment.NETHER);
        server.dispatchCommand(player, "pvp on");
        assertFalse(plugin.getPvpService().isPvpEnabled(player.getUniqueId()));
        world.setEnvironment(World.Environment.NORMAL);
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
