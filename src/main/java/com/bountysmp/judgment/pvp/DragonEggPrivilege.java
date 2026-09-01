package com.bountysmp.judgment.pvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Objects;

public final class DragonEggPrivilege {
    private final Plugin plugin;
    private final PvpService pvp;
    private final java.util.function.Supplier<DragonEggSettings> settings;
    private final NamespacedKey glow, strength, speed;

    public DragonEggPrivilege(Plugin plugin, PvpService pvp, java.util.function.Supplier<DragonEggSettings> settings) {
        this.plugin = plugin; this.pvp = pvp; this.settings = settings;
        glow = new NamespacedKey(plugin, "dragon_egg_glow");
        strength = new NamespacedKey(plugin, "dragon_egg_strength");
        speed = new NamespacedKey(plugin, "dragon_egg_speed");
    }

    public void refresh(Player player) {
        boolean holder = settings.get().enabled() && pvp.isPvpEnabled(player.getUniqueId()) && hasEgg(player);
        DragonEggSettings config = settings.get();
        update(player, PotionEffectType.GLOWING, config.glow() && holder, glow, 0);
        update(player, PotionEffectType.STRENGTH, config.strength() && holder, strength, 0);
        update(player, PotionEffectType.SPEED, config.speed() && holder, speed, 0);
    }

    public boolean hasEgg(Player player) {
        return java.util.Arrays.stream(player.getInventory().getContents())
            .filter(Objects::nonNull).anyMatch(item -> item.getType() == Material.DRAGON_EGG && item.getAmount() > 0);
    }

    public boolean canPickupEgg(Player player) {
        return pvp.isPvpEnabled(player.getUniqueId());
    }

    private void update(Player player, PotionEffectType type, boolean active, NamespacedKey marker, int amplifier) {
        if (active) {
            player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, false, false, true));
            player.getPersistentDataContainer().set(marker, PersistentDataType.BYTE, (byte) 1);
        } else if (player.getPersistentDataContainer().has(marker, PersistentDataType.BYTE)) {
            player.removePotionEffect(type);
            player.getPersistentDataContainer().remove(marker);
        }
    }
}
