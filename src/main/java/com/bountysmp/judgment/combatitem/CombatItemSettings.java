package com.bountysmp.judgment.combatitem;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

public final class CombatItemSettings {
    public static final String CONFIG_ROOT = "combat-item-cooldowns";
    private final Map<CombatItemAction, Double> seconds;

    public CombatItemSettings(Map<CombatItemAction, Double> seconds) {
        EnumMap<CombatItemAction, Double> copy = new EnumMap<>(CombatItemAction.class);
        for (CombatItemAction action : CombatItemAction.values()) copy.put(action, seconds.getOrDefault(action, 0.0));
        this.seconds = Map.copyOf(copy);
    }

    public static CombatItemSettings defaults() { return new CombatItemSettings(Map.of()); }

    public static CombatItemSettings fromConfig(FileConfiguration config, Logger logger) {
        EnumMap<CombatItemAction, Double> values = new EnumMap<>(CombatItemAction.class);
        for (CombatItemAction action : CombatItemAction.values()) {
            String path = CONFIG_ROOT + "." + action.configKey();
            Object raw = config.get(path);
            double value = raw instanceof Number number ? number.doubleValue() : 0.0;
            if (raw != null && (!(raw instanceof Number) || !valid(value))) {
                logger.warning("Invalid " + path + "; using 0 seconds.");
                value = 0.0;
            }
            values.put(action, value);
        }
        return new CombatItemSettings(values);
    }

    public double seconds(CombatItemAction action) { return seconds.get(action); }

    public CombatItemSettings with(CombatItemAction action, double value) {
        if (!valid(value)) throw new IllegalArgumentException("Cooldown must be -1 or a finite nonnegative number");
        EnumMap<CombatItemAction, Double> updated = new EnumMap<>(seconds);
        updated.put(action, value);
        return new CombatItemSettings(updated);
    }

    public static boolean valid(double value) {
        return Double.isFinite(value) && (value == -1.0 || value >= 0.0);
    }
}
