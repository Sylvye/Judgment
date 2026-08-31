package com.bountysmp.judgment.gui;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.util.DurationParser;
import com.bountysmp.judgment.pvp.PvpSettings;
import com.bountysmp.judgment.pvp.DragonEggSettings;
import java.util.function.Consumer;
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

    private static final int PVP_DEFAULT_SLOT = 20;
    private static final int PVP_COOLDOWN_SLOT = 22;
    private static final int PVP_DELAY_SLOT = 24;
    private static final int DRAGON_EGG_SLOT = 26;
    private final Supplier<PvpSettings> pvpSettings;
    private final Consumer<PvpSettings> pvpUpdater;
    private final Supplier<DragonEggSettings> dragonEggSettings;
    private final Consumer<DragonEggSettings> dragonEggUpdater;
    private final Plugin plugin;
    private final Supplier<JudgmentSettings> settingsSupplier;
    private final LongConsumer combatTagUpdater;
    private final LongConsumer promptTimeoutUpdater;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();

    public SettingsGui(
        Plugin plugin,
        Supplier<JudgmentSettings> settingsSupplier,
        LongConsumer combatTagUpdater,
        LongConsumer promptTimeoutUpdater,
        Supplier<PvpSettings> pvpSettings,
        Consumer<PvpSettings> pvpUpdater,
        Supplier<DragonEggSettings> dragonEggSettings,
        Consumer<DragonEggSettings> dragonEggUpdater
    ) {
        this.pvpSettings = pvpSettings;
        this.pvpUpdater = pvpUpdater;
        this.dragonEggSettings = dragonEggSettings;
        this.dragonEggUpdater = dragonEggUpdater;
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

        PvpSettings pvp = pvpSettings.get();
        inventory.setItem(PVP_DEFAULT_SLOT, GuiItems.namedItem(Material.LEVER,
            Component.text("Default PvP: " + (pvp.defaultEnabled() ? "ON" : "OFF"), NamedTextColor.RED),
            List.of(Component.text("Click to toggle. Applies to new preferences only."))));
        inventory.setItem(PVP_COOLDOWN_SLOT, GuiItems.namedItem(Material.CLOCK,
            Component.text("PvP Toggle Cooldown: " + DurationParser.formatMillis(pvp.toggleCooldownMillis()), NamedTextColor.YELLOW),
            List.of(Component.text("Click to edit in chat."))));
        inventory.setItem(PVP_DELAY_SLOT, GuiItems.namedItem(Material.SHIELD,
            Component.text("PvP Post-Combat Wait: " + DurationParser.formatMillis(pvp.postCombatDelayMillis()), NamedTextColor.GREEN),
            List.of(Component.text("Starts when the last combat tag ends."), Component.text("Click to edit in chat."))));
        inventory.setItem(DRAGON_EGG_SLOT, GuiItems.namedItem(Material.DRAGON_EGG,
            Component.text("Dragon Egg", NamedTextColor.LIGHT_PURPLE),
            List.of(Component.text("Open dragon egg privilege settings."))));
        admin.openInventory(inventory);
    }

    public void openDragonEgg(Player admin) {
        if (!admin.hasPermission("judgment.admin")) return;
        DragonEggMenuHolder holder = new DragonEggMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Component.text("Dragon Egg Settings", NamedTextColor.LIGHT_PURPLE));
        holder.setInventory(inventory);
        DragonEggSettings s = dragonEggSettings.get();
        inventory.setItem(11, GuiItems.namedItem(Material.DRAGON_EGG, Component.text("Feature: " + onOff(s.enabled()), NamedTextColor.LIGHT_PURPLE), List.of(Component.text("Click to toggle."))));
        inventory.setItem(13, GuiItems.namedItem(Material.GLOWSTONE_DUST, Component.text("Glow: " + onOff(s.glow()), NamedTextColor.YELLOW), List.of(Component.text("Click to toggle."))));
        inventory.setItem(15, GuiItems.namedItem(Material.POTION, Component.text("Strength I: " + onOff(s.strength()), NamedTextColor.RED), List.of(Component.text("Click to toggle."))));
        inventory.setItem(17, GuiItems.namedItem(Material.SUGAR, Component.text("Speed I: " + onOff(s.speed()), NamedTextColor.AQUA), List.of(Component.text("Click to toggle."))));
        inventory.setItem(22, GuiItems.namedItem(Material.BARRIER, Component.text("Back", NamedTextColor.GRAY), List.of(Component.text("Return to Judgment settings."))));
        admin.openInventory(inventory);
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }

    public void handleClick(Player admin, int rawSlot) {
        if (!admin.hasPermission("judgment.admin")) {
            admin.closeInventory();
            return;
        }

        if (admin.getOpenInventory().getTopInventory().getHolder() instanceof DragonEggMenuHolder) {
            DragonEggSettings old = dragonEggSettings.get();
            DragonEggSettings updated = switch (rawSlot) {
                case 11 -> new DragonEggSettings(!old.enabled(), old.glow(), old.strength(), old.speed());
                case 13 -> new DragonEggSettings(old.enabled(), !old.glow(), old.strength(), old.speed());
                case 15 -> new DragonEggSettings(old.enabled(), old.glow(), !old.strength(), old.speed());
                case 17 -> new DragonEggSettings(old.enabled(), old.glow(), old.strength(), !old.speed());
                default -> null;
            };
            if (updated != null) {
                dragonEggUpdater.accept(updated);
                openDragonEgg(admin);
            } else if (rawSlot == 22) {
                open(admin);
            }
            return;
        }

        if (rawSlot == PVP_DEFAULT_SLOT) {
            PvpSettings old = pvpSettings.get();
            pvpUpdater.accept(new PvpSettings(!old.defaultEnabled(), old.toggleCooldownMillis(), old.postCombatDelayMillis()));
            open(admin);
            return;
        }
        if (rawSlot == DRAGON_EGG_SLOT) {
            openDragonEgg(admin);
            return;
        }
        if (rawSlot == PVP_COOLDOWN_SLOT || rawSlot == PVP_DELAY_SLOT) {
            pendingInputs.put(admin.getUniqueId(), rawSlot == PVP_COOLDOWN_SLOT ? PendingInput.PVP_COOLDOWN : PendingInput.PVP_DELAY);
            admin.closeInventory();
            admin.sendMessage(Component.text("Type a duration like 0s, 10m, 24h, or cancel.", NamedTextColor.YELLOW));
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

            if (!admin.isOnline() || !admin.hasPermission("judgment.admin")) return;
            if (pendingInput == PendingInput.PVP_COOLDOWN || pendingInput == PendingInput.PVP_DELAY) {
                PvpSettings old = pvpSettings.get();
                pvpUpdater.accept(new PvpSettings(old.defaultEnabled(),
                    pendingInput == PendingInput.PVP_COOLDOWN ? millis : old.toggleCooldownMillis(),
                    pendingInput == PendingInput.PVP_DELAY ? millis : old.postCombatDelayMillis()));
                admin.sendMessage(Component.text("Updated PvP settings.", NamedTextColor.GREEN));
                open(admin);
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
        PROMPT_TIMEOUT_DURATION,
        PVP_COOLDOWN,
        PVP_DELAY
    }
}
