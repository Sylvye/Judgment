package com.bountysmp.judgment.enchantment;

import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import io.papermc.paper.event.player.PlayerPurchaseEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.event.enchantment.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.inventory.view.EnchantmentView;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EnchantmentControllerTest {
    ServerMock server;
    PlayerMock player;
    EnchantmentController controller;
    AtomicReference<EnchantmentSettings> settings;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        settings = new AtomicReference<>(new EnchantmentSettings(true,
            Map.of(Enchantment.SHARPNESS.getKey(), 3, Enchantment.WIND_BURST.getKey(), -1)));
        var plugin = MockBukkit.createMockPlugin();
        controller = new EnchantmentController(plugin, settings::get);
        server.getPluginManager().registerEvents(controller, plugin);
    }

    @AfterEach void cleanup() { controller.close(); MockBukkit.unmock(); }

    // MockBukkit has no typed anvil/table views; supply the public view interface.
    private <T extends InventoryView> T view(Class<T> type, Inventory top) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> switch (method.getName()) {
            case "getTopInventory" -> top;
            case "getBottomInventory" -> player.getInventory();
            case "getPlayer" -> player;
            case "getType" -> top.getType();
            case "getCursor" -> player.getItemOnCursor();
            case "convertSlot" -> (int) args[0] < top.getSize() ? args[0] : (int) args[0] - top.getSize();
            case "getItem" -> top.getItem((int) args[0]);
            case "setItem" -> { top.setItem((int) args[0], (ItemStack) args[1]); yield null; }
            default -> throw new UnsupportedOperationException(method.getName());
        }));
    }

    @Test void tableOffersAndActualAdditionsAreFilteredIndependently() {
        Inventory table = Bukkit.createInventory(null, InventoryType.ENCHANTING);
        var view = view(EnchantmentView.class, table);
        var offers = new EnchantmentOffer[]{new EnchantmentOffer(Enchantment.WIND_BURST, 3, 30),
            new EnchantmentOffer(Enchantment.SHARPNESS, 5, 20), new EnchantmentOffer(Enchantment.UNBREAKING, 3, 10)};
        var prepare = new PrepareItemEnchantEvent(player, view, player.getLocation().getBlock(),
            new ItemStack(Material.DIAMOND_SWORD), offers, 15);
        server.getPluginManager().callEvent(prepare);
        assertNull(offers[0]);
        assertEquals(3, offers[1].getEnchantmentLevel());
        assertEquals(20, offers[1].getCost());
        assertEquals(3, offers[2].getEnchantmentLevel());

        Map<Enchantment, Integer> additions = new HashMap<>(Map.of(Enchantment.SHARPNESS, 5,
            Enchantment.WIND_BURST, 3, Enchantment.UNBREAKING, 2));
        var enchant = new EnchantItemEvent(player, view, player.getLocation().getBlock(),
            new ItemStack(Material.DIAMOND_SWORD), 30, additions, Enchantment.SHARPNESS, 5, 2);
        server.getPluginManager().callEvent(enchant);
        assertFalse(enchant.isCancelled());
        assertEquals(Map.of(Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 2), enchant.getEnchantsToAdd());
        assertEquals(30, enchant.getExpLevelCost());
    }

    @Test void tableWithOnlyBannedEnchantmentsCancelsWithoutTouchingResources() {
        Inventory table = Bukkit.createInventory(null, InventoryType.ENCHANTING);
        table.setItem(1, new ItemStack(Material.LAPIS_LAZULI, 12));
        player.setLevel(40);
        var event = new EnchantItemEvent(player, view(EnchantmentView.class, table), player.getLocation().getBlock(),
            new ItemStack(Material.MACE), 30, new HashMap<>(Map.of(Enchantment.WIND_BURST, 3)), Enchantment.WIND_BURST, 3, 2);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertEquals(40, player.getLevel());
        assertEquals(12, table.getItem(1).getAmount());
    }

    @Test void bookTableOffersAndEnchantingRemainUnrestricted() {
        Inventory table = Bukkit.createInventory(null, InventoryType.ENCHANTING);
        var view = view(EnchantmentView.class, table);
        for (Material material : List.of(Material.BOOK, Material.ENCHANTED_BOOK)) {
            var offers = new EnchantmentOffer[]{new EnchantmentOffer(Enchantment.WIND_BURST, 3, 30),
                new EnchantmentOffer(Enchantment.SHARPNESS, 5, 20), null};
            var prepare = new PrepareItemEnchantEvent(player, view, player.getLocation().getBlock(),
                new ItemStack(material), offers, 15);
            server.getPluginManager().callEvent(prepare);
            assertNotNull(offers[0]);
            assertEquals(3, offers[0].getEnchantmentLevel());
            assertEquals(5, offers[1].getEnchantmentLevel());
            var additions = Map.of(Enchantment.WIND_BURST, 3, Enchantment.SHARPNESS, 5);
            var enchant = new EnchantItemEvent(player, view, player.getLocation().getBlock(),
                new ItemStack(material), 30, new HashMap<>(additions), Enchantment.SHARPNESS, 5, 2);
            server.getPluginManager().callEvent(enchant);
            assertFalse(enchant.isCancelled());
            assertEquals(additions, enchant.getEnchantsToAdd());
        }
    }

    @Test void bookToBookCombiningPreservesBannedAndOverCapEnchantments() {
        AnvilInventory inventory = (AnvilInventory) Bukkit.createInventory(null, InventoryType.ANVIL);
        ItemStack input = EnchantmentSanitizerTest.book();
        EnchantmentStorageMeta inputMeta = (EnchantmentStorageMeta) input.getItemMeta();
        inputMeta.addStoredEnchant(Enchantment.SHARPNESS, 3, true);
        input.setItemMeta(inputMeta);
        inventory.setItem(0, input);
        inventory.setItem(1, input.clone());
        ItemStack combined = input.clone();
        EnchantmentStorageMeta combinedMeta = (EnchantmentStorageMeta) combined.getItemMeta();
        combinedMeta.addStoredEnchant(Enchantment.SHARPNESS, 4, true);
        combined.setItemMeta(combinedMeta);
        var event = new PrepareAnvilEvent(view(AnvilView.class, inventory), combined);
        server.getPluginManager().callEvent(event);
        assertEquals(combined, event.getResult());
        assertEquals(input, inventory.getItem(0));
        assertEquals(input, inventory.getItem(1));
        inventory.setItem(2, event.getResult());
        var take = new InventoryClickEvent(view(InventoryView.class, inventory), InventoryType.SlotType.RESULT,
            2, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(take);
        assertFalse(take.isCancelled());
        assertEquals(combined, take.getCurrentItem());
    }

    @Test void bannedOnlyBookApplicationToGearIsBlockedWithoutEditingTheBook() {
        AnvilInventory inventory = (AnvilInventory) Bukkit.createInventory(null, InventoryType.ANVIL);
        ItemStack input = new ItemStack(Material.MACE);
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(Enchantment.WIND_BURST, 3, true);
        book.setItemMeta(meta);
        inventory.setItem(0, input);
        inventory.setItem(1, book);
        ItemStack result = input.clone();
        result.addUnsafeEnchantment(Enchantment.WIND_BURST, 3);
        var event = new PrepareAnvilEvent(view(AnvilView.class, inventory), result);
        server.getPluginManager().callEvent(event);
        assertNull(event.getResult());
        assertEquals(input, inventory.getItem(0));
        assertEquals(book, inventory.getItem(1));
    }

    @Test void anvilBookApplicationsCapAndRemoveEnchantmentsButKeepUsefulResults() {
        AnvilInventory inventory = (AnvilInventory) Bukkit.createInventory(null, InventoryType.ANVIL);
        ItemStack input = new ItemStack(Material.DIAMOND_SWORD);
        input.addUnsafeEnchantment(Enchantment.SHARPNESS, 1);
        inventory.setItem(0, input);
        inventory.setItem(1, EnchantmentSanitizerTest.book());
        var event = new PrepareAnvilEvent(view(AnvilView.class, inventory), EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        server.getPluginManager().callEvent(event);
        assertNotNull(event.getResult());
        assertEquals(3, event.getResult().getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(event.getResult().containsEnchantment(Enchantment.WIND_BURST));
        EnchantmentStorageMeta book = (EnchantmentStorageMeta) inventory.getItem(1).getItemMeta();
        assertEquals(5, book.getStoredEnchantLevel(Enchantment.SHARPNESS));
        assertTrue(book.hasStoredEnchant(Enchantment.WIND_BURST));
        assertEquals(EnchantmentSanitizerTest.book(), inventory.getItem(1));
    }

    @Test void anvilRejectsCappedMergingButAllowsRenaming() {
        AnvilInventory inventory = (AnvilInventory) Bukkit.createInventory(null, InventoryType.ANVIL);
        ItemStack input = new ItemStack(Material.DIAMOND_SWORD);
        input.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
        inventory.setItem(0, input);
        ItemStack merged = input.clone();
        merged.addUnsafeEnchantment(Enchantment.SHARPNESS, 4);
        var event = new PrepareAnvilEvent(view(AnvilView.class, inventory), merged);
        server.getPluginManager().callEvent(event);
        assertNull(event.getResult());
        var meta = merged.getItemMeta();
        meta.displayName(Component.text("Renamed"));
        merged.setItemMeta(meta);
        event = new PrepareAnvilEvent(view(AnvilView.class, inventory), merged);
        server.getPluginManager().callEvent(event);
        assertNotNull(event.getResult());
        assertEquals(Component.text("Renamed"), event.getResult().getItemMeta().displayName());
        assertEquals(3, event.getResult().getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test void smithingAndGrindstoneResultsAreSanitized() {
        Inventory smithing = Bukkit.createInventory(null, InventoryType.SMITHING);
        var smith = new PrepareSmithingEvent(view(InventoryView.class, smithing), EnchantmentSanitizerTest.gear(Material.NETHERITE_SWORD));
        server.getPluginManager().callEvent(smith);
        assertEquals(3, smith.getResult().getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(smith.getResult().containsEnchantment(Enchantment.WIND_BURST));
        Inventory grindstone = Bukkit.createInventory(null, InventoryType.GRINDSTONE);
        var grind = new PrepareGrindstoneEvent(view(InventoryView.class, grindstone), EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        server.getPluginManager().callEvent(grind);
        assertFalse(grind.getResult().containsEnchantment(Enchantment.WIND_BURST));
    }

    @Test void joinAndPeriodicChecksIncludeArmorOffhandCursorAndEnderChest() {
        player.getInventory().setItem(0, EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        player.getInventory().setHelmet(EnchantmentSanitizerTest.gear(Material.DIAMOND_HELMET));
        player.getInventory().setItemInOffHand(EnchantmentSanitizerTest.book());
        player.getEnderChest().setItem(0, EnchantmentSanitizerTest.book());
        player.setItemOnCursor(EnchantmentSanitizerTest.book());
        server.getPluginManager().callEvent(new PlayerJoinEvent(player, Component.empty()));
        assertEquals(3, player.getInventory().getItem(0).getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(player.getInventory().getHelmet().containsEnchantment(Enchantment.WIND_BURST));
        for (ItemStack book : List.of(player.getInventory().getItemInOffHand(), player.getEnderChest().getItem(0), player.getItemOnCursor()))
            assertEquals(EnchantmentSanitizerTest.book(), book);
        player.getInventory().setItem(1, EnchantmentSanitizerTest.gear(Material.MACE));
        server.getScheduler().performTicks(21);
        assertFalse(player.getInventory().getItem(1).containsEnchantment(Enchantment.WIND_BURST));
    }

    @Test void hopperCorrectionDoesNotDuplicateOrDiscardTheSource() {
        Inventory source = Bukkit.createInventory(null, 9), destination = Bukkit.createInventory(null, 9);
        ItemStack gear = EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD);
        gear.setAmount(4);
        source.setItem(0, gear);
        var event = new InventoryMoveItemEvent(source, gear, destination, true);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        server.getScheduler().performOneTick();
        assertEquals(4, source.getItem(0).getAmount());
        assertTrue(destination.isEmpty());
        assertEquals(3, source.getItem(0).getEnchantmentLevel(Enchantment.SHARPNESS));
        var retry = new InventoryMoveItemEvent(source, source.getItem(0), destination, true);
        server.getPluginManager().callEvent(retry);
        assertFalse(retry.isCancelled());
    }

    @Test void deferredCleanupRereadsSlotsRatherThanRestoringAnOldEventItem() {
        Inventory chest = Bukkit.createInventory(null, 27);
        player.openInventory(chest);
        chest.setItem(0, EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        var click = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 0,
            ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY);
        server.getPluginManager().callEvent(click);
        assertEquals(3, click.getCurrentItem().getEnchantmentLevel(Enchantment.SHARPNESS));
        chest.setItem(0, new ItemStack(Material.DIAMOND, 7));
        server.getScheduler().performOneTick();
        assertEquals(Material.DIAMOND, chest.getItem(0).getType());
        assertEquals(7, chest.getItem(0).getAmount());
    }

    @Test void creativeAndDragInputsCannotIntroduceIllegalGear() {
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        player.openInventory(Bukkit.createInventory(null, 27));
        var creative = new InventoryCreativeEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 27, EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        server.getPluginManager().callEvent(creative);
        assertFalse(creative.getCursor().containsEnchantment(Enchantment.WIND_BURST));
        ItemStack cursor = EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD);
        cursor.setAmount(4);
        player.setItemOnCursor(cursor);
        var drag = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.AIR), cursor, false,
            Map.of(36, EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD)));
        server.getPluginManager().callEvent(drag);
        assertTrue(drag.isCancelled());
        server.getScheduler().performOneTick();
        assertEquals(4, player.getItemOnCursor().getAmount());
        assertEquals(3, player.getItemOnCursor().getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test void booksStayUnchangedDuringStorageCreativeDragHopperAndPickupHandling() {
        Inventory chest = Bukkit.createInventory(null, 27);
        ItemStack book = EnchantmentSanitizerTest.book();
        book.setAmount(4);
        chest.setItem(0, book.clone());
        player.openInventory(chest);
        assertEquals(book, chest.getItem(0));
        var creative = new InventoryCreativeEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, 27, book.clone());
        server.getPluginManager().callEvent(creative);
        assertEquals(book, creative.getCursor());
        player.setItemOnCursor(book.clone());
        var drag = new InventoryDragEvent(player.getOpenInventory(), new ItemStack(Material.AIR), book.clone(), false,
            Map.of(1, EnchantmentSanitizerTest.book()));
        server.getPluginManager().callEvent(drag);
        assertFalse(drag.isCancelled());
        assertEquals(EnchantmentSanitizerTest.book(), drag.getNewItems().get(1));
        var move = new InventoryMoveItemEvent(chest, book.clone(), Bukkit.createInventory(null, 9), true);
        server.getPluginManager().callEvent(move);
        assertFalse(move.isCancelled());
        assertEquals(book, move.getItem());
        var item = player.getWorld().dropItem(player.getLocation(), book.clone());
        server.getPluginManager().callEvent(new ItemSpawnEvent(item));
        server.getPluginManager().callEvent(new EntityPickupItemEvent(player, item, 0));
        server.getScheduler().performTicks(21);
        assertEquals(book, item.getItemStack());
        assertEquals(book, chest.getItem(0));
        assertEquals(book, player.getItemOnCursor());
    }

    @Test void merchantRulesPreservePricesAndUsage() {
        Merchant merchant = (Merchant) Proxy.newProxyInstance(Merchant.class.getClassLoader(), new Class<?>[]{Merchant.class},
            (proxy, method, args) -> { throw new UnsupportedOperationException(method.getName()); });
        MerchantRecipe recipe = new MerchantRecipe(EnchantmentSanitizerTest.book(), 2, 12, true, 8, 0.2f, 4, -1, true);
        recipe.addIngredient(new ItemStack(Material.EMERALD, 9));
        var purchase = new PlayerPurchaseEvent(player, merchant, recipe, true, true);
        server.getPluginManager().callEvent(purchase);
        MerchantRecipe corrected = purchase.getTrade();
        assertEquals(5, ((EnchantmentStorageMeta) corrected.getResult().getItemMeta()).getStoredEnchantLevel(Enchantment.SHARPNESS));
        assertTrue(((EnchantmentStorageMeta) corrected.getResult().getItemMeta()).hasStoredEnchant(Enchantment.WIND_BURST));
        assertEquals(recipe.getResult(), corrected.getResult());
        assertEquals(recipe.getIngredients(), corrected.getIngredients());
        assertEquals(2, corrected.getUses());
        assertEquals(12, corrected.getMaxUses());
        assertEquals(4, corrected.getDemand());
        assertEquals(-1, corrected.getSpecialPrice());
        assertTrue(corrected.shouldIgnoreDiscounts());
        assertFalse(purchase.isCancelled());
    }

    @Test void slotEventsAreHandledWhileAChestViewIsOpen() {
        player.openInventory(Bukkit.createInventory(null, 54));
        player.getInventory().setItem(0, EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        var event = new PlayerInventorySlotChangeEvent(player, 81, new ItemStack(Material.AIR), player.getInventory().getItem(0));
        server.getPluginManager().callEvent(event);
        assertEquals(3, player.getInventory().getItem(0).getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test void chunkCleanupIsBoundedAndDeduplicated() {
        var world = new EnchantmentFixtures.StorageWorld();
        world.setName("storage");
        server.addWorld(world);
        var first = world.getChunkAt(5, 5);
        var second = world.getChunkAt(6, 5);
        var firstBlock = first.getBlock(0, 70, 0);
        firstBlock.setType(Material.CHEST);
        var secondBlock = second.getBlock(0, 70, 0);
        secondBlock.setType(Material.CHEST);
        ((org.bukkit.block.Container) firstBlock.getState()).getInventory().setItem(0, EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        ((org.bukkit.block.Container) firstBlock.getState()).getInventory().setItem(1, EnchantmentSanitizerTest.book());
        ((org.bukkit.block.Container) secondBlock.getState()).getInventory().setItem(0, EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        server.getPluginManager().callEvent(new ChunkLoadEvent(first, false));
        server.getPluginManager().callEvent(new ChunkLoadEvent(first, false));
        server.getPluginManager().callEvent(new ChunkLoadEvent(second, false));
        server.getScheduler().performOneTick();
        assertEquals(3, sharpness(firstBlock));
        assertEquals(5, sharpness(secondBlock));
        assertEquals(EnchantmentSanitizerTest.book(), ((org.bukkit.block.Container) firstBlock.getState()).getInventory().getItem(1));
        server.getScheduler().performOneTick();
        assertEquals(3, sharpness(secondBlock));
    }

    @Test void chunkCleanupDoesNotLoadChunksThatHaveUnloaded() {
        var world = new EnchantmentFixtures.StorageWorld();
        world.setName("unloading");
        server.addWorld(world);
        var chunk = world.getChunkAt(8, 8);
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));
        world.forgetChunk(8, 8);
        server.getScheduler().performOneTick();
        assertFalse(world.isChunkLoaded(8, 8));
    }

    private int sharpness(org.bukkit.block.Block block) {
        ItemStack item = ((org.bukkit.block.Container) block.getState()).getInventory().getItem(0);
        return item.getEnchantmentLevel(Enchantment.SHARPNESS);
    }

    @Test void disablingAndClosingStopEnforcement() {
        settings.set(settings.get().withEnabled(false));
        player.getInventory().setItem(0, EnchantmentSanitizerTest.gear(Material.MACE));
        server.getScheduler().performTicks(21);
        assertEquals(5, player.getInventory().getItem(0).getEnchantmentLevel(Enchantment.SHARPNESS));
        controller.close();
        settings.set(settings.get().withEnabled(true));
        server.getScheduler().performTicks(21);
        assertEquals(5, player.getInventory().getItem(0).getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test void lootFilteringPreservesQuantitiesAndDoesNotMutateLootTableTemplates() {
        ItemStack template = EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD);
        template.setAmount(6);
        var event = new LootGenerateEvent(player.getWorld(), player, null, null,
            new org.bukkit.loot.LootContext.Builder(player.getLocation()).build(), new ArrayList<>(List.of(template, EnchantmentSanitizerTest.book())), false);
        server.getPluginManager().callEvent(event);
        var result = event.getLoot().getFirst();
        assertEquals(6, result.getAmount());
        assertEquals(3, result.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(result.containsEnchantment(Enchantment.WIND_BURST));
        assertEquals(5, template.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertEquals(EnchantmentSanitizerTest.book(), event.getLoot().get(1));
    }

    @Test void craftingAndAutomaticCraftingResultsAreCorrected() {
        CraftingInventory inventory = (CraftingInventory) Bukkit.createInventory(null, InventoryType.WORKBENCH);
        inventory.setResult(EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        server.getPluginManager().callEvent(new PrepareItemCraftEvent(inventory, view(InventoryView.class, inventory), false));
        assertEquals(3, inventory.getResult().getEnchantmentLevel(Enchantment.SHARPNESS));
        var recipe = new org.bukkit.inventory.ShapelessRecipe(new org.bukkit.NamespacedKey("test", "crafted"),
            new ItemStack(Material.DIAMOND_SWORD)).addIngredient(Material.DIAMOND);
        var event = new CrafterCraftEvent(player.getLocation().getBlock(), recipe, EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        server.getPluginManager().callEvent(event);
        assertEquals(3, event.getResult().getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(event.getResult().containsEnchantment(Enchantment.WIND_BURST));
    }

    @Test void spawnedAndPickedUpItemsAreCorrectedWithoutChangingQuantity() {
        settings.set(settings.get().withEnabled(false));
        var gear = EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD);
        gear.setAmount(4);
        var item = player.getWorld().dropItem(player.getLocation(), gear);
        settings.set(settings.get().withEnabled(true));
        server.getPluginManager().callEvent(new ItemSpawnEvent(item));
        assertEquals(4, item.getItemStack().getAmount());
        assertEquals(3, item.getItemStack().getEnchantmentLevel(Enchantment.SHARPNESS));
        item.setItemStack(EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        var event = new EntityPickupItemEvent(player, item, 0);
        server.getPluginManager().callEvent(event);
        assertFalse(event.isCancelled());
        assertFalse(item.getItemStack().containsEnchantment(Enchantment.WIND_BURST));
    }

    @Test void itemsAreSanitizedBeforeInteractionAndMeleeAttack() {
        player.getInventory().setItemInMainHand(EnchantmentSanitizerTest.gear(Material.MACE));
        var use = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, player.getInventory().getItemInMainHand(),
            null, org.bukkit.block.BlockFace.SELF);
        server.getPluginManager().callEvent(use);
        assertFalse(player.getInventory().getItemInMainHand().containsEnchantment(Enchantment.WIND_BURST));
        player.getInventory().setItemInMainHand(EnchantmentSanitizerTest.gear(Material.DIAMOND_SWORD));
        var target = server.addPlayer();
        target.getInventory().setHelmet(EnchantmentSanitizerTest.gear(Material.DIAMOND_HELMET));
        server.getPluginManager().callEvent(new io.papermc.paper.event.player.PrePlayerAttackEntityEvent(player, target, true));
        assertEquals(3, player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(target.getInventory().getHelmet().containsEnchantment(Enchantment.WIND_BURST));
    }

    @Test void numberKeyCannotTakeAnAnvilNoOpOrConsumeResources() {
        AnvilInventory inventory = (AnvilInventory) Bukkit.createInventory(null, InventoryType.ANVIL);
        ItemStack input = new ItemStack(Material.DIAMOND_SWORD);
        input.addUnsafeEnchantment(Enchantment.SHARPNESS, 3);
        inventory.setItem(0, input);
        var result = input.clone();
        result.addUnsafeEnchantment(Enchantment.SHARPNESS, 4);
        inventory.setItem(2, result);
        player.setLevel(40);
        var event = new InventoryClickEvent(view(InventoryView.class, inventory), InventoryType.SlotType.RESULT,
            2, ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 0);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertNull(inventory.getItem(2));
        assertEquals(input, inventory.getItem(0));
        assertEquals(40, player.getLevel());
    }

    @Test void unopenedChunkLootIsNotGeneratedDuringCleanup() {
        var world = new EnchantmentFixtures.StorageWorld();
        world.setName("unopenedloot");
        server.addWorld(world);
        var chunk = world.getChunkAt(9, 9);
        var lootTable = (org.bukkit.loot.LootTable) Proxy.newProxyInstance(org.bukkit.loot.LootTable.class.getClassLoader(),
            new Class<?>[]{org.bukkit.loot.LootTable.class}, (proxy, method, args) -> { throw new AssertionError("Loot was generated"); });
        var container = (org.bukkit.block.Container) Proxy.newProxyInstance(org.bukkit.block.Container.class.getClassLoader(),
            new Class<?>[]{org.bukkit.block.Container.class, org.bukkit.loot.Lootable.class}, (proxy, method, args) -> {
                if (method.getName().equals("getLootTable")) return lootTable;
                throw new AssertionError("Unopened loot inventory was accessed");
            });
        world.setTileEntities(9, 9, container);
        server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, false));
        assertDoesNotThrow(() -> server.getScheduler().performOneTick());
    }
}
