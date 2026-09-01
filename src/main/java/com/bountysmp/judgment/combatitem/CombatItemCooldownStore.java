package com.bountysmp.judgment.combatitem;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatItemCooldownStore {
    private final Path path;

    public CombatItemCooldownStore(Path path) { this.path = path; }

    public Map<UUID, Map<CombatItemAction, Long>> load() throws IOException {
        Map<UUID, Map<CombatItemAction, Long>> loaded = new HashMap<>();
        if (!Files.exists(path)) return loaded;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        ConfigurationSection root = yaml.getConfigurationSection("cooldowns");
        if (root == null) return loaded;
        for (String uuidKey : root.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                EnumMap<CombatItemAction, Long> actions = new EnumMap<>(CombatItemAction.class);
                for (CombatItemAction action : CombatItemAction.values()) {
                    long timestamp = root.getLong(uuidKey + "." + action.configKey(), -1L);
                    if (timestamp >= 0L) actions.put(action, timestamp);
                }
                if (!actions.isEmpty()) loaded.put(uuid, actions);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed UUID entries without preventing valid timers from loading.
            }
        }
        return loaded;
    }

    public void save(Map<UUID, Map<CombatItemAction, Long>> cooldowns) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        cooldowns.forEach((uuid, actions) -> actions.forEach((action, timestamp) ->
            yaml.set("cooldowns." + uuid + "." + action.configKey(), timestamp)));
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        yaml.save(temporary.toFile());
        try {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
