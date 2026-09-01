package com.bountysmp.judgment.combatitem;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

public final class CombatItemSettings {
    public static final String CONFIG_ROOT = "combat-item-cooldowns";
    public static final String SCOPE_CONFIG_ROOT = "combat-item-scopes";
    public static final String DAMAGE_MODIFIER_CONFIG_ROOT = "combat-item-damage-modifiers";
    private final Map<CombatItemAction, CombatItemRule> rules;

    public CombatItemSettings(Map<CombatItemAction, Double> seconds) {
        this(seconds, Map.of());
    }

    public CombatItemSettings(Map<CombatItemAction, Double> seconds,
                              Map<CombatItemAction, CombatItemScope> scopes) {
        this(seconds, scopes, Map.of());
    }

    public CombatItemSettings(Map<CombatItemAction, Double> seconds,
                              Map<CombatItemAction, CombatItemScope> scopes,
                              Map<CombatItemAction, Double> damageModifiers) {
        EnumMap<CombatItemAction, CombatItemRule> copy = new EnumMap<>(CombatItemAction.class);
        for (CombatItemAction action : CombatItemAction.values())
            copy.put(action, new CombatItemRule(seconds.getOrDefault(action, 0.0),
                scopes.getOrDefault(action, CombatItemScope.PVP_ONLY),
                damageModifiers.getOrDefault(action, 1.0)));
        this.rules = Map.copyOf(copy);
    }

    public static CombatItemSettings defaults() { return new CombatItemSettings(Map.of()); }

    public static CombatItemSettings fromConfig(FileConfiguration config, Logger logger) {
        EnumMap<CombatItemAction, Double> values = new EnumMap<>(CombatItemAction.class);
        EnumMap<CombatItemAction, CombatItemScope> scopes = new EnumMap<>(CombatItemAction.class);
        EnumMap<CombatItemAction, Double> damageModifiers = new EnumMap<>(CombatItemAction.class);
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
            double damageModifier = 1.0;
            if (action.explosive()) {
                String modifierPath = DAMAGE_MODIFIER_CONFIG_ROOT + "." + action.configKey();
                Object rawModifier = config.get(modifierPath);
                damageModifier = rawModifier instanceof Number number ? number.doubleValue() : 1.0;
                if (rawModifier != null && (!(rawModifier instanceof Number) || !validDamageModifier(damageModifier))) {
                    logger.warning("Invalid " + modifierPath + "; using 1.0.");
                    damageModifier = 1.0;
                }
            }
            damageModifiers.put(action, damageModifier);
        }
        return new CombatItemSettings(values, scopes, damageModifiers);
    }

    public CombatItemRule rule(CombatItemAction action) { return rules.get(action); }
    public double seconds(CombatItemAction action) { return rule(action).seconds(); }
    public CombatItemScope scope(CombatItemAction action) { return rule(action).scope(); }
    public double damageModifier(CombatItemAction action) { return rule(action).damageModifier(); }

    public CombatItemSettings with(CombatItemAction action, double value) {
        if (!valid(value)) throw new IllegalArgumentException("Cooldown must be -1 or a finite nonnegative number");
        return withRule(action, new CombatItemRule(value, scope(action), damageModifier(action)));
    }

    public CombatItemSettings withScope(CombatItemAction action, CombatItemScope scope) {
        return withRule(action, new CombatItemRule(seconds(action), scope, damageModifier(action)));
    }

    public CombatItemSettings withDamageModifier(CombatItemAction action, double modifier) {
        if (!action.explosive()) throw new IllegalArgumentException("Damage modifiers require an explosive action");
        if (!validDamageModifier(modifier)) throw new IllegalArgumentException("Damage modifier must be finite and nonnegative");
        return withRule(action, new CombatItemRule(seconds(action), scope(action), modifier));
    }

    private CombatItemSettings withRule(CombatItemAction changed, CombatItemRule rule) {
        EnumMap<CombatItemAction, Double> values = new EnumMap<>(CombatItemAction.class);
        EnumMap<CombatItemAction, CombatItemScope> scopes = new EnumMap<>(CombatItemAction.class);
        EnumMap<CombatItemAction, Double> damageModifiers = new EnumMap<>(CombatItemAction.class);
        for (CombatItemAction action : CombatItemAction.values()) {
            CombatItemRule selected = action == changed ? rule : rules.get(action);
            values.put(action, selected.seconds());
            scopes.put(action, selected.scope());
            damageModifiers.put(action, selected.damageModifier());
        }
        return new CombatItemSettings(values, scopes, damageModifiers);
    }

    public static boolean valid(double value) {
        return Double.isFinite(value) && (value == -1.0 || value >= 0.0);
    }

    public static boolean validDamageModifier(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }
}
