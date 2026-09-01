package com.bountysmp.judgment.combatitem;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.model.CombatTag;
import com.bountysmp.judgment.service.JudgmentService;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class CombatBossBarController implements AutoCloseable {
    private final JudgmentService combat;
    private final CombatItemCooldownManager cooldowns;
    private final Supplier<CombatItemSettings> itemSettings;
    private final Supplier<JudgmentSettings> settings;
    private final Map<UUID, Map<CombatItemAction, BossBar>> itemBars = new HashMap<>();
    private final Map<UUID, BossBar> combatBars = new HashMap<>();
    private final BukkitTask task;

    public CombatBossBarController(Plugin plugin, JudgmentService combat, CombatItemCooldownManager cooldowns,
                                   Supplier<CombatItemSettings> itemSettings,
                                   Supplier<JudgmentSettings> settings) {
        this.combat = combat;
        this.cooldowns = cooldowns;
        this.itemSettings = itemSettings;
        this.settings = settings;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1L, 2L);
    }

    void refresh() {
        cooldowns.clearPlayersOutsideCombatAndSave();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshItems(player);
            refreshCombat(player);
        }
        itemBars.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        combatBars.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
    }

    private void refreshItems(Player player) {
        if (!settings.get().itemCooldownBossBars()) {
            hideItemBars(player);
            return;
        }
        Map<CombatItemAction, Long> active = cooldowns.activeCooldowns(player.getUniqueId());
        Map<CombatItemAction, BossBar> bars = itemBars.computeIfAbsent(player.getUniqueId(),
            ignored -> new EnumMap<>(CombatItemAction.class));
        for (CombatItemAction action : CombatItemAction.values()) {
            Long remaining = active.get(action);
            BossBar existing = bars.get(action);
            if (remaining == null) {
                if (existing != null) player.hideBossBar(existing);
                bars.remove(action);
                continue;
            }
            long duration = Math.max(1L, Math.round(itemSettings.get().seconds(action) * 1_000.0));
            float progress = clamp((float) remaining / duration);
            Component name = Component.text(action.displayName() + ": " + CombatItemListener.formatSeconds(remaining) + "s");
            if (existing == null) {
                existing = BossBar.bossBar(name, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                bars.put(action, existing);
                player.showBossBar(existing);
            } else {
                existing.name(name);
                existing.progress(progress);
            }
        }
        if (bars.isEmpty()) itemBars.remove(player.getUniqueId());
    }

    private void refreshCombat(Player player) {
        if (!settings.get().combatTimerBossBar()) {
            hideCombatBar(player);
            return;
        }
        CombatTag tag = combat.getCombatTag(player.getUniqueId()).orElse(null);
        if (tag == null) {
            hideCombatBar(player);
            return;
        }
        long remaining = Math.max(0L, tag.expiresAtMillis() - System.currentTimeMillis());
        long duration = Math.max(1L, combat.settings().combatTagMillis());
        float progress = clamp((float) remaining / duration);
        Component name = Component.text("Combat: " + CombatItemListener.formatSeconds(remaining) + "s");
        BossBar bar = combatBars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(name, progress, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
            combatBars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(name);
            bar.progress(progress);
        }
    }

    private void hideItemBars(Player player) {
        Map<CombatItemAction, BossBar> bars = itemBars.remove(player.getUniqueId());
        if (bars != null) bars.values().forEach(player::hideBossBar);
    }

    private void hideCombatBar(Player player) {
        BossBar bar = combatBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    private static float clamp(float value) { return Math.max(0.0f, Math.min(1.0f, value)); }

    @Override public void close() {
        task.cancel();
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideItemBars(player);
            hideCombatBar(player);
        }
    }
}
