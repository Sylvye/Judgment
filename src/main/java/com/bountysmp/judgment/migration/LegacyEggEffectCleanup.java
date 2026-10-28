package com.bountysmp.judgment.migration;

import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

/** Upgrade-only cleanup for permanent effects left by the former embedded module. */
public final class LegacyEggEffectCleanup implements Listener {
    private static final Map<String, PotionEffectType> EFFECTS = Map.of(
        "dragon_egg_glow", PotionEffectType.GLOWING,
        "dragon_egg_strength", PotionEffectType.STRENGTH,
        "dragon_egg_speed", PotionEffectType.SPEED);

    public static void clear(Player player) {
        EFFECTS.forEach((name, effect) -> {
            NamespacedKey key = new NamespacedKey("judgment", name);
            if (player.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) {
                player.removePotionEffect(effect);
                player.getPersistentDataContainer().remove(key);
            }
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) { clear(event.getPlayer()); }
}
