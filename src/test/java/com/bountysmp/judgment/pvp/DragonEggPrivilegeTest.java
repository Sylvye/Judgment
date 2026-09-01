package com.bountysmp.judgment.pvp;

import org.bukkit.Material;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DragonEggPrivilegeTest {
    Plugin plugin;
    PlayerMock player;
    AtomicReference<DragonEggSettings> settings;
    DragonEggPrivilege privilege;
    PvpService pvp;

    @BeforeEach void setup() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = MockBukkit.getMock().addPlayer("Hunter");
        settings = new AtomicReference<>(new DragonEggSettings(true, true, true, true));
        pvp = new PvpService(new PvpStore(java.nio.file.Path.of("build/test-pvp.yml")),
            () -> new PvpSettings(true, 0, 0), System::currentTimeMillis, ignored -> false, plugin.getLogger());
        privilege = new DragonEggPrivilege(plugin, pvp, settings::get);
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void enabledHolderReceivesConfiguredPermanentEffects() {
        player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.DRAGON_EGG));
        pvp.change(player.getUniqueId(), true);
        privilege.refresh(player);
        assertTrue(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.GLOWING));
        assertEquals(0, player.getPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH).getAmplifier());
        assertEquals(Integer.MAX_VALUE, player.getPotionEffect(org.bukkit.potion.PotionEffectType.SPEED).getDuration());
    }

    @Test void effectsAreRemovedWhenEggIsLostAndUnrelatedEffectsRemain() {
        player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.DRAGON_EGG));
        pvp.change(player.getUniqueId(), true);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, 400, 1));
        privilege.refresh(player);
        player.getInventory().clear();
        privilege.refresh(player);
        assertFalse(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.GLOWING));
        assertFalse(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH));
        assertTrue(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST));
    }

    @Test void featureAndIndividualEffectsCanBeDisabled() {
        player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.DRAGON_EGG));
        pvp.change(player.getUniqueId(), true);
        settings.set(new DragonEggSettings(false, true, true, true));
        privilege.refresh(player);
        assertFalse(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.GLOWING));
        settings.set(new DragonEggSettings(true, false, true, false));
        privilege.refresh(player);
        assertTrue(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH));
        assertFalse(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED));
    }

    @Test void pvpDisabledPlayersDoNotReceiveThePrivilege() {
        player.getInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.DRAGON_EGG));
        pvp.change(player.getUniqueId(), false);
        privilege.refresh(player);
        assertFalse(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.GLOWING));
        assertFalse(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH));
        assertFalse(player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SPEED));
    }
}
