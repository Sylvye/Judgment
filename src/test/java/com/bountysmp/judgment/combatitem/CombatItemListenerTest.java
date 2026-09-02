package com.bountysmp.judgment.combatitem;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent;
import io.papermc.paper.event.entity.EntityLungeEvent;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CombatItemListenerTest {
    @TempDir Path directory;
    ServerMock server;
    WorldMock world;
    PlayerMock player;
    CombatItemListener listener;
    NativeWeaponCooldownController nativeCooldowns;
    CombatItemCooldownManager manager;
    AtomicReference<CombatItemSettings> settings;

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Fighter");
        Plugin plugin = MockBukkit.createMockPlugin();
        EnumMap<CombatItemAction, Double> values = new EnumMap<>(CombatItemAction.class);
        for (CombatItemAction action : CombatItemAction.values()) values.put(action, -1.0);
        settings = new AtomicReference<>(new CombatItemSettings(values));
        manager = new CombatItemCooldownManager(
            new CombatItemCooldownStore(directory.resolve("cooldowns.yml")), settings::get,
            ignored -> true, System::currentTimeMillis, plugin.getLogger());
        nativeCooldowns = new NativeWeaponCooldownController(plugin, manager);
        listener = new CombatItemListener(plugin, manager, nativeCooldowns);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach void cleanup() {
        nativeCooldowns.close();
        MockBukkit.unmock();
    }

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
        PlayerMock target = server.addPlayer("Target");
        player.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
        EntityDamageByEntityEvent smash = maceDamage(target, DamageType.MACE_SMASH);
        server.getPluginManager().callEvent(smash);
        assertTrue(smash.isCancelled());
    }

    @Test void positiveWeaponRulesUseTheirConfiguredEnforcement() {
        settings.set(CombatItemSettings.defaults()
            .with(CombatItemAction.MACE_SMASH, 1.01)
            .with(CombatItemAction.RIPTIDE, 1.01)
            .with(CombatItemAction.LUNGE, 1.01));

        PlayerRiptideEvent riptide = new PlayerRiptideEvent(player, new ItemStack(Material.TRIDENT));
        server.getPluginManager().callEvent(riptide);
        assertFalse(riptide.isCancelled());
        assertEquals(21, player.getCooldown(Material.TRIDENT));
        player.setCooldown(Material.TRIDENT, 10);
        nativeCooldowns.refresh(player);
        assertEquals(10, player.getCooldown(Material.TRIDENT));
        player.setCooldown(Material.TRIDENT, 0);
        nativeCooldowns.resynchronize(CombatItemAction.RIPTIDE);
        assertTrue(player.getCooldown(Material.TRIDENT) > 0);

        EntityLungeEvent lunge = new EntityLungeEvent(player, 1);
        server.getPluginManager().callEvent(lunge);
        assertFalse(lunge.isCancelled());
        for (Material spear : spearMaterials()) assertEquals(0, player.getCooldown(spear));
        EntityLungeEvent secondLunge = new EntityLungeEvent(player, 1);
        server.getPluginManager().callEvent(secondLunge);
        assertTrue(secondLunge.isCancelled());

        PlayerMock target = server.addPlayer("Target");
        player.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
        EntityDamageByEntityEvent smash = maceDamage(target, DamageType.MACE_SMASH);
        server.getPluginManager().callEvent(smash);
        assertFalse(smash.isCancelled());
        assertEquals(21, player.getCooldown(Material.MACE));
        EntityDamageByEntityEvent ordinaryAttack = maceDamage(target, DamageType.PLAYER_ATTACK);
        server.getPluginManager().callEvent(ordinaryAttack);
        assertTrue(ordinaryAttack.isCancelled());

        settings.set(CombatItemSettings.defaults());
        manager.settingChanged(CombatItemAction.MACE_SMASH, 0.0);
        manager.settingChanged(CombatItemAction.RIPTIDE, 0.0);
        manager.settingChanged(CombatItemAction.LUNGE, 0.0);
        nativeCooldowns.refresh(player);
        assertEquals(0, player.getCooldown(Material.MACE));
        assertEquals(0, player.getCooldown(Material.TRIDENT));
        assertEquals(0, player.getCooldown(Material.IRON_SPEAR));
    }

    @Test void nativeCooldownRoundsPartialTicksUp() {
        nativeCooldowns.apply(player, CombatItemAction.RIPTIDE, 51L);
        assertEquals(2, player.getCooldown(Material.TRIDENT));
    }

    @Test void blocksBannedRiptideBeforeItStartsCharging() {
        ItemStack riptide = new ItemStack(Material.TRIDENT);
        riptide.addUnsafeEnchantment(Enchantment.RIPTIDE, 1);
        PlayerInteractEvent blocked = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
            riptide, null, BlockFace.SELF, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(blocked);
        assertTrue(blocked.isCancelled());

        PlayerInteractEvent ordinary = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
            new ItemStack(Material.TRIDENT), null, BlockFace.SELF, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(ordinary);
        assertNotEquals(org.bukkit.event.Event.Result.DENY, ordinary.useItemInHand());
    }

    @Test void bansOnlyFireworkCrossbowLoadingAndTimesFireworkShots() {
        ItemStack crossbow = new ItemStack(Material.CROSSBOW);
        player.getInventory().setItemInOffHand(new ItemStack(Material.FIREWORK_ROCKET));
        EntityLoadCrossbowEvent blockedLoad = new EntityLoadCrossbowEvent(player, crossbow, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(blockedLoad);
        assertTrue(blockedLoad.isCancelled());

        player.getInventory().setItemInOffHand(new ItemStack(Material.ARROW));
        EntityLoadCrossbowEvent arrowLoad = new EntityLoadCrossbowEvent(player, crossbow, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(arrowLoad);
        assertFalse(arrowLoad.isCancelled());

        settings.set(CombatItemSettings.defaults().with(CombatItemAction.FIREWORK_CROSSBOWS, 1.01));
        Firework firework = world.spawn(world.getSpawnLocation(), Firework.class);
        EntityShootBowEvent shot = new EntityShootBowEvent(player, crossbow,
            new ItemStack(Material.FIREWORK_ROCKET), firework, EquipmentSlot.HAND, 1.0f, true);
        server.getPluginManager().callEvent(shot);
        assertFalse(shot.isCancelled());
        assertTrue(player.getCooldown(crossbow) > 0);
        assertTrue(player.getCooldown(crossbow) <= 21);

        player.setCooldown(crossbow, 0);
        EntityShootBowEvent arrowShot = new EntityShootBowEvent(player, crossbow,
            new ItemStack(Material.ARROW), world.spawn(world.getSpawnLocation(), Arrow.class),
            EquipmentSlot.HAND, 1.0f, true);
        server.getPluginManager().callEvent(arrowShot);
        assertEquals(0, player.getCooldown(crossbow));
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

        Block tnt = world.getBlockAt(2, 64, 0);
        tnt.setType(Material.TNT);
        BlockPlaceEvent tntPlace = new BlockPlaceEvent(tnt, tnt.getState(), block,
            new ItemStack(Material.TNT), player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(tntPlace);
        assertTrue(tntPlace.isCancelled());

        Block bed = world.getBlockAt(3, 64, 0);
        bed.setType(Material.BLUE_BED);
        BlockPlaceEvent bedPlace = new BlockPlaceEvent(bed, bed.getState(), block,
            new ItemStack(Material.BLUE_BED), player, true, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(bedPlace);
        assertTrue(bedPlace.isCancelled());

        Block stone = world.getBlockAt(4, 64, 0);
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

    @Test void modifiesOnlyPlayerDamageFromEntityExplosives() {
        CombatItemSettings updated = CombatItemSettings.defaults()
            .withDamageModifier(CombatItemAction.TNT, 0.5)
            .withDamageModifier(CombatItemAction.TNT_MINECARTS, 0.25)
            .withDamageModifier(CombatItemAction.END_CRYSTALS, 2.0);
        settings.set(updated);
        PlayerMock victim = server.addPlayer("Victim");

        TNTPrimed tnt = world.spawn(world.getSpawnLocation(), TNTPrimed.class);
        EntityDamageEvent tntDamage = explosion(victim, tnt, 8);
        server.getPluginManager().callEvent(tntDamage);
        assertEquals(4.0, tntDamage.getDamage());

        ExplosiveMinecart minecart = world.spawn(world.getSpawnLocation(), ExplosiveMinecart.class);
        EntityDamageEvent minecartDamage = explosion(victim, minecart, 8);
        server.getPluginManager().callEvent(minecartDamage);
        assertEquals(2.0, minecartDamage.getDamage());

        EnderCrystal crystal = world.spawn(world.getSpawnLocation(), EnderCrystal.class);
        EntityDamageEvent crystalDamage = explosion(victim, crystal, 8);
        server.getPluginManager().callEvent(crystalDamage);
        assertEquals(16.0, crystalDamage.getDamage());

        ArmorStand mob = world.spawn(world.getSpawnLocation(), ArmorStand.class);
        EntityDamageEvent mobDamage = explosion(mob, tnt, 8);
        server.getPluginManager().callEvent(mobDamage);
        assertEquals(8.0, mobDamage.getDamage());

        EntityDamageEvent cancelled = explosion(victim, tnt, 8);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        assertEquals(8.0, cancelled.getDamage());
    }

    @Test void tracksBedAndRespawnAnchorDamageByLocation() {
        settings.set(CombatItemSettings.defaults()
            .withDamageModifier(CombatItemAction.BEDS, 0.5)
            .withDamageModifier(CombatItemAction.RESPAWN_ANCHORS, 0.0));
        PlayerMock victim = server.addPlayer("Victim");

        world.setEnvironment(World.Environment.NETHER);
        Block bed = world.getBlockAt(10, 64, 10);
        bed.setType(Material.RED_BED);
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            null, bed, BlockFace.UP, EquipmentSlot.HAND));
        EntityDamageEvent bedDamage = badRespawnDamage(victim, bed, 8);
        server.getPluginManager().callEvent(bedDamage);
        assertEquals(4.0, bedDamage.getDamage());

        world.setEnvironment(World.Environment.NORMAL);
        Block anchor = world.getBlockAt(12, 64, 12);
        anchor.setType(Material.RESPAWN_ANCHOR);
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
            null, anchor, BlockFace.UP, EquipmentSlot.HAND));
        EntityDamageEvent anchorDamage = badRespawnDamage(victim, anchor, 8);
        server.getPluginManager().callEvent(anchorDamage);
        assertEquals(0.0, anchorDamage.getDamage());
    }

    private EntityDamageEvent explosion(Entity victim, Entity source, double damage) {
        DamageSource damageSource = DamageSource.builder(DamageType.EXPLOSION)
            .withDirectEntity(source).withDamageLocation(source.getLocation()).build();
        return new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, damageSource, damage);
    }

    private static Material[] spearMaterials() {
        return new Material[] {Material.WOODEN_SPEAR, Material.STONE_SPEAR, Material.COPPER_SPEAR,
            Material.IRON_SPEAR, Material.GOLDEN_SPEAR, Material.DIAMOND_SPEAR, Material.NETHERITE_SPEAR};
    }

    private EntityDamageByEntityEvent maceDamage(Entity victim, DamageType type) {
        DamageSource source = DamageSource.builder(type).withDirectEntity(player).withCausingEntity(player).build();
        return new EntityDamageByEntityEvent(player, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 10);
    }

    private EntityDamageEvent badRespawnDamage(Entity victim, Block source, double damage) {
        DamageSource damageSource = DamageSource.builder(DamageType.BAD_RESPAWN_POINT)
            .withDamageLocation(source.getLocation()).build();
        return new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION, damageSource, damage);
    }
}
