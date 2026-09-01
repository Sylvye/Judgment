package com.bountysmp.judgment.combatitem;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.papermc.paper.event.entity.EntityLungeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatItemListener implements Listener {
    private static final long NOTICE_INTERVAL_MILLIS = 500L;
    private final CombatItemCooldownManager cooldowns;
    private final Plugin plugin;
    private final Map<NoticeKey, Long> lastNotices = new HashMap<>();

    public CombatItemListener(Plugin plugin, CombatItemCooldownManager cooldowns) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player)
            enforce(player, CombatItemAction.ELYTRA, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFireworkBoost(PlayerElytraBoostEvent event) {
        enforce(event.getPlayer(), CombatItemAction.FIREWORKS, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectile(PlayerLaunchProjectileEvent event) {
        if (event.getProjectile() instanceof EnderPearl)
            enforce(event.getPlayer(), CombatItemAction.ENDER_PEARLS, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceSmash(EntityDamageEvent event) {
        if (!event.getDamageSource().getDamageType().equals(DamageType.MACE_SMASH)) return;
        if (event.getDamageSource().getCausingEntity() instanceof Player player)
            enforce(player, CombatItemAction.MACE_SMASH, event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordMaceSmashThatStartedCombat(EntityDamageEvent event) {
        if (!event.getDamageSource().getDamageType().equals(DamageType.MACE_SMASH)) return;
        if (event.getDamageSource().getCausingEntity() instanceof Player player)
            cooldowns.recordFirstCombatUse(player.getUniqueId(), CombatItemAction.MACE_SMASH);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        Vector priorVelocity = player.getVelocity().clone();
        enforce(player, CombatItemAction.RIPTIDE, event);
        if (event.isCancelled()) {
            stopRiptide(player, priorVelocity);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> stopRiptide(player, priorVelocity));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLunge(EntityLungeEvent event) {
        if (event.getEntity() instanceof Player player) enforce(player, CombatItemAction.LUNGE, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (event.getEntity() instanceof EnderCrystal)
            enforce(player, CombatItemAction.END_CRYSTALS, event);
        else if (event.getEntity() instanceof ExplosiveMinecart)
            enforce(player, CombatItemAction.TNT_MINECARTS, event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == org.bukkit.Material.RESPAWN_ANCHOR)
            enforce(event.getPlayer(), CombatItemAction.RESPAWN_ANCHORS, event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.clearPvpCooldowns(event.getPlayer().getUniqueId());
    }

    private static void stopRiptide(Player player, Vector priorVelocity) {
        player.setRiptiding(false);
        player.setVelocity(priorVelocity);
    }

    private void enforce(Player player, CombatItemAction action, Cancellable event) {
        CombatItemCooldownManager.Result result = cooldowns.attempt(player.getUniqueId(), action);
        if (result.allowed()) return;
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        NoticeKey key = new NoticeKey(player.getUniqueId(), action);
        if (now - lastNotices.getOrDefault(key, 0L) < NOTICE_INTERVAL_MILLIS) return;
        lastNotices.put(key, now);
        String message = result.outcome() == CombatItemCooldownManager.Outcome.BANNED
            ? action.displayName() + (cooldowns.scope(action) == CombatItemScope.GLOBAL
                ? " are globally banned." : " are banned during combat.")
            : action.displayName() + " ready in " + formatSeconds(result.remainingMillis()) + "s.";
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }

    static String formatSeconds(long millis) {
        return String.format(java.util.Locale.ROOT, "%.1f", Math.ceil(millis / 100.0) / 10.0);
    }

    private record NoticeKey(UUID playerId, CombatItemAction action) {}
}
