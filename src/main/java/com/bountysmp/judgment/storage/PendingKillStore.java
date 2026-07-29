package com.bountysmp.judgment.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PendingKillStore {
    private final Path path;
    private final Map<UUID, PendingKill> pendingKills = new LinkedHashMap<>();

    public PendingKillStore(Path path) {
        this.path = path;
    }

    public synchronized void load() {
        pendingKills.clear();
        if (!Files.exists(path)) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        ConfigurationSection section = yaml.getConfigurationSection("pending-kills");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID offenderId = UUID.fromString(key);
                UUID killerId = UUID.fromString(section.getString(key + ".killer-uuid", ""));
                String offenderName = section.getString(key + ".offender-name", "Unknown");
                String killerName = section.getString(key + ".killer-name", "Unknown");
                long approvedAt = section.getLong(key + ".approved-at-millis", 0L);
                pendingKills.put(offenderId, new PendingKill(offenderId, offenderName, killerId, killerName, approvedAt));
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed entries and keep loading the rest.
            }
        }
    }

    public synchronized void save() throws IOException {
        Files.createDirectories(path.getParent());
        YamlConfiguration yaml = new YamlConfiguration();
        for (PendingKill pendingKill : pendingKills.values()) {
            String base = "pending-kills." + pendingKill.offenderId();
            yaml.set(base + ".offender-name", pendingKill.offenderName());
            yaml.set(base + ".killer-uuid", pendingKill.killerId().toString());
            yaml.set(base + ".killer-name", pendingKill.killerName());
            yaml.set(base + ".approved-at-millis", pendingKill.approvedAtMillis());
        }
        yaml.save(path.toFile());
    }

    public synchronized void put(PendingKill pendingKill) {
        pendingKills.put(pendingKill.offenderId(), pendingKill);
    }

    public synchronized Optional<PendingKill> get(UUID offenderId) {
        return Optional.ofNullable(pendingKills.get(offenderId));
    }

    public synchronized Optional<PendingKill> remove(UUID offenderId) {
        return Optional.ofNullable(pendingKills.remove(offenderId));
    }

    public synchronized Collection<PendingKill> values() {
        return List.copyOf(pendingKills.values());
    }
}
