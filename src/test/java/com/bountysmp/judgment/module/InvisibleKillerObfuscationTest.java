package com.bountysmp.judgment.module;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvisibleKillerObfuscationTest {
    @Test
    void masksAlwaysHaveAConstantLengthAndVary() {
        InvisibleKillerObfuscation module = new InvisibleKillerObfuscation(() -> true);
        String first = module.randomMask();
        String second = module.randomMask();

        assertEquals(InvisibleKillerObfuscation.MASK_LENGTH, first.length());
        assertEquals(InvisibleKillerObfuscation.MASK_LENGTH, second.length());
        assertNotEquals(first, second);
    }

    @Test
    void deathMessageUsesAnObfuscatedMaskWithoutChangingVanillaReason() {
        String mask = "A".repeat(InvisibleKillerObfuscation.MASK_LENGTH);
        TranslatableComponent original = Component.translatable("death.attack.explosion.player",
            Component.text("Victim"), Component.text("Killer"));
        TranslatableComponent message = (TranslatableComponent) InvisibleKillerObfuscation.maskKiller(original, mask);
        Component maskedName = (Component) message.arguments().get(1).value();

        assertEquals("death.attack.explosion.player", message.key());
        assertEquals(Component.text("Victim"), message.arguments().getFirst().value());
        assertEquals(TextDecoration.State.TRUE, maskedName.decoration(TextDecoration.OBFUSCATED));
        assertFalse(message.toString().contains("Killer"));
    }

    @Test
    void preservesItemArgumentForWeaponDeathMessages() {
        Component weapon = Component.text("Bonk Stick");
        TranslatableComponent original = Component.translatable("death.attack.player.item",
            Component.text("Victim"), Component.text("Killer"), weapon);

        TranslatableComponent message = (TranslatableComponent) InvisibleKillerObfuscation.maskKiller(original, "ABCDEFGH");

        assertEquals("death.attack.player.item", message.key());
        assertEquals(weapon, message.arguments().get(2).value());
    }
}
