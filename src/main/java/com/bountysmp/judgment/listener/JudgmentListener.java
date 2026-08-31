package com.bountysmp.judgment.listener;

import com.bountysmp.judgment.gui.SettingsGui;
import com.bountysmp.judgment.gui.SettingsMenuHolder;
import com.bountysmp.judgment.service.JudgmentService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JudgmentListener implements Listener {
    private final JudgmentService judgmentService;
    private final SettingsGui settingsGui;

    public JudgmentListener(JudgmentService judgmentService, SettingsGui settingsGui) {
        this.judgmentService = judgmentService;
        this.settingsGui = settingsGui;
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
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        settingsGui.handleClick(player, event.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SettingsMenuHolder) {
            event.setCancelled(true);
        }
    }

}
