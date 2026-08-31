package com.bountysmp.judgment.pvp;

import com.bountysmp.judgment.service.JudgmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PvpListener implements Listener {
    private final Plugin plugin;
    private final PvpService pvp;
    private final JudgmentService combat;
    private final PvpPresentation presentation;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public PvpListener(Plugin plugin, PvpService pvp, JudgmentService combat, PvpPresentation presentation) {
        this.plugin = plugin;
        this.pvp = pvp;
        this.combat = combat;
        this.presentation = presentation;
    }

    public UUID responsiblePlayer(Entity entity) {
        return responsiblePlayer(entity, 0);
    }

    private UUID responsiblePlayer(Entity entity, int depth) {
        if (entity == null || depth > 8) return null;
        if (entity instanceof Player player) return player.getUniqueId();
        if (entity instanceof Tameable pet && pet.getOwner() != null) return pet.getOwner().getUniqueId();
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)
            return responsiblePlayer(shooter, depth + 1);
        if (entity instanceof TNTPrimed tnt) return responsiblePlayer(tnt.getSource(), depth + 1);
        if (entity instanceof AreaEffectCloud cloud) {
            if (cloud.getSource() instanceof Entity source) return responsiblePlayer(source, depth + 1);
            return cloud.getOwnerUniqueId();
        }
        return null;
    }

    private UUID attacker(EntityDamageEvent event) {
        UUID id = responsiblePlayer(event.getDamageSource().getCausingEntity());
        if (id == null) id = responsiblePlayer(event.getDamageSource().getDirectEntity());
        if (id == null && event instanceof EntityDamageByEntityEvent byEntity) id = responsiblePlayer(byEntity.getDamager());
        return id;
    }

    private boolean blocked(UUID attacker, Player victim) {
        if (attacker == null || combat.isExecutingPunishment(victim.getUniqueId())) return false;
        if (pvp.canAttack(attacker, victim.getUniqueId())) return false;
        Player player = Bukkit.getPlayer(attacker);
        long now = System.currentTimeMillis();
        if (player != null && now - lastNotice.getOrDefault(attacker, 0L) >= 2_000) {
            player.sendActionBar(Component.text("Both players must have PvP ON to fight. Use /pvp.", NamedTextColor.RED));
            lastNotice.put(attacker, now);
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && blocked(attacker(event), victim)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim && event.getFinalDamage() > 0) record(attacker(event), victim);
    }

    private void record(UUID attacker, Player victim) {
        if (attacker == null || attacker.equals(victim.getUniqueId()) || combat.isExecutingPunishment(victim.getUniqueId())
            || !pvp.canAttack(attacker, victim.getUniqueId())) return;
        pvp.recordCombat(attacker, victim.getUniqueId(), combat.settings().combatTagMillis());
        Player player = Bukkit.getPlayer(attacker);
        if (player != null) combat.recordPvpDamage(victim, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectIgnition(EntityCombustByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && blocked(responsiblePlayer(event.getCombuster()), victim)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectKnockback(EntityPushedByEntityAttackEvent event) {
        if (event.getEntity() instanceof Player victim && blocked(responsiblePlayer(event.getPushedBy()), victim)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectSplash(PotionSplashEvent event) {
        if (!harmful(event.getPotion().getEffects())) return;
        UUID attacker = responsiblePlayer(event.getPotion());
        for (LivingEntity entity : java.util.List.copyOf(event.getAffectedEntities())) {
            if (entity instanceof Player victim && blocked(attacker, victim)) event.setIntensity(victim, 0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordSplash(PotionSplashEvent event) {
        if (!harmful(event.getPotion().getEffects())) return;
        for (LivingEntity entity : java.util.List.copyOf(event.getAffectedEntities())) {
            if (entity instanceof Player victim && event.getIntensity(victim) > 0) record(responsiblePlayer(event.getPotion()), victim);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectCloud(AreaEffectCloudApplyEvent event) {
        if (!harmfulCloud(event.getEntity())) return;
        UUID attacker = responsiblePlayer(event.getEntity());
        event.getAffectedEntities().removeIf(entity -> entity instanceof Player victim && blocked(attacker, victim));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordCloud(AreaEffectCloudApplyEvent event) {
        if (!harmfulCloud(event.getEntity())) return;
        for (LivingEntity entity : java.util.List.copyOf(event.getAffectedEntities())) {
            if (entity instanceof Player victim) record(responsiblePlayer(event.getEntity()), victim);
        }
    }

    private boolean harmfulCloud(AreaEffectCloud cloud) {
        return harmful(cloud.getCustomEffects()) || (cloud.getBasePotionType() != null && harmful(cloud.getBasePotionType().getPotionEffects()));
    }

    private boolean harmful(Collection<PotionEffect> effects) {
        return effects.stream().anyMatch(effect -> effect.getType().getEffectCategory() != PotionEffectType.Category.BENEFICIAL);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        pvp.initialize(event.getPlayer().getUniqueId());
        Bukkit.getScheduler().runTask(plugin, presentation::refreshAll);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        presentation.remove(event.getPlayer());
        lastNotice.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        pvp.handleDeath(event.getEntity().getUniqueId());
    }
}
