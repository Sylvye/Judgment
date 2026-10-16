package com.bountysmp.judgment.combatitem;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.damage.DamageAttribution;
import com.bountysmp.judgment.model.PunishmentMode;
import com.bountysmp.judgment.pvp.*;
import com.bountysmp.judgment.service.JudgmentService;
import com.bountysmp.judgment.storage.PendingKillStore;
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.*;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CombatDamageRulesTest {
    @TempDir Path directory;
    ServerMock server;
    WorldMock world;
    Plugin plugin;
    PlayerMock attacker, victim;
    JudgmentService combat;
    DamageAttribution attribution;
    FlightLandingRequirement landing;
    FlightLandingStore store;
    NativeWeaponCooldownController nativeCooldowns;
    AtomicReference<CombatItemSettings> settings = new AtomicReference<>(CombatItemSettings.defaults());
    AtomicLong now = new AtomicLong(1_000);

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        attacker = server.addPlayer("Attacker");
        victim = server.addPlayer("Victim");
        attacker.setOnGround(false);
        combat = new JudgmentService(plugin, new JudgmentSettings(30_000, 10_000, PunishmentMode.RELOG),
            new PendingKillStore(directory.resolve("pending.yml")), now::get);
        PvpService pvp = new PvpService(new PvpStore(directory.resolve("pvp.yml")),
            () -> new PvpSettings(true, 0, 0), now::get,
            id -> combat.getCombatTag(id).isPresent(), plugin.getLogger());
        attribution = new DamageAttribution(plugin);
        server.getPluginManager().registerEvents(new PvpListener(plugin, pvp, combat, new PvpPresentation(pvp), attribution), plugin);
        store = new FlightLandingStore(directory.resolve("flight.yml"));
        createLanding();
        CombatItemCooldownManager cooldowns = new CombatItemCooldownManager(
            new CombatItemCooldownStore(directory.resolve("cooldowns.yml")), settings::get,
            id -> combat.getCombatTag(id).isPresent(), now::get, plugin.getLogger());
        nativeCooldowns = new NativeWeaponCooldownController(plugin, cooldowns);
        server.getPluginManager().registerEvents(new CombatItemListener(plugin, cooldowns, nativeCooldowns,
            attribution, combat::isExecutingPunishment), plugin);
    }

    private void createLanding() {
        // MockBukkit does not implement block collision shapes; geometry is tested separately.
        landing = new FlightLandingRequirement(plugin, settings::get, attribution, combat::isExecutingPunishment,
            store, p -> p.isOnGround() && !p.isGliding() && !p.isInWater());
        server.getPluginManager().registerEvents(landing, plugin);
    }

    @AfterEach void cleanup() {
        landing.close();
        nativeCooldowns.close();
        MockBukkit.unmock();
    }

    @ParameterizedTest
    @EnumSource(value = CombatItemAction.class, names = {"TNT", "TNT_MINECARTS", "END_CRYSTALS", "BEDS", "RESPAWN_ANCHORS"})
    void bannedExplosivesCannotStartOrRefreshCombat(CombatItemAction action) {
        settings.set(settings.get().with(action, -1));
        EntityDamageEvent opening = explosion(action, victim, true);
        server.getPluginManager().callEvent(opening);
        assertTrue(opening.isCancelled());
        assertTrue(combat.getCombatTag(attacker.getUniqueId()).isEmpty());
        assertTrue(combat.getCombatTag(victim.getUniqueId()).isEmpty());

        combat.recordPvpDamage(victim, attacker);
        long expires = combat.getCombatTag(attacker.getUniqueId()).orElseThrow().expiresAtMillis();
        now.addAndGet(1_000);
        EntityDamageEvent tagged = explosion(action, victim, true);
        server.getPluginManager().callEvent(tagged);
        assertTrue(tagged.isCancelled());
        assertEquals(expires, combat.getCombatTag(attacker.getUniqueId()).orElseThrow().expiresAtMillis());
    }

    @ParameterizedTest
    @EnumSource(value = CombatItemAction.class, names = {"TNT", "TNT_MINECARTS", "END_CRYSTALS", "BEDS", "RESPAWN_ANCHORS"})
    void allowedExplosivesKeepDamageModifiersAndPositivePlacementCooldowns(CombatItemAction action) {
        for (double seconds : new double[] {0, 10}) {
            settings.set(settings.get().with(action, seconds).withDamageModifier(action, 0.5));
            EntityDamageEvent event = explosion(action, victim, true);
            server.getPluginManager().callEvent(event);
            assertFalse(event.isCancelled());
            assertEquals(4.0, event.getDamage());
            assertTrue(combat.getCombatTag(attacker.getUniqueId()).isPresent());
        }
    }

    @Test void environmentalSelfAndMobExplosionsRemainAllowed() {
        settings.set(settings.get().with(CombatItemAction.END_CRYSTALS, -1));
        for (Entity target : List.of(attacker, world.spawn(world.getSpawnLocation(), ArmorStand.class))) {
            EntityDamageEvent event = explosion(CombatItemAction.END_CRYSTALS, target, true);
            server.getPluginManager().callEvent(event);
            assertFalse(event.isCancelled());
        }
        EntityDamageEvent environmental = explosion(CombatItemAction.END_CRYSTALS, victim, false);
        server.getPluginManager().callEvent(environmental);
        assertFalse(environmental.isCancelled());
    }

    @Test void projectileDetonationAndCrystalChainsKeepAttribution() {
        settings.set(settings.get().with(CombatItemAction.END_CRYSTALS, -1));
        Arrow arrow = world.spawn(world.getSpawnLocation(), Arrow.class);
        arrow.setShooter(attacker);
        EnderCrystal first = world.spawn(world.getSpawnLocation(), EnderCrystal.class);
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(arrow, first,
            EntityDamageEvent.DamageCause.PROJECTILE,
            DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).build(), 1));
        EnderCrystal second = world.spawn(world.getSpawnLocation(), EnderCrystal.class);
        server.getPluginManager().callEvent(new EntityDamageByEntityEvent(first, second,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(first).build(), 1));
        assertEquals(attacker.getUniqueId(), attribution.responsiblePlayer(second));
        EntityDamageEvent event = new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(second).build(), 8);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
        assertTrue(combat.getCombatTag(attacker.getUniqueId()).isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "true,false,false,true,true", "true,false,true,true,true",
        "false,true,false,false,true", "false,true,true,true,true",
        "true,true,false,true,true", "true,true,true,true,true",
        "false,false,false,false,true", "false,false,true,false,true",
        "true,true,true,false,false"
    })
    void flightTriggerMatrix(boolean elytraBan, boolean rocketBan, boolean boost, boolean blocked, boolean toggle) {
        settings.set(settings.get().with(CombatItemAction.ELYTRA, elytraBan ? -1 : 0)
            .with(CombatItemAction.FIREWORKS, rocketBan ? -1 : 0).withRequireLanding(toggle));
        if (boost) boost(false); else glide(false);
        EntityDamageByEntityEvent hit = hit(attacker, DamageType.MACE_SMASH);
        attacker.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
        server.getPluginManager().callEvent(hit);
        assertEquals(blocked, hit.isCancelled());
        assertEquals(!blocked, combat.getCombatTag(attacker.getUniqueId()).isPresent());
        assertEquals(!blocked, combat.getCombatTag(victim.getUniqueId()).isPresent());
    }

    @Test void cancelledFlightAttemptsDoNotRequireLanding() {
        settings.set(settings.get().with(CombatItemAction.ELYTRA, -1)
            .with(CombatItemAction.FIREWORKS, -1).withRequireLanding(true));
        boost(true);
        glide(true);
        assertFalse(landing.blocks(attacker.getUniqueId(), victim));
    }

    @Test void stoppingGlideAndWaterDoNotClearButLandingDoes() {
        settings.set(settings.get().with(CombatItemAction.FIREWORKS, -1).withRequireLanding(true));
        boost(false);
        server.getPluginManager().callEvent(new EntityToggleGlideEvent(attacker, false));
        assertTrue(landing.blocks(attacker.getUniqueId(), victim));
        attacker.setInWater(true);
        attacker.setOnGround(true);
        assertTrue(landing.blocks(attacker.getUniqueId(), victim));
        attacker.setInWater(false);
        assertFalse(landing.blocks(attacker.getUniqueId(), victim));
        assertTrue(combat.getCombatTag(attacker.getUniqueId()).isEmpty());
        EntityDamageEvent allowed = hit(attacker, DamageType.PLAYER_ATTACK);
        server.getPluginManager().callEvent(allowed);
        assertFalse(allowed.isCancelled());
        assertTrue(combat.getCombatTag(attacker.getUniqueId()).isPresent());
    }

    @Test void restrictionsSurviveQuitJoinAndServiceRestart() throws Exception {
        settings.set(settings.get().with(CombatItemAction.FIREWORKS, -1).withRequireLanding(true));
        boost(false);
        server.getPluginManager().callEvent(new PlayerQuitEvent(attacker, net.kyori.adventure.text.Component.empty()));
        assertTrue(store.load().containsKey(attacker.getUniqueId()));
        HandlerList.unregisterAll(landing);
        landing.close();
        createLanding();
        server.getPluginManager().callEvent(new PlayerJoinEvent(attacker, net.kyori.adventure.text.Component.empty()));
        assertTrue(landing.blocks(attacker.getUniqueId(), victim));
        attacker.setOnGround(true);
        landing.refresh(attacker);
        assertTrue(store.load().isEmpty());
    }

    @Test void disablingToggleOrRemovingBanClearsPersistedRestrictions() throws Exception {
        settings.set(settings.get().with(CombatItemAction.FIREWORKS, -1).withRequireLanding(true));
        boost(false);
        settings.set(settings.get().with(CombatItemAction.FIREWORKS, 0));
        landing.settingsChanged();
        assertTrue(store.load().isEmpty());
        settings.set(settings.get().with(CombatItemAction.FIREWORKS, -1));
        boost(false);
        settings.set(settings.get().withRequireLanding(false));
        landing.settingsChanged();
        assertTrue(store.load().isEmpty());
        assertFalse(landing.blocks(attacker.getUniqueId(), victim));
    }

    @Test void combatExpiryDoesNotClearFlightHistory() {
        settings.set(settings.get().with(CombatItemAction.FIREWORKS, -1).withRequireLanding(true));
        boost(false);
        combat.recordPvpDamage(victim, attacker);
        now.addAndGet(31_000);
        assertTrue(combat.getCombatTag(attacker.getUniqueId()).isEmpty());
        assertTrue(landing.blocks(attacker.getUniqueId(), victim));
    }

    @Test void unavailableStorageBlocksAffectedPvpWithoutOverwritingTheFile() throws Exception {
        HandlerList.unregisterAll(landing);
        landing.close();
        Path path = directory.resolve("flight.yml");
        java.nio.file.Files.writeString(path, "pending: [invalid");
        settings.set(settings.get().with(CombatItemAction.FIREWORKS, -1).withRequireLanding(true));
        createLanding();
        assertTrue(landing.blocks(attacker.getUniqueId(), victim));
        assertEquals("pending: [invalid", java.nio.file.Files.readString(path));
        settings.set(settings.get().withRequireLanding(false));
        landing.settingsChanged();
        assertFalse(landing.blocks(attacker.getUniqueId(), victim));
    }

    @Test void activationDetectsExistingGlideAndDeathClearsState() throws Exception {
        attacker.setGliding(true);
        settings.set(settings.get().with(CombatItemAction.ELYTRA, -1).withRequireLanding(true));
        landing.settingsChanged();
        assertTrue(landing.blocks(attacker.getUniqueId(), victim));
        attacker.setGliding(false);
        landing.onDeath(new PlayerDeathEvent(attacker, DamageSource.builder(DamageType.FALL).build(), new ArrayList<>(), 0, net.kyori.adventure.text.Component.empty(), false));
        assertTrue(store.load().isEmpty());
    }

    @Test void flightBlocksIndirectDamageButAllowsSelfEnvironmentAndMobs() {
        settings.set(settings.get().with(CombatItemAction.FIREWORKS, -1).withRequireLanding(true));
        boost(false);
        Arrow arrow = world.spawn(world.getSpawnLocation(), Arrow.class);
        arrow.setShooter(attacker);
        EntityDamageEvent projectile = new EntityDamageByEntityEvent(arrow, victim,
            EntityDamageEvent.DamageCause.PROJECTILE,
            DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).build(), 4);
        server.getPluginManager().callEvent(projectile);
        assertTrue(projectile.isCancelled());
        EntityDamageEvent explosive = explosion(CombatItemAction.TNT, victim, true);
        server.getPluginManager().callEvent(explosive);
        assertTrue(explosive.isCancelled());
        EntityDamageEvent self = explosion(CombatItemAction.TNT, attacker, true);
        server.getPluginManager().callEvent(self);
        assertFalse(self.isCancelled());
        EntityDamageEvent mob = explosion(CombatItemAction.TNT, world.spawn(world.getSpawnLocation(), ArmorStand.class), true);
        server.getPluginManager().callEvent(mob);
        assertFalse(mob.isCancelled());
        EntityDamageEvent fall = new EntityDamageEvent(victim, EntityDamageEvent.DamageCause.FALL,
            DamageSource.builder(DamageType.FALL).build(), 4);
        server.getPluginManager().callEvent(fall);
        assertFalse(fall.isCancelled());
        assertTrue(combat.getCombatTag(victim.getUniqueId()).isEmpty());
    }

    @Test void harmfulPotionsAndCloudsCannotStartCombatWhileFlightRestricted() {
        settings.set(settings.get().with(CombatItemAction.FIREWORKS, -1).withRequireLanding(true));
        boost(false);
        SplashPotion potion = world.spawn(world.getSpawnLocation(), SplashPotion.class);
        potion.setShooter(attacker);
        var meta = potion.getPotionMeta();
        meta.setBasePotionType(PotionType.POISON);
        meta.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 200, 0), true);
        potion.setPotionMeta(meta);
        PotionSplashEvent splash = new PotionSplashEvent(potion, null, null, null,
            new HashMap<>(Map.of(victim, 1.0, attacker, 1.0)));
        server.getPluginManager().callEvent(splash);
        assertEquals(0.0, splash.getIntensity(victim));
        assertEquals(1.0, splash.getIntensity(attacker));
        AreaEffectCloud cloud = world.spawn(world.getSpawnLocation(), AreaEffectCloud.class);
        cloud.setSource(attacker);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0), true);
        AreaEffectCloudApplyEvent apply = new AreaEffectCloudApplyEvent(cloud, new ArrayList<>(List.of(victim, attacker)));
        server.getPluginManager().callEvent(apply);
        assertEquals(List.of(attacker), apply.getAffectedEntities());
        assertTrue(combat.getCombatTag(victim.getUniqueId()).isEmpty());

        var healingMeta = potion.getPotionMeta();
        healingMeta.clearCustomEffects();
        healingMeta.setBasePotionType(PotionType.HEALING);
        potion.setPotionMeta(healingMeta);
        PotionSplashEvent healing = new PotionSplashEvent(potion, null, null, null, new HashMap<>(Map.of(victim, 1.0)));
        server.getPluginManager().callEvent(healing);
        assertEquals(1.0, healing.getIntensity(victim));
    }

    private void glide(boolean cancelled) {
        EntityToggleGlideEvent event = new EntityToggleGlideEvent(attacker, true);
        event.setCancelled(cancelled);
        server.getPluginManager().callEvent(event);
        if (!event.isCancelled()) attacker.setGliding(true);
    }

    private void boost(boolean cancelled) {
        PlayerElytraBoostEvent event = new PlayerElytraBoostEvent(attacker, new ItemStack(Material.FIREWORK_ROCKET),
            world.spawn(world.getSpawnLocation(), Firework.class), EquipmentSlot.HAND);
        event.setCancelled(cancelled);
        server.getPluginManager().callEvent(event);
    }

    private EntityDamageByEntityEvent hit(Entity source, DamageType type) {
        return new EntityDamageByEntityEvent(source, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK,
            DamageSource.builder(type).withCausingEntity(source).withDirectEntity(source).build(), 8);
    }

    private EntityDamageEvent explosion(CombatItemAction action, Entity target, boolean credited) {
        if (action == CombatItemAction.BEDS || action == CombatItemAction.RESPAWN_ANCHORS) {
            world.setEnvironment(action == CombatItemAction.BEDS ? World.Environment.NETHER : World.Environment.NORMAL);
            Block block = world.getBlockAt(10, 64, 10);
            block.setType(action == CombatItemAction.BEDS ? Material.RED_BED : Material.RESPAWN_ANCHOR);
            if (credited) server.getPluginManager().callEvent(new PlayerInteractEvent(attacker,
                Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND));
            Location source = new Location(null, 10, 64, 10);
            return new EntityDamageEvent(target, EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
                DamageSource.builder(DamageType.BAD_RESPAWN_POINT).withDamageLocation(source).build(), 8);
        }
        Entity entity = switch (action) {
            case TNT -> world.spawn(world.getSpawnLocation(), TNTPrimed.class);
            case TNT_MINECARTS -> world.spawn(world.getSpawnLocation(), ExplosiveMinecart.class);
            case END_CRYSTALS -> world.spawn(world.getSpawnLocation(), EnderCrystal.class);
            default -> throw new IllegalArgumentException();
        };
        if (credited) {
            if (entity instanceof TNTPrimed tnt) tnt.setSource(attacker);
            else server.getPluginManager().callEvent(new EntityDamageByEntityEvent(attacker, entity,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(attacker).build(), 1));
        }
        return new EntityDamageEvent(target, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,
            DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(entity).build(), 8);
    }
}
