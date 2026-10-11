package com.bountysmp.judgment.gui;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.List;

public final class EnchantmentMenuHolder implements InventoryHolder {
    private Inventory inventory;
    private final int page;
    private final List<NamespacedKey> enchantments;

    public EnchantmentMenuHolder(int page, List<NamespacedKey> enchantments) {
        this.page = page;
        this.enchantments = List.copyOf(enchantments);
    }

    void setInventory(Inventory inventory) { this.inventory = inventory; }
    public int page() { return page; }
    public NamespacedKey enchantmentAt(int slot) {
        return slot >= 0 && slot < enchantments.size() ? enchantments.get(slot) : null;
    }
    @Override public Inventory getInventory() { return inventory; }
}
