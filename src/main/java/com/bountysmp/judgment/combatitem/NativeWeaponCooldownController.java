package com.bountysmp.judgment.combatitem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NativeWeaponCooldownController implements AutoCloseable, Listener {
    private final CombatItemCooldownManager cooldowns;
    private final Map<UUID, EnumSet<CombatItemAction>> applied = new HashMap<>();
    private final BukkitTask task;

    public NativeWeaponCooldownController(Plugin plugin, CombatItemCooldownManager cooldowns) {
        this.cooldowns = cooldowns;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::refresh, 1L, 2L);
    }

    void refresh() {
        for (Player player : Bukkit.getOnlinePlayers()) refresh(player);
        applied.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    void refresh(Player player) {
        Map<CombatItemAction, Long> active = cooldowns.activeCooldowns(player.getUniqueId());
        EnumSet<CombatItemAction> playerApplied = applied.computeIfAbsent(player.getUniqueId(),
            ignored -> EnumSet.noneOf(CombatItemAction.class));
        for (CombatItemAction action : CombatItemAction.values()) {
            if (!action.usesNativeCooldown()) continue;
            Long remaining = active.get(action);
            if (remaining != null) {
                if (!playerApplied.contains(action)) apply(player, action, remaining);
            } else if (playerApplied.remove(action)) {
                clear(player, action);
            }
        }
        if (playerApplied.isEmpty()) applied.remove(player.getUniqueId());
    }

    void apply(Player player, CombatItemAction action, long remainingMillis) {
        int ticks = ticks(remainingMillis);
        action.nativeCooldownMaterials().forEach(material -> player.setCooldown(material, ticks));
        if (action.usesNativeCooldown())
            applied.computeIfAbsent(player.getUniqueId(), ignored -> EnumSet.noneOf(CombatItemAction.class)).add(action);
    }

    void apply(Player player, CombatItemAction action, ItemStack item, long remainingMillis) {
        player.setCooldown(item, ticks(remainingMillis));
        applied.computeIfAbsent(player.getUniqueId(), ignored -> EnumSet.noneOf(CombatItemAction.class)).add(action);
    }

    public void resynchronize(CombatItemAction action) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            EnumSet<CombatItemAction> playerApplied = applied.get(player.getUniqueId());
            if (playerApplied != null && playerApplied.remove(action)) clear(player, action);
            refresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        refresh(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        clearApplied(event.getPlayer());
    }

    private void clearApplied(Player player) {
        EnumSet<CombatItemAction> playerApplied = applied.remove(player.getUniqueId());
        if (playerApplied != null)
            playerApplied.forEach(action -> clear(player, action));
    }

    private static int ticks(long remainingMillis) {
        long ticksLong = remainingMillis / 50L + (remainingMillis % 50L == 0L ? 0L : 1L);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, ticksLong));
    }

    private static void clear(Player player, CombatItemAction action) {
        action.nativeCooldownMaterials().forEach(material -> player.setCooldown(material, 0));
    }

    @Override
    public void close() {
        task.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) clearApplied(player);
        applied.clear();
    }
}
