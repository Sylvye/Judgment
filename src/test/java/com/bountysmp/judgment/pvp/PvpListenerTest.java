package com.bountysmp.judgment.pvp;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.model.PunishmentMode;
import com.bountysmp.judgment.service.JudgmentService;
import com.bountysmp.judgment.storage.PendingKill;
import com.bountysmp.judgment.storage.PendingKillStore;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.bukkit.plugin.Plugin;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PvpListenerTest {
    @TempDir Path directory;
    ServerMock server;
    WorldMock world;
    Plugin plugin;
    PlayerMock a, b;
    PvpService pvp;
    JudgmentService combat;
    PvpListener listener;
    PendingKillStore pending;
    AtomicLong now = new AtomicLong(1_000);

    @BeforeEach void setup() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        plugin = MockBukkit.createMockPlugin();
        a = server.addPlayer("Attacker");
        b = server.addPlayer("Victim");
        pending = new PendingKillStore(directory.resolve("pending.yml"));
        combat = new JudgmentService(plugin, new JudgmentSettings(30_000, 10_000, PunishmentMode.RELOG), pending, now::get);
        pvp = new PvpService(new PvpStore(directory.resolve("pvp.yml")), () -> new PvpSettings(false, 0, 0),
            now::get, id -> combat.getCombatTag(id).isPresent(), plugin.getLogger());
        listener = new PvpListener(plugin, pvp, combat, new PvpPresentation(pvp));
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach void cleanup() { MockBukkit.unmock(); }

    EntityDamageByEntityEvent hit(Entity direct, Entity cause, double damage) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(direct).withCausingEntity(cause).build();
        return new EntityDamageByEntityEvent(direct, b, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, damage);
    }

    void enableBoth() {
        pvp.change(a.getUniqueId(), true);
        pvp.change(b.getUniqueId(), true);
    }

    @Test void damagePermissionMatrixAndSuccessfulCombatTag() {
        var offOff = hit(a, a, 2);
        server.getPluginManager().callEvent(offOff);
        assertTrue(offOff.isCancelled());
        pvp.change(a.getUniqueId(), true);
        var onOff = hit(a, a, 2);
        server.getPluginManager().callEvent(onOff);
        assertTrue(onOff.isCancelled());
        pvp.change(a.getUniqueId(), false);
        pvp.change(b.getUniqueId(), true);
        var offOn = hit(a, a, 2);
        server.getPluginManager().callEvent(offOn);
        assertTrue(offOn.isCancelled());
        assertTrue(combat.getCombatStack(a.getUniqueId()).isEmpty());
        enableBoth();
        var onOn = hit(a, a, 2);
        server.getPluginManager().callEvent(onOn);
        assertFalse(onOn.isCancelled());
        assertEquals(1, combat.getCombatStack(a.getUniqueId()).size());
        assertEquals(1, combat.getCombatStack(b.getUniqueId()).size());
    }

    @Test void cancelledAndZeroDamageDoNotTag() {
        enableBoth();
        var cancelled = hit(a, a, 2);
        cancelled.setCancelled(true);
        server.getPluginManager().callEvent(cancelled);
        server.getPluginManager().callEvent(hit(a, a, 0));
        assertTrue(combat.getCombatStack(a.getUniqueId()).isEmpty());
    }

    @Test void projectilesResolveShooterEvenWhenOffline() {
        Arrow arrow = world.spawn(world.getSpawnLocation(), Arrow.class);
        arrow.setShooter(a);
        assertEquals(a.getUniqueId(), listener.responsiblePlayer(arrow));
        a.disconnect();
        var event = hit(arrow, a, 2);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled());
    }

    @Test void petsResolveOwnersAndExplosionAttributionIsProtected() {
        Wolf wolf = world.spawn(world.getSpawnLocation(), Wolf.class);
        wolf.setOwner(a);
        assertEquals(a.getUniqueId(), listener.responsiblePlayer(wolf));
        var pet = hit(wolf, wolf, 2);
        server.getPluginManager().callEvent(pet);
        assertTrue(pet.isCancelled());
        DamageSource source = DamageSource.builder(DamageType.PLAYER_EXPLOSION).withCausingEntity(a).build();
        var explosion = new EntityDamageEvent(b, EntityDamageEvent.DamageCause.ENTITY_EXPLOSION, source, 5);
        server.getPluginManager().callEvent(explosion);
        assertTrue(explosion.isCancelled());
    }

    @Test void fireAndKnockbackAreBlockedButEnvironmentalDamageIsNot() {
        var fire = new EntityCombustByEntityEvent(a, b, 5.0f);
        server.getPluginManager().callEvent(fire);
        assertTrue(fire.isCancelled());
        var push = new EntityPushedByEntityAttackEvent(b, EntityKnockbackEvent.Cause.ENTITY_ATTACK, a, new Vector(1, 0, 0));
        server.getPluginManager().callEvent(push);
        assertTrue(push.isCancelled());
        var fall = new EntityDamageEvent(b, EntityDamageEvent.DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), 2);
        server.getPluginManager().callEvent(fall);
        assertFalse(fall.isCancelled());
        var self = new EntityDamageByEntityEvent(b, b, EntityDamageEvent.DamageCause.PROJECTILE,
            DamageSource.builder(DamageType.ARROW).withCausingEntity(b).build(), 1);
        server.getPluginManager().callEvent(self);
        assertFalse(self.isCancelled());
    }

    SplashPotion potion(PotionType type) {
        SplashPotion potion = world.spawn(world.getSpawnLocation(), SplashPotion.class);
        potion.setShooter(a);
        var meta = potion.getPotionMeta();
        meta.setBasePotionType(type);
        // MockBukkit's getEffects currently exposes only custom effects, unlike Paper.
        type.getPotionEffects().forEach(effect -> meta.addCustomEffect(effect, true));
        potion.setPotionMeta(meta);
        return potion;
    }

    @Test void harmfulSplashFiltersOnlyProtectedPlayersAndBeneficialPotionsRemain() {
        enableBoth();
        PlayerMock protectedPlayer = server.addPlayer("Protected");
        var event = new PotionSplashEvent(potion(PotionType.POISON), null, null, null,
            new HashMap<>(Map.of(b, 1.0, protectedPlayer, 1.0)));
        server.getPluginManager().callEvent(event);
        assertEquals(0, event.getIntensity(protectedPlayer));
        assertEquals(1, event.getIntensity(b));
        assertTrue(combat.getCombatTag(b.getUniqueId()).isPresent());
        assertTrue(combat.getCombatTag(protectedPlayer.getUniqueId()).isEmpty());
        var healing = new PotionSplashEvent(potion(PotionType.HEALING), null, null, null,
            new HashMap<>(Map.of(protectedPlayer, 1.0)));
        server.getPluginManager().callEvent(healing);
        assertEquals(1, healing.getIntensity(protectedPlayer));
    }

    @Test void lingeringCloudFiltersTargetsAndCancelledSplashDoesNotTag() {
        enableBoth();
        PlayerMock protectedPlayer = server.addPlayer("Protected");
        AreaEffectCloud cloud = world.spawn(world.getSpawnLocation(), AreaEffectCloud.class);
        cloud.setSource(a);
        cloud.addCustomEffect(new PotionEffect(PotionEffectType.WITHER, 200, 0), true);
        var event = new AreaEffectCloudApplyEvent(cloud, new ArrayList<>(List.of(b, protectedPlayer)));
        server.getPluginManager().callEvent(event);
        assertEquals(List.of(b), event.getAffectedEntities());
        assertTrue(combat.getCombatTag(b.getUniqueId()).isPresent());
        var splash = new PotionSplashEvent(potion(PotionType.POISON), null, null, null,
            new HashMap<>(Map.of(protectedPlayer, 1.0)));
        splash.setCancelled(true);
        server.getPluginManager().callEvent(splash);
        assertTrue(combat.getCombatTag(protectedPlayer.getUniqueId()).isEmpty());
    }

    @Test void approvedPunishmentBypassesPvpGateAndNeverCreatesCombatTags() {
        pending.put(new PendingKill(b.getUniqueId(), b.getName(), a.getUniqueId(), a.getName(), now.get()));
        assertTrue(combat.attemptPendingKill(b.getUniqueId()));
        assertTrue(b.isDead() || b.getHealth() == 0);
        assertTrue(combat.getCombatStack(a.getUniqueId()).isEmpty());
        assertFalse(combat.isExecutingPunishment(b.getUniqueId()));
        assertTrue(pending.values().isEmpty());
    }
}
