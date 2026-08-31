package com.bountysmp.judgment.pvp;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Strict reads and atomic writes: corrupt data must never become fresh preferences. */
public final class PvpStore {
    private final Path path;

    public PvpStore(Path path) {
        this.path = path;
    }

    public Map<UUID, PvpState> load() throws IOException {
        Map<UUID, PvpState> result = new HashMap<>();
        if (!Files.exists(path)) return result;
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(path.toFile());
            if (!yaml.isConfigurationSection("players")) throw new IllegalArgumentException("Missing players section");
            for (String id : yaml.getConfigurationSection("players").getKeys(false)) {
                String base = "players." + id;
                if (!yaml.isBoolean(base + ".enabled")) throw new IllegalArgumentException("Invalid status for " + id);
                long toggle = timestamp(yaml, base + ".last-toggle-millis");
                long end = timestamp(yaml, base + ".combat-end-millis");
                Map<UUID, Long> opponents = new HashMap<>();
                var section = yaml.getConfigurationSection(base + ".opponents");
                if (yaml.contains(base + ".opponents") && section == null) {
                    throw new IllegalArgumentException("Invalid opponents for " + id);
                }
                if (section != null) {
                    for (String opponent : section.getKeys(false)) {
                        opponents.put(UUID.fromString(opponent), timestamp(yaml, base + ".opponents." + opponent));
                    }
                }
                result.put(UUID.fromString(id), new PvpState(yaml.getBoolean(base + ".enabled"), toggle, end, opponents));
            }
        } catch (InvalidConfigurationException | IllegalArgumentException exception) {
            throw new IOException("Invalid PvP data in " + path + "; refusing to reset it", exception);
        }
        return result;
    }

    private static long timestamp(YamlConfiguration yaml, String key) {
        Object value = yaml.get(key);
        if (!(value instanceof Long || value instanceof Integer) || ((Number) value).longValue() < -1L) {
            throw new IllegalArgumentException("Invalid timestamp: " + key);
        }
        return ((Number) value).longValue();
    }

    public void save(Map<UUID, PvpState> states) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("players");
        states.forEach((id, state) -> {
            String base = "players." + id;
            yaml.set(base + ".enabled", state.enabled());
            yaml.set(base + ".last-toggle-millis", state.lastToggleMillis());
            yaml.set(base + ".combat-end-millis", state.combatEndMillis());
            state.opponents().forEach((opponent, end) -> yaml.set(base + ".opponents." + opponent, end));
        });
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "pvp-players-", ".tmp");
        try {
            Files.writeString(temporary, yaml.saveToString(), StandardCharsets.UTF_8);
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
