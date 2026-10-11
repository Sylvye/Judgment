package com.bountysmp.judgment.enchantment;

import me.easyenchants.enchant.EnchantedBookApplicator;
import me.easyenchants.listener.EasyEnchantsListener;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Runs both real listeners, with assertions before any scheduled cleanup. */
class EasyEnchantsIntegrationTest {
    ServerMock server;
    PlayerMock player;
    AtomicReference<EnchantmentSettings> settings;
    List<ItemStack> writes;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        settings = new AtomicReference<>(new EnchantmentSettings(true,
            Map.of(Enchantment.SHARPNESS.getKey(), 3, Enchantment.WIND_BURST.getKey(), -1)));
        writes = new ArrayList<>();
        var plugin = MockBukkit.createMockPlugin();
        server.getPluginManager().registerEvents(new EasyEnchantsListener(() -> true, null, new EnchantedBookApplicator()), plugin);
        server.getPluginManager().registerEvents(new EasyEnchantsIntegration(settings::get), plugin);
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    private InventoryClickEvent click(ItemStack target, ItemStack book) {
        Inventory top = Bukkit.createInventory(null, 27);
        player.getInventory().setItem(0, target);
        player.setItemOnCursor(book);
        InventoryView view = (InventoryView) Proxy.newProxyInstance(InventoryView.class.getClassLoader(),
            new Class<?>[]{InventoryView.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getTopInventory" -> top;
                case "getBottomInventory" -> player.getInventory();
                case "getPlayer" -> player;
                case "getType" -> InventoryType.CHEST;
                case "getInventory" -> (int) args[0] < 27 ? top : player.getInventory();
                case "convertSlot" -> (int) args[0] < 27 ? args[0] : (int) args[0] - 27;
                case "getItem" -> player.getInventory().getItem((int) args[0] - 27);
                case "setItem" -> {
                    ItemStack item = (ItemStack) args[1];
                    writes.add(item.clone());
                    player.getInventory().setItem((int) args[0] - 27, item);
                    yield null;
                }
                case "getCursor" -> player.getItemOnCursor();
                case "setCursor" -> { player.setItemOnCursor((ItemStack) args[0]); yield null; }
                default -> throw new UnsupportedOperationException(method.getName());
            });
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 27,
            ClickType.LEFT, InventoryAction.SWAP_WITH_CURSOR);
    }

    private ItemStack book(int amount, Map<Enchantment, Integer> enchants) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, amount);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        enchants.forEach((enchantment, level) -> meta.addStoredEnchant(enchantment, level, true));
        book.setItemMeta(meta);
        return book;
    }

    @Test void capsBeforeAnyGearWriteAndPreservesBookAndMetadata() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        Damageable meta = (Damageable) sword.getItemMeta();
        meta.displayName(Component.text("Named sword"));
        meta.lore(List.of(Component.text("Keep this lore")));
        meta.setDamage(23);
        sword.setItemMeta(meta);
        ItemStack books = book(2, Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3));
        ItemStack expectedBook = books.clone();
        expectedBook.setAmount(1);
        var event = click(sword, books);
        server.getPluginManager().callEvent(event);
        assertEquals(3, event.getCurrentItem().getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(3, event.getCurrentItem().getEnchantmentLevel(Enchantment.UNBREAKING));
        assertEquals(expectedBook, event.getCursor());
        assertEquals(meta.displayName(), event.getCurrentItem().getItemMeta().displayName());
        assertEquals(meta.lore(), event.getCurrentItem().getItemMeta().lore());
        assertEquals(23, ((Damageable) event.getCurrentItem().getItemMeta()).getDamage());
        assertEquals(1, writes.size());
        assertEquals(3, writes.getFirst().getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test void bannedOnlyBookNeverWritesGearOrConsumesBook() {
        ItemStack books = book(2, Map.of(Enchantment.WIND_BURST, 3));
        ItemStack mace = new ItemStack(Material.MACE);
        var event = click(mace, books);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertEquals(mace, event.getCurrentItem());
        assertEquals(books, event.getCursor());
        assertTrue(writes.isEmpty());
    }

    @Test void mixedBookAppliesAllowedEntriesWithoutEverWritingBannedOnes() {
        var event = click(new ItemStack(Material.MACE), book(2,
            Map.of(Enchantment.WIND_BURST, 3, Enchantment.UNBREAKING, 3)));
        server.getPluginManager().callEvent(event);
        assertEquals(3, event.getCurrentItem().getEnchantmentLevel(Enchantment.UNBREAKING));
        assertEquals(1, event.getCursor().getAmount());
        assertEquals(3, ((EnchantmentStorageMeta) event.getCursor().getItemMeta()).getStoredEnchantLevel(Enchantment.WIND_BURST));
        assertTrue(writes.stream().noneMatch(item -> item.containsEnchantment(Enchantment.WIND_BURST)));
    }

    @Test void equalLevelsMergeUpToCap() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 2);
        var event = click(sword, book(1, Map.of(Enchantment.SHARPNESS, 2)));
        server.getPluginManager().callEvent(event);
        assertEquals(3, event.getCurrentItem().getEnchantmentLevel(Enchantment.SHARPNESS));
        assertTrue(event.getCursor() == null || event.getCursor().getType().isAir());
    }

    @Test void mergingAtCapDoesNotConsumeBookOrWriteGear() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 3);
        ItemStack books = book(2, Map.of(Enchantment.SHARPNESS, 3));
        var event = click(sword, books);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertEquals(sword, event.getCurrentItem());
        assertEquals(books, event.getCursor());
        assertTrue(writes.isEmpty());
    }

    @Test void correctsExistingIllegalGearWithoutConsumingNoOpBook() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 5);
        ItemStack books = book(2, Map.of(Enchantment.SHARPNESS, 5));
        var event = click(sword, books);
        server.getPluginManager().callEvent(event);
        assertEquals(3, event.getCurrentItem().getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(books, event.getCursor());
        assertEquals(1, writes.size());
    }

    @Test void removesExistingBannedConflictBeforeApplyingAllowedEnchant() {
        settings.set(settings.get().with(Enchantment.SHARPNESS.getKey(), -1));
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 3);
        var event = click(sword, book(1, Map.of(Enchantment.SHARPNESS, 5, Enchantment.SMITE, 4)));
        server.getPluginManager().callEvent(event);
        assertEquals(4, event.getCurrentItem().getEnchantmentLevel(Enchantment.SMITE));
        assertFalse(event.getCurrentItem().containsEnchantment(Enchantment.SHARPNESS));
    }

    @Test void bookCombiningRemainsUnrestricted() {
        var event = click(book(1, Map.of(Enchantment.SHARPNESS, 3, Enchantment.WIND_BURST, 2)),
            book(2, Map.of(Enchantment.SHARPNESS, 3, Enchantment.WIND_BURST, 2)));
        server.getPluginManager().callEvent(event);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) event.getCurrentItem().getItemMeta();
        assertEquals(4, meta.getStoredEnchantLevel(Enchantment.SHARPNESS));
        assertEquals(3, meta.getStoredEnchantLevel(Enchantment.WIND_BURST));
        assertEquals(1, event.getCursor().getAmount());
    }

    @Test void disabledAndUnrestrictedModulesAllowVanillaMaximum() {
        settings.set(settings.get().withEnabled(false));
        var disabled = click(new ItemStack(Material.DIAMOND_SWORD), book(1, Map.of(Enchantment.SHARPNESS, 5)));
        server.getPluginManager().callEvent(disabled);
        assertEquals(5, disabled.getCurrentItem().getEnchantmentLevel(Enchantment.SHARPNESS));
        settings.set(new EnchantmentSettings(true, Map.of()));
        var unrestricted = click(new ItemStack(Material.MACE), book(1, Map.of(Enchantment.WIND_BURST, 3)));
        server.getPluginManager().callEvent(unrestricted);
        assertEquals(3, unrestricted.getCurrentItem().getEnchantmentLevel(Enchantment.WIND_BURST));
    }

    @Test void ruleChangesTakeEffectOnNextClick() {
        settings.set(settings.get().with(Enchantment.SHARPNESS.getKey(), 1));
        var event = click(new ItemStack(Material.DIAMOND_SWORD), book(1, Map.of(Enchantment.SHARPNESS, 5)));
        server.getPluginManager().callEvent(event);
        assertEquals(1, event.getCurrentItem().getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test void previouslyCancelledClicksRemainUntouched() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        ItemStack books = book(2, Map.of(Enchantment.SHARPNESS, 5));
        var event = click(sword, books);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertEquals(sword, event.getCurrentItem());
        assertEquals(books, event.getCursor());
        assertTrue(writes.isEmpty());
    }
}
