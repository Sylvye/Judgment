package com.bountysmp.judgment.gui;

import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.combatitem.CombatItemAction;
import com.bountysmp.judgment.combatitem.CombatItemSettings;
import com.bountysmp.judgment.combatitem.CombatItemScope;
import com.bountysmp.judgment.util.DurationParser;
import com.bountysmp.judgment.pvp.PvpSettings;
import com.bountysmp.judgment.pvp.DragonEggSettings;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public final class SettingsGui {
    private static final int INVENTORY_SIZE = 27;
    private static final int COMBAT_ITEM_INVENTORY_SIZE = 45;
    private static final int COMBAT_TAG_SLOT = 1;
    private static final int PROMPT_TIMEOUT_SLOT = 3;
    private static final int MODE_SLOT = 5;
    private static final int INVISIBLE_KILLER_SLOT = 7;

    private static final int PVP_DEFAULT_SLOT = 10;
    private static final int PVP_COOLDOWN_SLOT = 12;
    private static final int PVP_DELAY_SLOT = 14;
    private static final int COMBAT_TIMER_BAR_SLOT = 16;
    private static final int COMBAT_ITEMS_SLOT = 21;
    private static final int DRAGON_EGG_SLOT = 23;
    private static final int[] COMBAT_ITEM_SLOTS = {11, 13, 15, 20, 22, 24, 29, 31, 33};
    private final Supplier<PvpSettings> pvpSettings;
    private final Consumer<PvpSettings> pvpUpdater;
    private final Supplier<DragonEggSettings> dragonEggSettings;
    private final Consumer<DragonEggSettings> dragonEggUpdater;
    private final Plugin plugin;
    private final Supplier<JudgmentSettings> settingsSupplier;
    private final LongConsumer combatTagUpdater;
    private final LongConsumer promptTimeoutUpdater;
    private final Consumer<Boolean> invisibleKillerUpdater;
    private final Supplier<CombatItemSettings> combatItemSettings;
    private final BiConsumer<CombatItemAction, Double> combatItemUpdater;
    private final BiConsumer<CombatItemAction, CombatItemScope> combatItemScopeUpdater;
    private final Consumer<Boolean> itemCooldownBossBarUpdater;
    private final Consumer<Boolean> combatTimerBossBarUpdater;
    private final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private final Map<UUID, CombatItemAction> pendingCombatItems = new ConcurrentHashMap<>();

    public SettingsGui(
        Plugin plugin,
        Supplier<JudgmentSettings> settingsSupplier,
        LongConsumer combatTagUpdater,
        LongConsumer promptTimeoutUpdater,
        Consumer<Boolean> invisibleKillerUpdater,
        Supplier<PvpSettings> pvpSettings,
        Consumer<PvpSettings> pvpUpdater,
        Supplier<DragonEggSettings> dragonEggSettings,
        Consumer<DragonEggSettings> dragonEggUpdater,
        Supplier<CombatItemSettings> combatItemSettings,
        BiConsumer<CombatItemAction, Double> combatItemUpdater,
        BiConsumer<CombatItemAction, CombatItemScope> combatItemScopeUpdater,
        Consumer<Boolean> itemCooldownBossBarUpdater,
        Consumer<Boolean> combatTimerBossBarUpdater
    ) {
        this.pvpSettings = pvpSettings;
        this.pvpUpdater = pvpUpdater;
        this.dragonEggSettings = dragonEggSettings;
        this.dragonEggUpdater = dragonEggUpdater;
        this.plugin = plugin;
        this.settingsSupplier = settingsSupplier;
        this.combatTagUpdater = combatTagUpdater;
        this.promptTimeoutUpdater = promptTimeoutUpdater;
        this.invisibleKillerUpdater = invisibleKillerUpdater;
        this.combatItemSettings = combatItemSettings;
        this.combatItemUpdater = combatItemUpdater;
        this.combatItemScopeUpdater = combatItemScopeUpdater;
        this.itemCooldownBossBarUpdater = itemCooldownBossBarUpdater;
        this.combatTimerBossBarUpdater = combatTimerBossBarUpdater;
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

        inventory.setItem(INVISIBLE_KILLER_SLOT, GuiItems.namedItem(
            Material.ENDER_EYE,
            Component.text("Invisible Killer Masking: " + onOff(settings.invisibleKillerObfuscation()), NamedTextColor.LIGHT_PURPLE),
            List.of(Component.text("Hide invisible killers in public death messages."), Component.text("Click to toggle."))
        ));
        inventory.setItem(COMBAT_ITEMS_SLOT, GuiItems.namedItem(
            Material.NETHERITE_SWORD,
            Component.text("Combat Item Rules", NamedTextColor.RED),
            List.of(Component.text("Configure bans and cooldowns used during combat."), Component.text("Click to open."))
        ));
        inventory.setItem(COMBAT_TIMER_BAR_SLOT, GuiItems.namedItem(
            Material.RED_DYE,
            Component.text("Combat Timer Boss Bar: " + onOff(settings.combatTimerBossBar()), NamedTextColor.RED),
            List.of(Component.text("Show remaining combat time above the hotbar."), Component.text("Click to toggle."))
        ));

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
        inventory.setItem(10, GuiItems.namedItem(Material.DRAGON_EGG, Component.text("Feature: " + onOff(s.enabled()), NamedTextColor.LIGHT_PURPLE), List.of(Component.text("Click to toggle."))));
        inventory.setItem(12, GuiItems.namedItem(Material.GLOWSTONE_DUST, Component.text("Glow: " + onOff(s.glow()), NamedTextColor.YELLOW), List.of(Component.text("Click to toggle."))));
        inventory.setItem(14, GuiItems.namedItem(Material.POTION, Component.text("Strength I: " + onOff(s.strength()), NamedTextColor.RED), List.of(Component.text("Click to toggle."))));
        inventory.setItem(16, GuiItems.namedItem(Material.SUGAR, Component.text("Speed I: " + onOff(s.speed()), NamedTextColor.AQUA), List.of(Component.text("Click to toggle."))));
        inventory.setItem(22, GuiItems.namedItem(Material.BARRIER, Component.text("Back", NamedTextColor.GRAY), List.of(Component.text("Return to Judgment settings."))));
        admin.openInventory(inventory);
    }

    public void openCombatItems(Player admin) {
        if (!admin.hasPermission("judgment.admin")) return;
        CombatItemMenuHolder holder = new CombatItemMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, COMBAT_ITEM_INVENTORY_SIZE, Component.text("Combat Item Rules", NamedTextColor.RED));
        holder.setInventory(inventory);
        CombatItemSettings settings = combatItemSettings.get();
        inventory.setItem(4, GuiItems.namedItem(Material.EXPERIENCE_BOTTLE,
            Component.text("Cooldown Boss Bars: " + onOff(settingsSupplier.get().itemCooldownBossBars()), NamedTextColor.AQUA),
            List.of(Component.text("Show a decreasing bar for every active item cooldown."), Component.text("Click to toggle."))));
        CombatItemAction[] actions = CombatItemAction.values();
        for (int index = 0; index < actions.length; index++) {
            CombatItemAction action = actions[index];
            double seconds = settings.seconds(action);
            inventory.setItem(COMBAT_ITEM_SLOTS[index], GuiItems.namedItem(action.icon(),
                Component.text(action.displayName() + ": " + formatCombatItemSetting(seconds), settingColor(seconds)),
                List.of(Component.text("Scope: " + settings.scope(action).displayName(),
                        settings.scope(action) == CombatItemScope.GLOBAL ? NamedTextColor.GOLD : NamedTextColor.AQUA),
                    Component.text("-1 = banned, 0 = unrestricted, positive = cooldown seconds."),
                    Component.text("Left-click: edit cooldown."),
                    Component.text("Right-click: toggle scope."))));
        }
        inventory.setItem(40, GuiItems.namedItem(Material.BARRIER, Component.text("Back", NamedTextColor.GRAY),
            List.of(Component.text("Return to Judgment settings."))));
        admin.openInventory(inventory);
    }

    private static String formatCombatItemSetting(double seconds) {
        if (seconds == -1.0) return "BANNED";
        if (seconds == 0.0) return "NO COOLDOWN";
        return java.math.BigDecimal.valueOf(seconds).stripTrailingZeros().toPlainString() + "s";
    }

    private static NamedTextColor settingColor(double seconds) {
        return seconds == -1.0 ? NamedTextColor.RED : seconds == 0.0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }

    public void handleClick(Player admin, int rawSlot, ClickType clickType) {
        if (!admin.hasPermission("judgment.admin")) {
            admin.closeInventory();
            return;
        }

        if (admin.getOpenInventory().getTopInventory().getHolder() instanceof DragonEggMenuHolder) {
            DragonEggSettings old = dragonEggSettings.get();
            DragonEggSettings updated = switch (rawSlot) {
                case 10 -> new DragonEggSettings(!old.enabled(), old.glow(), old.strength(), old.speed());
                case 12 -> new DragonEggSettings(old.enabled(), !old.glow(), old.strength(), old.speed());
                case 14 -> new DragonEggSettings(old.enabled(), old.glow(), !old.strength(), old.speed());
                case 16 -> new DragonEggSettings(old.enabled(), old.glow(), old.strength(), !old.speed());
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

        if (admin.getOpenInventory().getTopInventory().getHolder() instanceof CombatItemMenuHolder) {
            if (rawSlot == 4) {
                itemCooldownBossBarUpdater.accept(!settingsSupplier.get().itemCooldownBossBars());
                openCombatItems(admin);
            } else if (rawSlot == 40) {
                open(admin);
            } else if (combatItemActionAt(rawSlot) != null) {
                CombatItemAction action = combatItemActionAt(rawSlot);
                if (clickType.isRightClick()) {
                    CombatItemScope updated = combatItemSettings.get().scope(action).toggled();
                    combatItemScopeUpdater.accept(action, updated);
                    openCombatItems(admin);
                } else if (clickType.isLeftClick()) {
                    pendingInputs.put(admin.getUniqueId(), PendingInput.COMBAT_ITEM);
                    pendingCombatItems.put(admin.getUniqueId(), action);
                    admin.closeInventory();
                    admin.sendMessage(Component.text("Enter -1 to ban, 0 for no cooldown, or positive cooldown seconds (decimals allowed), or cancel.", NamedTextColor.YELLOW));
                }
            }
            return;
        }

        if (rawSlot == PVP_DEFAULT_SLOT) {
            PvpSettings old = pvpSettings.get();
            pvpUpdater.accept(new PvpSettings(!old.defaultEnabled(), old.toggleCooldownMillis(), old.postCombatDelayMillis()));
            open(admin);
            return;
        }
        if (rawSlot == COMBAT_TIMER_BAR_SLOT) {
            combatTimerBossBarUpdater.accept(!settingsSupplier.get().combatTimerBossBar());
            open(admin);
            return;
        }
        if (rawSlot == INVISIBLE_KILLER_SLOT) {
            invisibleKillerUpdater.accept(!settingsSupplier.get().invisibleKillerObfuscation());
            open(admin);
            return;
        }
        if (rawSlot == DRAGON_EGG_SLOT) {
            openDragonEgg(admin);
            return;
        }
        if (rawSlot == COMBAT_ITEMS_SLOT) {
            openCombatItems(admin);
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
        CombatItemAction pendingAction = pendingCombatItems.remove(admin.getUniqueId());
        if (pendingInput == null) {
            return false;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                admin.sendMessage(Component.text("Cancelled setting change.", NamedTextColor.YELLOW));
                if (pendingInput == PendingInput.COMBAT_ITEM) openCombatItems(admin); else open(admin);
                return;
            }

            if (pendingInput == PendingInput.COMBAT_ITEM) {
                Double seconds = parseCombatItemSeconds(message);
                if (seconds == null || pendingAction == null) {
                    admin.sendMessage(Component.text("Enter -1, 0, or a positive number of seconds, or cancel.", NamedTextColor.RED));
                    pendingInputs.put(admin.getUniqueId(), pendingInput);
                    if (pendingAction != null) pendingCombatItems.put(admin.getUniqueId(), pendingAction);
                    return;
                }
                if (!admin.isOnline() || !admin.hasPermission("judgment.admin")) return;
                combatItemUpdater.accept(pendingAction, seconds);
                admin.sendMessage(Component.text("Updated " + pendingAction.displayName() + " combat rule.", NamedTextColor.GREEN));
                openCombatItems(admin);
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

    static Double parseCombatItemSeconds(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return CombatItemSettings.valid(value) ? value : null;
        } catch (NullPointerException | NumberFormatException exception) {
            return null;
        }
    }

    private static CombatItemAction combatItemActionAt(int slot) {
        for (int index = 0; index < COMBAT_ITEM_SLOTS.length; index++)
            if (COMBAT_ITEM_SLOTS[index] == slot) return CombatItemAction.values()[index];
        return null;
    }

    private enum PendingInput {
        COMBAT_TAG_DURATION,
        PROMPT_TIMEOUT_DURATION,
        PVP_COOLDOWN,
        PVP_DELAY,
        COMBAT_ITEM
    }
}
