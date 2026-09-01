package com.bountysmp.judgment.module;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffectType;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Hides invisible killers in public death messages without changing kill credit. */
public final class InvisibleKillerObfuscation implements Listener {
    static final int MASK_LENGTH = 8;
    private static final char[] MASK_CHARACTERS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final BooleanSupplier enabled;

    public InvisibleKillerObfuscation(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (!enabled.getAsBoolean() || killer == null || !isInvisible(killer) || event.deathMessage() == null) {
            return;
        }

        // Deliberately do not call victim.setKiller(...): plugins still receive the real credit.
        event.deathMessage(maskKiller(event.deathMessage(), randomMask()));
    }

    static boolean isInvisible(Player player) {
        return player.isInvisible() || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    static Component maskKiller(Component deathMessage, String mask) {
        if (!(deathMessage instanceof TranslatableComponent translatable)
            || translatable.arguments().size() < 2) {
            return deathMessage;
        }

        List<ComponentLike> arguments = new ArrayList<>(translatable.arguments().size());
        for (TranslationArgument argument : translatable.arguments()) {
            if (!(argument.value() instanceof ComponentLike component)) {
                return deathMessage;
            }
            arguments.add(component);
        }
        arguments.set(1, Component.text(mask, NamedTextColor.WHITE, TextDecoration.OBFUSCATED));
        return translatable.arguments(arguments);
    }

    String randomMask() {
        StringBuilder mask = new StringBuilder(MASK_LENGTH);
        for (int index = 0; index < MASK_LENGTH; index++) {
            mask.append(MASK_CHARACTERS[random.nextInt(MASK_CHARACTERS.length)]);
        }
        return mask.toString();
    }
}
