package com.bountysmp.judgment.gui;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.util.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public final class SettingsGui {
    private static final int INVENTORY_SIZE = 27;
    private static final int COMBAT_TAG_SLOT = 10;
    private static final int MODE_SLOT = 13;
    private static final int PROMPT_TIMEOUT_SLOT = 16;

    private final Plugin plugin;
    private final Supplier<JudgmentSettings> settingsSupplier;
    private final LongConsumer combatTagUpdater;
    private final LongConsumer promptTimeoutUpdater;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public SettingsGui(
        Plugin plugin,
        Supplier<JudgmentSettings> settingsSupplier,
        LongConsumer combatTagUpdater,
        LongConsumer promptTimeoutUpdater
    ) {
        this.plugin = plugin;
        this.settingsSupplier = settingsSupplier;
        this.combatTagUpdater = combatTagUpdater;
        this.promptTimeoutUpdater = promptTimeoutUpdater;
    }

    public void open(Player admin) {
        if (!admin.hasPermission("judgment.admin")) {
            admin.sendMessage(Component.text("You do not have permission to manage Judgment.", NamedTextColor.RED));
            return;
        }

        JudgmentSettings settings = settingsSupplier.get();
        SettingsMenuHolder holder = new SettingsMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Component.text("Judgment Settings", NamedTextColor.GOLD));
        holder.setInventory(inventory);

        inventory.setItem(COMBAT_TAG_SLOT, GuiItems.namedItem(
            Material.CLOCK,
            Component.text("Combat Tag: " + DurationParser.formatMillis(settings.combatTagMillis()), NamedTextColor.YELLOW),
            List.of(Component.text("Click to edit in chat.", NamedTextColor.GRAY))
        ));
        inventory.setItem(MODE_SLOT, GuiItems.namedItem(
            Material.IRON_SWORD,
            Component.text("Mode: " + settings.effectivePunishmentMode().displayName(), NamedTextColor.AQUA),
            List.of(Component.text("Instant is reserved for a future release.", NamedTextColor.GRAY))
        ));
        inventory.setItem(PROMPT_TIMEOUT_SLOT, GuiItems.namedItem(
            Material.REPEATER,
            Component.text("Prompt Timeout: " + DurationParser.formatMillis(settings.promptTimeoutMillis()), NamedTextColor.GREEN),
            List.of(Component.text("Click to edit in chat.", NamedTextColor.GRAY))
        ));

        admin.openInventory(inventory);
    }

    public void handleClick(Player admin, int rawSlot) {
        if (!admin.hasPermission("judgment.admin")) {
            admin.closeInventory();
            return;
        }

        if (rawSlot == COMBAT_TAG_SLOT) {
            pendingInputs.put(admin.getUniqueId(), PendingInput.COMBAT_TAG_DURATION);
            admin.closeInventory();
            admin.sendMessage(Component.text("Type a duration like 0s, 30s, 5m, 1h, or cancel.", NamedTextColor.YELLOW));
            return;
        }

        if (rawSlot == PROMPT_TIMEOUT_SLOT) {
            pendingInputs.put(admin.getUniqueId(), PendingInput.PROMPT_TIMEOUT_DURATION);
            admin.closeInventory();
            admin.sendMessage(Component.text("Type a duration like 1s, 10s, 5m, 1h, or cancel.", NamedTextColor.YELLOW));
            return;
        }

        if (rawSlot == MODE_SLOT) {
            admin.sendMessage(Component.text("Instant mode is reserved for a future release. Relog remains active.", NamedTextColor.YELLOW));
        }
    }

    public boolean handleChat(Player admin, String message) {
        PendingInput pendingInput = pendingInputs.remove(admin.getUniqueId());
        if (pendingInput == null) {
            return false;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                admin.sendMessage(Component.text("Cancelled setting change.", NamedTextColor.YELLOW));
                open(admin);
                return;
            }

            Long millis = DurationParser.parseMillis(message);
            if (millis == null) {
                admin.sendMessage(Component.text("Use a duration like 0s, 30s, 5m, 1h, or cancel.", NamedTextColor.RED));
                pendingInputs.put(admin.getUniqueId(), pendingInput);
                return;
            }

            if (pendingInput == PendingInput.COMBAT_TAG_DURATION) {
                combatTagUpdater.accept(millis);
                admin.sendMessage(Component.text("Updated combat tag duration.", NamedTextColor.GREEN));
                open(admin);
            }
            if (pendingInput == PendingInput.PROMPT_TIMEOUT_DURATION) {
                promptTimeoutUpdater.accept(millis);
                admin.sendMessage(Component.text("Updated prompt timeout.", NamedTextColor.GREEN));
                open(admin);
            }
        });
        return true;
    }

    private enum PendingInput {
        COMBAT_TAG_DURATION,
        PROMPT_TIMEOUT_DURATION
    }
}
