package com.bountysmp.judgment.combatitem;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pending flight restrictions are separate from combat timers and survive combat exit. */
public final class FlightLandingStore {
    private final Path path;

    public FlightLandingStore(Path path) { this.path = path; }

    public Map<UUID, EnumSet<CombatItemAction>> load() throws IOException {
        Map<UUID, EnumSet<CombatItemAction>> result = new HashMap<>();
        if (!Files.exists(path)) return result;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            var root = yaml.getConfigurationSection("pending");
            if (root == null) {
                if (yaml.contains("pending")) throw new IOException("Invalid pending flight restrictions");
                return result;
            }
            for (String key : root.getKeys(false)) {
                UUID id = UUID.fromString(key);
                var values = root.getList(key);
                if (values == null) throw new IOException("Invalid flight restriction list: " + key);
                EnumSet<CombatItemAction> causes = EnumSet.noneOf(CombatItemAction.class);
                for (Object raw : values) {
                    if (!(raw instanceof String value)) throw new IOException("Invalid flight restriction: " + raw);
                    CombatItemAction action = CombatItemAction.valueOf(value);
                    if (action != CombatItemAction.ELYTRA && action != CombatItemAction.FIREWORKS)
                        throw new IOException("Invalid flight restriction: " + value);
                    causes.add(action);
                }
                if (!causes.isEmpty()) result.put(id, causes);
            }
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IOException("Invalid flight landing state", exception);
        }
        return result;
    }

    public void save(Map<UUID, EnumSet<CombatItemAction>> pending) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        pending.forEach((id, causes) -> yaml.set("pending." + id, causes.stream().map(Enum::name).toList()));
        Path parent = path.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        yaml.save(temporary.toFile());
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
