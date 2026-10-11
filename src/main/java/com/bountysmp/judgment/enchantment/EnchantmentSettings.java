package com.bountysmp.judgment.enchantment;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public record EnchantmentSettings(boolean enabled, Map<NamespacedKey, Integer> limits) {
    public static final String CONFIG_ROOT = "enchantment-rules";

    public EnchantmentSettings {
        limits = Map.copyOf(limits);
        if (limits.values().stream().anyMatch(value -> !valid(value)))
            throw new IllegalArgumentException("Enchantment rule must be -1, 0, or 1 through 255");
    }

    public static EnchantmentSettings fromConfig(ConfigurationSection config, Logger logger) {
        Map<NamespacedKey, Integer> limits = new HashMap<>();
        var registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        configuredLimits(config).forEach((id, raw) -> {
            NamespacedKey key = NamespacedKey.fromString(id);
            if (key == null || registry.get(key) == null || !(raw instanceof Number number)
                || !Double.isFinite(number.doubleValue()) || number.doubleValue() != number.intValue()
                || !valid(number.intValue())) {
                logger.warning("Invalid enchantment rule " + id + "; ignoring it.");
            } else limits.put(key, number.intValue());
        });
        return new EnchantmentSettings(config.getBoolean(CONFIG_ROOT + ".enabled", true), limits);
    }

    /** Bukkit treats dots in custom IDs as path separators when loading YAML. */
    public static Map<String, Object> configuredLimits(ConfigurationSection config) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        flattenLimits("", config.get(CONFIG_ROOT + ".limits"), result);
        return result;
    }

    private static void flattenLimits(String prefix, Object value, Map<String, Object> result) {
        Map<?, ?> entries = value instanceof ConfigurationSection section ? section.getValues(false)
            : value instanceof Map<?, ?> map ? map : null;
        if (entries == null) {
            if (!prefix.isEmpty()) result.put(prefix, value);
            return;
        }
        entries.forEach((key, child) -> flattenLimits(prefix.isEmpty() ? key.toString() : prefix + "." + key, child, result));
    }

    public static boolean valid(int value) { return value >= -1 && value <= 255; }
    public int limit(Enchantment enchantment) { return limits.getOrDefault(enchantment.getKey(), 0); }
    public boolean active() { return enabled && limits.values().stream().anyMatch(value -> value != 0); }
    public int allowedLevel(Enchantment enchantment, int level) {
        if (!enabled) return level;
        int limit = limit(enchantment);
        return limit == -1 ? 0 : limit == 0 ? level : Math.min(level, limit);
    }

    public EnchantmentSettings with(NamespacedKey enchantment, int limit) {
        Map<NamespacedKey, Integer> updated = new HashMap<>(limits);
        updated.put(enchantment, limit);
        return new EnchantmentSettings(enabled, updated);
    }

    public EnchantmentSettings withEnabled(boolean value) { return new EnchantmentSettings(value, limits); }
}
