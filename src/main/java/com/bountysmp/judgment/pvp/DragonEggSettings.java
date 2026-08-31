package com.bountysmp.judgment.pvp;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.logging.Logger;

public record DragonEggSettings(boolean enabled, boolean glow, boolean strength, boolean speed) {
    public static DragonEggSettings fromConfig(FileConfiguration config, Logger logger) {
        return new DragonEggSettings(config.getBoolean("pvp.dragon-egg.enabled", false),
            config.getBoolean("pvp.dragon-egg.effects.glow", true),
            config.getBoolean("pvp.dragon-egg.effects.strength", true),
            config.getBoolean("pvp.dragon-egg.effects.speed", true));
    }
}
