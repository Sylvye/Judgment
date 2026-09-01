package com.bountysmp.judgment.combatitem;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.papermc.paper.event.entity.EntityLungeEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CombatItemListenerTest {
    @TempDir Path directory;
    ServerMock server;
    WorldMock world;
    PlayerMock player;
    CombatItemListener listener;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Fighter");
        Plugin plugin = MockBukkit.createMockPlugin();
        EnumMap<CombatItemAction, Double> values = new EnumMap<>(CombatItemAction.class);
        for (CombatItemAction action : CombatItemAction.values()) values.put(action, -1.0);
        CombatItemSettings settings = new CombatItemSettings(values);
        CombatItemCooldownManager manager = new CombatItemCooldownManager(
            new CombatItemCooldownStore(directory.resolve("cooldowns.yml")), () -> settings,
            ignored -> true, System::currentTimeMillis, plugin.getLogger());
        listener = new CombatItemListener(plugin, manager);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    @Test void blocksElytraEntryButNotExit() {
        EntityToggleGlideEvent entering = new EntityToggleGlideEvent(player, true);
        server.getPluginManager().callEvent(entering);
        assertTrue(entering.isCancelled());
        EntityToggleGlideEvent exiting = new EntityToggleGlideEvent(player, false);
        server.getPluginManager().callEvent(exiting);
        assertFalse(exiting.isCancelled());
    }

    @Test void blocksFireworkBoostPearlRiptideAndLunge() {
        Firework firework = world.spawn(world.getSpawnLocation(), Firework.class);
        PlayerElytraBoostEvent boost = new PlayerElytraBoostEvent(player,
            new ItemStack(Material.FIREWORK_ROCKET), firework, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(boost);
        assertTrue(boost.isCancelled());

        EnderPearl pearl = world.spawn(world.getSpawnLocation(), EnderPearl.class);
        PlayerLaunchProjectileEvent launch = new PlayerLaunchProjectileEvent(player,
            new ItemStack(Material.ENDER_PEARL), pearl);
        server.getPluginManager().callEvent(launch);
        assertTrue(launch.isCancelled());

        PlayerRiptideEvent riptide = new PlayerRiptideEvent(player, new ItemStack(Material.TRIDENT));
        server.getPluginManager().callEvent(riptide);
        assertTrue(riptide.isCancelled());

        EntityLungeEvent lunge = new EntityLungeEvent(player, 1);
        server.getPluginManager().callEvent(lunge);
        assertTrue(lunge.isCancelled());
    }

    @Test void blocksMaceSmash() {
        DamageSource source = DamageSource.builder(DamageType.MACE_SMASH)
            .withDirectEntity(player).withCausingEntity(player).build();
        EntityDamageEvent smash = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 10);
        server.getPluginManager().callEvent(smash);
        assertTrue(smash.isCancelled());
    }

    @Test void blocksConfiguredPlacementsButNotUnrelatedBlocks() {
        Block block = world.getBlockAt(0, 64, 0);
        EnderCrystal crystal = world.spawn(world.getSpawnLocation(), EnderCrystal.class);
        EntityPlaceEvent crystalPlace = new EntityPlaceEvent(crystal, player, block, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(crystalPlace);
        assertTrue(crystalPlace.isCancelled());

        ExplosiveMinecart minecart = world.spawn(world.getSpawnLocation(), ExplosiveMinecart.class);
        EntityPlaceEvent minecartPlace = new EntityPlaceEvent(minecart, player, block, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(minecartPlace);
        assertTrue(minecartPlace.isCancelled());

        Block anchor = world.getBlockAt(1, 64, 0);
        anchor.setType(Material.RESPAWN_ANCHOR);
        BlockPlaceEvent anchorPlace = new BlockPlaceEvent(anchor, anchor.getState(), block,
            new ItemStack(Material.RESPAWN_ANCHOR), player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(anchorPlace);
        assertTrue(anchorPlace.isCancelled());

        Block stone = world.getBlockAt(2, 64, 0);
        stone.setType(Material.STONE);
        BlockPlaceEvent stonePlace = new BlockPlaceEvent(stone, stone.getState(), block,
            new ItemStack(Material.STONE), player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(stonePlace);
        assertFalse(stonePlace.isCancelled());
    }

    @Test void ignoresEventsAlreadyCancelledByAnotherRule() {
        EntityToggleGlideEvent event = new EntityToggleGlideEvent(player, true);
        event.setCancelled(true);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }
}
