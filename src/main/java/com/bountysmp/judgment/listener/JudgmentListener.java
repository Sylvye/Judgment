package com.bountysmp.judgment.listener;

import com.bountysmp.judgment.gui.SettingsGui;
import com.bountysmp.judgment.gui.SettingsMenuHolder;
import com.bountysmp.judgment.gui.DragonEggMenuHolder;
import com.bountysmp.judgment.gui.CombatItemMenuHolder;
import com.bountysmp.judgment.service.JudgmentService;
import com.bountysmp.judgment.pvp.DragonEggPrivilege;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class JudgmentListener implements Listener {
    private final JudgmentService judgmentService;
    private final SettingsGui settingsGui;
    private final DragonEggPrivilege dragonEggPrivilege;

    public JudgmentListener(JudgmentService judgmentService, SettingsGui settingsGui, DragonEggPrivilege dragonEggPrivilege) {
        this.judgmentService = judgmentService;
        this.settingsGui = settingsGui;
        this.dragonEggPrivilege = dragonEggPrivilege;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        judgmentService.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        judgmentService.handleDeath(event.getEntity());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        judgmentService.handleJoin(event.getPlayer());
        dragonEggPrivilege.refresh(event.getPlayer());
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (settingsGui.handleChat(event.getPlayer(), message)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTask(judgmentService.getPlugin(), () -> dragonEggPrivilege.refresh(player));
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsMenuHolder)
            && !(event.getView().getTopInventory().getHolder() instanceof DragonEggMenuHolder)) {
            if (!(event.getView().getTopInventory().getHolder() instanceof CombatItemMenuHolder)) return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        settingsGui.handleClick(player, event.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Bukkit.getScheduler().runTask(judgmentService.getPlugin(), () -> dragonEggPrivilege.refresh(player));
        }
        if (event.getView().getTopInventory().getHolder() instanceof SettingsMenuHolder
            || event.getView().getTopInventory().getHolder() instanceof DragonEggMenuHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                Bukkit.getScheduler().runTask(judgmentService.getPlugin(), () -> dragonEggPrivilege.refresh(player));
            }
        } else if (event.getView().getTopInventory().getHolder() instanceof CombatItemMenuHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                Bukkit.getScheduler().runTask(judgmentService.getPlugin(), () -> dragonEggPrivilege.refresh(player));
            }
        }
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        Bukkit.getScheduler().runTask(judgmentService.getPlugin(), () -> dragonEggPrivilege.refresh(event.getPlayer()));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Bukkit.getScheduler().runTask(judgmentService.getPlugin(), () -> dragonEggPrivilege.refresh(event.getPlayer()));
    }

}
