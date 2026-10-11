package com.bountysmp.judgment.enchantment;

import org.bukkit.block.Container;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.Supplier;

/** Changes enchantments only; callers must write changed stack copies back to their source. */
public final class EnchantmentSanitizer {
    private final Supplier<EnchantmentSettings> settings;

    public EnchantmentSanitizer(Supplier<EnchantmentSettings> settings) { this.settings = settings; }
    public boolean active() { return settings.get().active(); }

    public static boolean isBook(ItemStack item) {
        return item != null && (item.getType() == Material.BOOK || item.getType() == Material.ENCHANTED_BOOK);
    }

    public boolean sanitize(ItemStack item) {
        if (!active() || item == null || item.getType().isAir() || isBook(item) || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        boolean changed = sanitizeEnchants(meta);
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            var contents = new ArrayList<>(bundle.getItems());
            boolean nestedChanged = false;
            for (ItemStack nested : contents) nestedChanged |= sanitize(nested);
            if (nestedChanged) { bundle.setItems(contents); changed = true; }
        }
        if (meta instanceof BlockStateMeta blockMeta && blockMeta.hasBlockState()
            && blockMeta.getBlockState() instanceof Container container) {
            if (sanitizeInventory(container.getSnapshotInventory())) {
                blockMeta.setBlockState(container);
                changed = true;
            }
        }
        if (changed) item.setItemMeta(meta);
        return changed;
    }

    private boolean sanitizeEnchants(ItemMeta meta) {
        Map<Enchantment, Integer> enchants = meta.getEnchants();
        boolean changed = false;
        for (var entry : enchants.entrySet()) {
            int level = settings.get().allowedLevel(entry.getKey(), entry.getValue());
            if (level == entry.getValue() && settings.get().limit(entry.getKey()) != -1) continue;
            meta.removeEnchant(entry.getKey());
            if (level > 0) meta.addEnchant(entry.getKey(), level, true);
            changed = true;
        }
        return changed;
    }

    public boolean sanitizeInventory(Inventory inventory) {
        if (!active()) return false;
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (sanitize(item)) { inventory.setItem(slot, item); changed = true; }
        }
        return changed;
    }

    /** Anvil prior-work penalties do not make an otherwise unchanged operation useful. */
    public static boolean equivalentAnvilResult(ItemStack input, ItemStack result) {
        if (input == null || result == null) return false;
        ItemStack left = input.clone(), right = result.clone();
        clearRepairCost(left);
        clearRepairCost(right);
        return left.isSimilar(right);
    }

    private static void clearRepairCost(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Repairable repairable) {
            repairable.setRepairCost(0);
            item.setItemMeta(meta);
        }
    }
}
