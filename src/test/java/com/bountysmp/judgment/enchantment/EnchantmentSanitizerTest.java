package com.bountysmp.judgment.enchantment;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EnchantmentSanitizerTest {
    AtomicReference<EnchantmentSettings> settings;
    EnchantmentSanitizer sanitizer;

    @BeforeEach void setup() {
        MockBukkit.mock();
        settings = new AtomicReference<>(new EnchantmentSettings(true,
            Map.of(Enchantment.SHARPNESS.getKey(), 3, Enchantment.WIND_BURST.getKey(), -1)));
        sanitizer = new EnchantmentSanitizer(settings::get);
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    static ItemStack gear(Material material) {
        ItemStack item = new ItemStack(material);
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        item.addUnsafeEnchantment(Enchantment.WIND_BURST, 3);
        item.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        return item;
    }

    static ItemStack book() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(Enchantment.SHARPNESS, 5, true);
        meta.addStoredEnchant(Enchantment.WIND_BURST, 3, true);
        meta.addStoredEnchant(Enchantment.MENDING, 1, true);
        item.setItemMeta(meta);
        return item;
    }

    @Test void correctsGearWithoutChangingOtherMetadataAndIsIdempotent() {
        ItemStack item = gear(Material.DIAMOND_SWORD);
        Damageable meta = (Damageable) item.getItemMeta();
        meta.setDamage(29);
        meta.displayName(Component.text("Keepsake"));
        meta.lore(List.of(Component.text("Original lore")));
        meta.setUnbreakable(true);
        var tag = new NamespacedKey("test", "owner");
        meta.getPersistentDataContainer().set(tag, org.bukkit.persistence.PersistentDataType.STRING, "Alice");
        item.setItemMeta(meta);
        assertTrue(sanitizer.sanitize(item));
        assertEquals(3, item.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(item.containsEnchantment(Enchantment.WIND_BURST));
        assertEquals(3, item.getEnchantmentLevel(Enchantment.UNBREAKING));
        Damageable corrected = (Damageable) item.getItemMeta();
        assertEquals(29, corrected.getDamage());
        assertEquals(meta.displayName(), corrected.displayName());
        assertEquals(meta.lore(), corrected.lore());
        assertTrue(corrected.isUnbreakable());
        assertEquals("Alice", corrected.getPersistentDataContainer().get(tag, org.bukkit.persistence.PersistentDataType.STRING));
        assertFalse(sanitizer.sanitize(item));
    }

    @Test void leavesBothStoredAndAppliedBookEnchantmentsUnchanged() {
        ItemStack item = book();
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        item.setAmount(4);
        ItemStack original = item.clone();
        assertFalse(sanitizer.sanitize(item));
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        assertEquals(5, meta.getStoredEnchantLevel(Enchantment.SHARPNESS));
        assertTrue(meta.hasStoredEnchant(Enchantment.WIND_BURST));
        assertEquals(1, meta.getStoredEnchantLevel(Enchantment.MENDING));
        assertEquals(5, meta.getEnchantLevel(Enchantment.SHARPNESS));
        assertEquals(4, item.getAmount());
        assertEquals(original, item);
    }

    @Test void leavesEmptyEnchantedBooksIntactAndNeverRaisesLevels() {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(Enchantment.WIND_BURST, 1, true);
        item.setItemMeta(meta);
        sanitizer.sanitize(item);
        assertEquals(Material.ENCHANTED_BOOK, item.getType());
        assertTrue(((EnchantmentStorageMeta) item.getItemMeta()).hasStoredEnchant(Enchantment.WIND_BURST));
        item.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
        assertFalse(sanitizer.sanitize(item));
        assertEquals(2, item.getEnchantmentLevel(Enchantment.SHARPNESS));
        ItemStack gear = new ItemStack(Material.DIAMOND_SWORD);
        gear.addUnsafeEnchantment(Enchantment.SHARPNESS, 2);
        assertFalse(sanitizer.sanitize(gear));
        assertEquals(2, gear.getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test void recursivelyWritesBundleAndShulkerContentsBack() {
        ItemStack shulker = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta shulkerMeta = (BlockStateMeta) shulker.getItemMeta();
        ShulkerBox box = (ShulkerBox) shulkerMeta.getBlockState();
        box.getSnapshotInventory().setItem(0, book());
        box.getSnapshotInventory().setItem(1, gear(Material.DIAMOND_SWORD));
        shulkerMeta.setBlockState(box);
        shulker.setItemMeta(shulkerMeta);
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta bundleMeta = (BundleMeta) bundle.getItemMeta();
        bundleMeta.setItems(List.of(shulker));
        bundle.setItemMeta(bundleMeta);
        assertTrue(sanitizer.sanitize(bundle));
        ItemStack correctedShulker = ((BundleMeta) bundle.getItemMeta()).getItems().getFirst();
        ShulkerBox correctedBox = (ShulkerBox) ((BlockStateMeta) correctedShulker.getItemMeta()).getBlockState();
        EnchantmentStorageMeta corrected = (EnchantmentStorageMeta) correctedBox.getSnapshotInventory().getItem(0).getItemMeta();
        assertEquals(5, corrected.getStoredEnchantLevel(Enchantment.SHARPNESS));
        assertTrue(corrected.hasStoredEnchant(Enchantment.WIND_BURST));
        assertEquals(book(), correctedBox.getSnapshotInventory().getItem(0));
        ItemStack correctedGear = correctedBox.getSnapshotInventory().getItem(1);
        assertEquals(3, correctedGear.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(correctedGear.containsEnchantment(Enchantment.WIND_BURST));
        assertFalse(sanitizer.sanitize(bundle));
    }

    @Test void disablingOrRemovingRulesDoesNotRestoreOrChangeItems() {
        ItemStack item = gear(Material.MACE);
        settings.set(settings.get().withEnabled(false));
        assertFalse(sanitizer.sanitize(item));
        assertEquals(5, item.getEnchantmentLevel(Enchantment.SHARPNESS));
        settings.set(settings.get().withEnabled(true));
        sanitizer.sanitize(item);
        settings.set(new EnchantmentSettings(true, Map.of()));
        assertFalse(sanitizer.sanitize(item));
        assertEquals(3, item.getEnchantmentLevel(Enchantment.SHARPNESS));
        assertFalse(item.containsEnchantment(Enchantment.WIND_BURST));
    }

    @Test void anvilNoOpIgnoresPriorWorkButPreservesRenameAndRepair() {
        ItemStack input = gear(Material.DIAMOND_SWORD);
        sanitizer.sanitize(input);
        ItemStack result = input.clone();
        Repairable meta = (Repairable) result.getItemMeta();
        meta.setRepairCost(7);
        result.setItemMeta(meta);
        assertTrue(EnchantmentSanitizer.equivalentAnvilResult(input, result));
        meta.displayName(Component.text("Renamed"));
        result.setItemMeta(meta);
        assertFalse(EnchantmentSanitizer.equivalentAnvilResult(input, result));
        result = input.clone();
        Damageable damage = (Damageable) result.getItemMeta();
        damage.setDamage(10);
        result.setItemMeta(damage);
        assertFalse(EnchantmentSanitizer.equivalentAnvilResult(input, result));
    }
}
