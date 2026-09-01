package com.bountysmp.judgment.combatitem;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

public final class CombatItemSettings {
    public static final String CONFIG_ROOT = "combat-item-cooldowns";
    public static final String SCOPE_CONFIG_ROOT = "combat-item-scopes";
    private final Map<CombatItemAction, CombatItemRule> rules;

    public CombatItemSettings(Map<CombatItemAction, Double> seconds) {
        this(seconds, Map.of());
    }

    public CombatItemSettings(Map<CombatItemAction, Double> seconds,
                              Map<CombatItemAction, CombatItemScope> scopes) {
        EnumMap<CombatItemAction, CombatItemRule> copy = new EnumMap<>(CombatItemAction.class);
        for (CombatItemAction action : CombatItemAction.values())
            copy.put(action, new CombatItemRule(seconds.getOrDefault(action, 0.0),
                scopes.getOrDefault(action, CombatItemScope.PVP_ONLY)));
        this.rules = Map.copyOf(copy);
    }

    public static CombatItemSettings defaults() { return new CombatItemSettings(Map.of()); }

    public static CombatItemSettings fromConfig(FileConfiguration config, Logger logger) {
        EnumMap<CombatItemAction, Double> values = new EnumMap<>(CombatItemAction.class);
        EnumMap<CombatItemAction, CombatItemScope> scopes = new EnumMap<>(CombatItemAction.class);
        for (CombatItemAction action : CombatItemAction.values()) {
            String path = CONFIG_ROOT + "." + action.configKey();
            Object raw = config.get(path);
            double value = raw instanceof Number number ? number.doubleValue() : 0.0;
            if (raw != null && (!(raw instanceof Number) || !valid(value))) {
                logger.warning("Invalid " + path + "; using 0 seconds.");
                value = 0.0;
            }
            values.put(action, value);
            String scopePath = SCOPE_CONFIG_ROOT + "." + action.configKey();
            String rawScope = config.getString(scopePath);
            CombatItemScope scope = CombatItemScope.parse(rawScope);
            if (scope == null) {
                logger.warning("Invalid " + scopePath + "; using pvp.");
                scope = CombatItemScope.PVP_ONLY;
            }
            scopes.put(action, scope);
        }
        return new CombatItemSettings(values, scopes);
    }

    public CombatItemRule rule(CombatItemAction action) { return rules.get(action); }
    public double seconds(CombatItemAction action) { return rule(action).seconds(); }
    public CombatItemScope scope(CombatItemAction action) { return rule(action).scope(); }

    public CombatItemSettings with(CombatItemAction action, double value) {
        if (!valid(value)) throw new IllegalArgumentException("Cooldown must be -1 or a finite nonnegative number");
        return withRule(action, new CombatItemRule(value, scope(action)));
    }

    public CombatItemSettings withScope(CombatItemAction action, CombatItemScope scope) {
        return withRule(action, new CombatItemRule(seconds(action), scope));
    }

    private CombatItemSettings withRule(CombatItemAction changed, CombatItemRule rule) {
        EnumMap<CombatItemAction, Double> values = new EnumMap<>(CombatItemAction.class);
        EnumMap<CombatItemAction, CombatItemScope> scopes = new EnumMap<>(CombatItemAction.class);
        for (CombatItemAction action : CombatItemAction.values()) {
            CombatItemRule selected = action == changed ? rule : rules.get(action);
            values.put(action, selected.seconds());
            scopes.put(action, selected.scope());
        }
        return new CombatItemSettings(values, scopes);
    }

    public static boolean valid(double value) {
        return Double.isFinite(value) && (value == -1.0 || value >= 0.0);
    }
}
