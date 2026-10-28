package com.bountysmp.judgment.gui;

import com.bountysmp.judgment.JudgmentPlugin;
import com.bountysmp.judgment.enchantment.EnchantmentFixtures;
import com.bountysmp.judgment.enchantment.EnchantmentSettings;
import me.easyenchants.event.EnchantedBookApplyEvent;
import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.HashSet;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EnchantmentGuiTest {
    ServerMock server;
    JudgmentPlugin plugin;
    PlayerMock admin;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        admin = server.addPlayer();
        admin.setOp(true);
        MockBukkit.createMockPlugin("EasyEnchants");
        plugin = MockBukkit.load(JudgmentPlugin.class);
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private void open() { server.dispatchCommand(admin, "judgment settings"); click(16); }

    private void click(int slot) { click(slot, ClickType.LEFT); }
    private void click(int slot, ClickType type) {
        var event = new InventoryClickEvent(admin.getOpenInventory(), InventoryType.SlotType.CONTAINER,
            slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    private void chat(String message) {
        var event = new AsyncChatEvent(false, admin, new HashSet<>(), ChatRenderer.defaultRenderer(),
            Component.text(message), Component.text(message), null);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        server.getScheduler().performOneTick();
    }

    private int slot(NamespacedKey key) {
        EnchantmentMenuHolder holder = (EnchantmentMenuHolder) admin.getOpenInventory().getTopInventory().getHolder();
        for (int slot = 0; slot < 45; slot++) if (key.equals(holder.enchantmentAt(slot))) return slot;
        fail("Enchantment missing from current page: " + key);
        return -1;
    }

    @Test void automaticallyRegistersEasyEnchantsHookAndUsesGuiChangesImmediately() {
        open();
        click(slot(Enchantment.SHARPNESS.getKey()));
        chat("3");
        ItemStack sword = new ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        var event = new EnchantedBookApplyEvent(admin, sword, new ItemStack(org.bukkit.Material.ENCHANTED_BOOK));
        server.getPluginManager().callEvent(event);
        assertEquals(3, event.limitLevel(Enchantment.SHARPNESS, 5));
        click(45);
        var disabled = new EnchantedBookApplyEvent(admin, sword, new ItemStack(org.bukkit.Material.ENCHANTED_BOOK));
        server.getPluginManager().callEvent(disabled);
        assertEquals(5, disabled.limitLevel(Enchantment.SHARPNESS, 5));
    }

    @Test void listsRegistryAndEditsRulesWithImmediateInventoryCorrectionAndPersistence() {
        ItemStack sword = new ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        admin.getInventory().setItem(0, sword);
        open();
        assertInstanceOf(EnchantmentMenuHolder.class, admin.getOpenInventory().getTopInventory().getHolder());
        assertEquals(54, admin.getOpenInventory().getTopInventory().getSize());
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        assertEquals(Math.min(45, registry.stream().count()), java.util.stream.IntStream.range(0, 45)
            .filter(index -> admin.getOpenInventory().getTopInventory().getItem(index) != null).count());
        click(slot(Enchantment.SHARPNESS.getKey()));
        chat("3.5");
        assertFalse(plugin.getConfig().contains("enchantment-rules.limits.minecraft:sharpness"));
        chat("3");
        assertEquals(3, admin.getInventory().getItem(0).getEnchantmentLevel(Enchantment.SHARPNESS));
        plugin.reloadConfig();
        assertEquals(3, EnchantmentSettings.fromConfig(plugin.getConfig(), Logger.getAnonymousLogger()).limit(Enchantment.SHARPNESS));
        click(slot(Enchantment.WIND_BURST.getKey()));
        chat("-1");
        plugin.reloadConfig();
        assertEquals(-1, EnchantmentSettings.fromConfig(plugin.getConfig(), Logger.getAnonymousLogger()).limit(Enchantment.WIND_BURST));
        assertEquals(3, EnchantmentSettings.fromConfig(plugin.getConfig(), Logger.getAnonymousLogger()).limit(Enchantment.SHARPNESS));
    }

    @Test void customRegistryPaginationPreservesPageForCancellationAndEdits() throws Exception {
        for (int i = 0; i < 15; i++) EnchantmentFixtures.register(new NamespacedKey("zzcustom", "enchantment_" + i));
        open();
        assertNotNull(admin.getOpenInventory().getTopInventory().getItem(50));
        click(50);
        var holder = (EnchantmentMenuHolder) admin.getOpenInventory().getTopInventory().getHolder();
        assertEquals(1, holder.page());
        var selected = holder.enchantmentAt(0);
        click(0);
        chat("cancel");
        assertEquals(1, ((EnchantmentMenuHolder) admin.getOpenInventory().getTopInventory().getHolder()).page());
        click(0);
        chat("2");
        assertEquals(1, ((EnchantmentMenuHolder) admin.getOpenInventory().getTopInventory().getHolder()).page());
        plugin.reloadConfig();
        assertEquals(2, EnchantmentSettings.fromConfig(plugin.getConfig(), Logger.getAnonymousLogger()).limits().get(selected));
        click(48);
        assertEquals(0, ((EnchantmentMenuHolder) admin.getOpenInventory().getTopInventory().getHolder()).page());
        click(49);
        assertInstanceOf(SettingsMenuHolder.class, admin.getOpenInventory().getTopInventory().getHolder());
    }

    @Test void moduleTogglePreservesRulesAndReenableCorrectsExistingItems() {
        open();
        click(slot(Enchantment.SHARPNESS.getKey()));
        chat("3");
        click(45);
        ItemStack sword = new ItemStack(org.bukkit.Material.DIAMOND_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        admin.getInventory().setItem(0, sword);
        server.getScheduler().performTicks(21);
        assertEquals(5, admin.getInventory().getItem(0).getEnchantmentLevel(Enchantment.SHARPNESS));
        click(45);
        assertTrue(plugin.getConfig().getBoolean("enchantment-rules.enabled"));
        assertEquals(3, admin.getInventory().getItem(0).getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test void menuCannotBeLootedAndPermissionRevocationBlocksPendingEdits() {
        open();
        click(0, ClickType.SHIFT_LEFT);
        admin.setOp(false);
        chat("-1");
        assertFalse(EnchantmentSettings.fromConfig(plugin.getConfig(), Logger.getAnonymousLogger()).active());
        admin.setOp(true);
        open();
        var drag = new InventoryDragEvent(admin.getOpenInventory(), new ItemStack(org.bukkit.Material.AIR),
            new ItemStack(org.bukkit.Material.STONE), false, java.util.Map.of(0, new ItemStack(org.bukkit.Material.STONE)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
        admin.setOp(false);
        click(45);
        assertTrue(plugin.getConfig().getBoolean("enchantment-rules.enabled"));
    }

    @Test void parsesOnlySupportedIntegerLimits() {
        for (int value : new int[]{-1, 0, 1, 3, 255}) assertEquals(value, SettingsGui.parseEnchantmentLimit(" " + value + " "));
        for (String value : new String[]{"-2", "256", "3.5", "NaN", "Infinity", "9999999999999999", "ban"})
            assertNull(SettingsGui.parseEnchantmentLimit(value));
    }
}
