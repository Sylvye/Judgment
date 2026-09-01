package com.bountysmp.judgment.gui;

import com.bountysmp.judgment.combatitem.CombatItemAction;
import com.bountysmp.judgment.combatitem.CombatItemScope;
import com.bountysmp.judgment.combatitem.CombatItemSettings;
import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.pvp.DragonEggSettings;
import com.bountysmp.judgment.pvp.PvpSettings;
import com.bountysmp.judgment.util.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public final class SettingsGui {
    private static final int MODULE_SIZE = 27;
    private static final int COMBAT_RULES_SIZE = 54;
    private static final int BACK_SLOT = 26;
    private static final int MAIN_COMBAT_LOG_SLOT = 10;
    private static final int MAIN_PVP_TAGS_SLOT = 12;
    private static final int MAIN_COMBAT_RULES_SLOT = 14;
    private static final int MAIN_DRAGON_EGG_SLOT = 16;
    private static final int COMBAT_TAG_SLOT = 10;
    private static final int PROMPT_TIMEOUT_SLOT = 12;
    private static final int MODE_SLOT = 14;
    private static final int COMBAT_TIMER_BAR_SLOT = 16;
    private static final int INVISIBLE_KILLER_SLOT = 22;
    private static final int PVP_DEFAULT_SLOT = 10;
    private static final int PVP_COOLDOWN_SLOT = 12;
    private static final int PVP_DELAY_SLOT = 14;
    private static final int PVP_END_LOCK_SLOT = 16;
    private static final int PVP_NETHER_LOCK_SLOT = 22;
    private static final int COMBAT_RULE_BOSS_BAR_SLOT = 4;
    private static final int COMBAT_RULE_BACK_SLOT = 49;
    private static final CombatItemAction[] MANAGED_ACTIONS = {
        CombatItemAction.ELYTRA, CombatItemAction.FIREWORKS, CombatItemAction.ENDER_PEARLS,
        CombatItemAction.MACE_SMASH, CombatItemAction.RIPTIDE, CombatItemAction.LUNGE
    };
    private static final int[] MANAGED_SLOTS = {11, 12, 13, 14, 15, 16};
    private static final CombatItemAction[] EXPLOSIVE_ACTIONS = {
        CombatItemAction.TNT, CombatItemAction.TNT_MINECARTS, CombatItemAction.BEDS,
        CombatItemAction.RESPAWN_ANCHORS, CombatItemAction.END_CRYSTALS
    };
    private static final int[] EXPLOSIVE_SLOTS = {29, 30, 31, 32, 33};

    private final Plugin plugin;
    private final Supplier<JudgmentSettings> settingsSupplier;
    private final LongConsumer combatTagUpdater;
    private final LongConsumer promptTimeoutUpdater;
    private final Consumer<Boolean> invisibleKillerUpdater;
    private final Supplier<PvpSettings> pvpSettings;
    private final Consumer<PvpSettings> pvpUpdater;
    private final Supplier<DragonEggSettings> dragonEggSettings;
    private final Consumer<DragonEggSettings> dragonEggUpdater;
    private final Supplier<CombatItemSettings> combatItemSettings;
    private final BiConsumer<CombatItemAction, Double> combatItemUpdater;
    private final BiConsumer<CombatItemAction, CombatItemScope> combatItemScopeUpdater;
    private final BiConsumer<CombatItemAction, Double> damageModifierUpdater;
    private final Consumer<Boolean> itemCooldownBossBarUpdater;
    private final Consumer<Boolean> combatTimerBossBarUpdater;
    private final Map<UUID, PendingEdit> pendingEdits = new ConcurrentHashMap<>();

    public SettingsGui(Plugin plugin, Supplier<JudgmentSettings> settingsSupplier,
                       LongConsumer combatTagUpdater, LongConsumer promptTimeoutUpdater,
                       Consumer<Boolean> invisibleKillerUpdater, Supplier<PvpSettings> pvpSettings,
                       Consumer<PvpSettings> pvpUpdater, Supplier<DragonEggSettings> dragonEggSettings,
                       Consumer<DragonEggSettings> dragonEggUpdater,
                       Supplier<CombatItemSettings> combatItemSettings,
                       BiConsumer<CombatItemAction, Double> combatItemUpdater,
                       BiConsumer<CombatItemAction, CombatItemScope> combatItemScopeUpdater,
                       BiConsumer<CombatItemAction, Double> damageModifierUpdater,
                       Consumer<Boolean> itemCooldownBossBarUpdater,
                       Consumer<Boolean> combatTimerBossBarUpdater) {
        this.plugin = plugin;
        this.settingsSupplier = settingsSupplier;
        this.combatTagUpdater = combatTagUpdater;
        this.promptTimeoutUpdater = promptTimeoutUpdater;
        this.invisibleKillerUpdater = invisibleKillerUpdater;
        this.pvpSettings = pvpSettings;
        this.pvpUpdater = pvpUpdater;
        this.dragonEggSettings = dragonEggSettings;
        this.dragonEggUpdater = dragonEggUpdater;
        this.combatItemSettings = combatItemSettings;
        this.combatItemUpdater = combatItemUpdater;
        this.combatItemScopeUpdater = combatItemScopeUpdater;
        this.damageModifierUpdater = damageModifierUpdater;
        this.itemCooldownBossBarUpdater = itemCooldownBossBarUpdater;
        this.combatTimerBossBarUpdater = combatTimerBossBarUpdater;
    }

    public void open(Player admin) {
        if (!authorized(admin)) return;
        SettingsMenuHolder holder = new SettingsMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, MODULE_SIZE,
            Component.text("Judgment Settings", NamedTextColor.GOLD));
        holder.setInventory(inventory);
        inventory.setItem(MAIN_COMBAT_LOG_SLOT, GuiItems.namedItem(Material.CLOCK,
            Component.text("CombatLog", NamedTextColor.YELLOW),
            List.of(Component.text("Combat tags, prompts, punishment, and display settings."), Component.text("Click to open."))));
        inventory.setItem(MAIN_PVP_TAGS_SLOT, GuiItems.namedItem(Material.IRON_SWORD,
            Component.text("PvP Tags", NamedTextColor.RED),
            List.of(Component.text("PvP defaults, waits, and dimension locks."), Component.text("Click to open."))));
        inventory.setItem(MAIN_COMBAT_RULES_SLOT, GuiItems.namedItem(Material.NETHERITE_SWORD,
            Component.text("Combat Rules", NamedTextColor.DARK_RED),
            List.of(Component.text("Managed abilities, explosives, cooldowns, and damage."), Component.text("Click to open."))));
        inventory.setItem(MAIN_DRAGON_EGG_SLOT, GuiItems.namedItem(Material.DRAGON_EGG,
            Component.text("Dragon Egg", NamedTextColor.LIGHT_PURPLE),
            List.of(Component.text("Dragon egg privilege and effect settings."), Component.text("Click to open."))));
        admin.openInventory(inventory);
    }

    public void openCombatLog(Player admin) {
        if (!authorized(admin)) return;
        JudgmentSettings settings = settingsSupplier.get();
        CombatLogMenuHolder holder = new CombatLogMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, MODULE_SIZE,
            Component.text("CombatLog Settings", NamedTextColor.YELLOW));
        holder.setInventory(inventory);
        inventory.setItem(COMBAT_TAG_SLOT, GuiItems.namedItem(Material.CLOCK,
            Component.text("Combat Tag: " + DurationParser.formatMillis(settings.combatTagMillis()), NamedTextColor.YELLOW),
            List.of(Component.text("Click to edit in chat."))));
        inventory.setItem(PROMPT_TIMEOUT_SLOT, GuiItems.namedItem(Material.REPEATER,
            Component.text("Prompt Timeout: " + DurationParser.formatMillis(settings.promptTimeoutMillis()), NamedTextColor.GREEN),
            List.of(Component.text("Click to edit in chat."))));
        inventory.setItem(MODE_SLOT, GuiItems.namedItem(Material.IRON_SWORD,
            Component.text("Mode: " + settings.effectivePunishmentMode().displayName(), NamedTextColor.AQUA),
            List.of(Component.text("Instant is reserved for a future release."))));
        inventory.setItem(COMBAT_TIMER_BAR_SLOT, GuiItems.namedItem(Material.RED_DYE,
            Component.text("Combat Timer Boss Bar: " + onOff(settings.combatTimerBossBar()), NamedTextColor.RED),
            List.of(Component.text("Show remaining combat time above the hotbar."), Component.text("Click to toggle."))));
        inventory.setItem(INVISIBLE_KILLER_SLOT, GuiItems.namedItem(Material.ENDER_EYE,
            Component.text("Invisible Killer Masking: " + onOff(settings.invisibleKillerObfuscation()), NamedTextColor.LIGHT_PURPLE),
            List.of(Component.text("Hide invisible killers in public death messages."), Component.text("Click to toggle."))));
        addBack(inventory);
        admin.openInventory(inventory);
    }

    public void openPvpTags(Player admin) {
        if (!authorized(admin)) return;
        PvpSettings pvp = pvpSettings.get();
        PvpTagsMenuHolder holder = new PvpTagsMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, MODULE_SIZE,
            Component.text("PvP Tag Settings", NamedTextColor.RED));
        holder.setInventory(inventory);
        inventory.setItem(PVP_DEFAULT_SLOT, GuiItems.namedItem(Material.LEVER,
            Component.text("Default PvP: " + onOff(pvp.defaultEnabled()), NamedTextColor.RED),
            List.of(Component.text("Applies to new preferences only."), Component.text("Click to toggle."))));
        inventory.setItem(PVP_COOLDOWN_SLOT, GuiItems.namedItem(Material.CLOCK,
            Component.text("PvP Toggle Cooldown: " + DurationParser.formatMillis(pvp.toggleCooldownMillis()), NamedTextColor.YELLOW),
            List.of(Component.text("Click to edit in chat."))));
        inventory.setItem(PVP_DELAY_SLOT, GuiItems.namedItem(Material.SHIELD,
            Component.text("PvP Post-Combat Wait: " + DurationParser.formatMillis(pvp.postCombatDelayMillis()), NamedTextColor.GREEN),
            List.of(Component.text("Starts when the last combat tag ends."), Component.text("Click to edit in chat."))));
        inventory.setItem(PVP_END_LOCK_SLOT, GuiItems.namedItem(Material.END_STONE,
            Component.text("End PvP Toggle Lock: " + onOff(pvp.preventToggleInEnd()), NamedTextColor.DARK_PURPLE),
            List.of(Component.text("Prevent PvP status changes in the End."), Component.text("Click to toggle."))));
        inventory.setItem(PVP_NETHER_LOCK_SLOT, GuiItems.namedItem(Material.NETHERRACK,
            Component.text("Nether PvP Toggle Lock: " + onOff(pvp.preventToggleInNether()), NamedTextColor.DARK_RED),
            List.of(Component.text("Prevent PvP status changes in the Nether."), Component.text("Click to toggle."))));
        addBack(inventory);
        admin.openInventory(inventory);
    }

    public void openDragonEgg(Player admin) {
        if (!authorized(admin)) return;
        DragonEggMenuHolder holder = new DragonEggMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, MODULE_SIZE,
            Component.text("Dragon Egg Settings", NamedTextColor.LIGHT_PURPLE));
        holder.setInventory(inventory);
        DragonEggSettings settings = dragonEggSettings.get();
        inventory.setItem(10, GuiItems.namedItem(Material.DRAGON_EGG,
            Component.text("Feature: " + onOff(settings.enabled()), NamedTextColor.LIGHT_PURPLE), List.of(Component.text("Click to toggle."))));
        inventory.setItem(12, GuiItems.namedItem(Material.GLOWSTONE_DUST,
            Component.text("Glow: " + onOff(settings.glow()), NamedTextColor.YELLOW), List.of(Component.text("Click to toggle."))));
        inventory.setItem(14, GuiItems.namedItem(Material.POTION,
            Component.text("Strength I: " + onOff(settings.strength()), NamedTextColor.RED), List.of(Component.text("Click to toggle."))));
        inventory.setItem(16, GuiItems.namedItem(Material.SUGAR,
            Component.text("Speed I: " + onOff(settings.speed()), NamedTextColor.AQUA), List.of(Component.text("Click to toggle."))));
        addBack(inventory);
        admin.openInventory(inventory);
    }

    public void openCombatItems(Player admin) {
        if (!authorized(admin)) return;
        CombatItemMenuHolder holder = new CombatItemMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, COMBAT_RULES_SIZE,
            Component.text("Combat Rules", NamedTextColor.RED));
        holder.setInventory(inventory);
        inventory.setItem(COMBAT_RULE_BOSS_BAR_SLOT, GuiItems.namedItem(Material.EXPERIENCE_BOTTLE,
            Component.text("Cooldown Boss Bars: " + onOff(settingsSupplier.get().itemCooldownBossBars()), NamedTextColor.AQUA),
            List.of(Component.text("Show a decreasing bar for every active item cooldown."), Component.text("Click to toggle."))));
        inventory.setItem(9, GuiItems.namedItem(Material.WRITABLE_BOOK,
            Component.text("Managed Items & Abilities", NamedTextColor.AQUA), List.of(Component.text("Cooldown, ban, and scope controls."))));
        inventory.setItem(27, GuiItems.namedItem(Material.TNT,
            Component.text("Explosives", NamedTextColor.RED), List.of(Component.text("Cooldown, scope, and player-damage controls."))));
        for (int index = 0; index < MANAGED_ACTIONS.length; index++)
            inventory.setItem(MANAGED_SLOTS[index], combatRuleItem(MANAGED_ACTIONS[index]));
        for (int index = 0; index < EXPLOSIVE_ACTIONS.length; index++)
            inventory.setItem(EXPLOSIVE_SLOTS[index], combatRuleItem(EXPLOSIVE_ACTIONS[index]));
        inventory.setItem(COMBAT_RULE_BACK_SLOT, backItem());
        admin.openInventory(inventory);
    }

    private org.bukkit.inventory.ItemStack combatRuleItem(CombatItemAction action) {
        CombatItemSettings settings = combatItemSettings.get();
        double seconds = settings.seconds(action);
        java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
        lore.add(Component.text("Scope: " + settings.scope(action).displayName(),
            settings.scope(action) == CombatItemScope.GLOBAL ? NamedTextColor.GOLD : NamedTextColor.AQUA));
        if (action.explosive()) lore.add(Component.text("Player damage: " + formatNumber(settings.damageModifier(action)) + "x", NamedTextColor.RED));
        lore.add(Component.text("-1 = banned, 0 = unrestricted, positive = cooldown seconds."));
        lore.add(Component.text("Left-click: edit cooldown."));
        lore.add(Component.text("Right-click: toggle scope."));
        if (action.explosive()) lore.add(Component.text("Shift-left-click: edit player damage."));
        return GuiItems.namedItem(action.icon(),
            Component.text(action.displayName() + ": " + formatCombatItemSetting(seconds), settingColor(seconds)), lore);
    }

    public void handleClick(Player admin, int rawSlot, ClickType clickType) {
        if (!admin.hasPermission("judgment.admin")) {
            admin.closeInventory();
            return;
        }
        InventoryHolder holder = admin.getOpenInventory().getTopInventory().getHolder();
        if (holder instanceof SettingsMenuHolder) handleMainClick(admin, rawSlot);
        else if (holder instanceof CombatLogMenuHolder) handleCombatLogClick(admin, rawSlot);
        else if (holder instanceof PvpTagsMenuHolder) handlePvpClick(admin, rawSlot);
        else if (holder instanceof DragonEggMenuHolder) handleDragonEggClick(admin, rawSlot);
        else if (holder instanceof CombatItemMenuHolder) handleCombatRuleClick(admin, rawSlot, clickType);
    }

    private void handleMainClick(Player admin, int slot) {
        switch (slot) {
            case MAIN_COMBAT_LOG_SLOT -> openCombatLog(admin);
            case MAIN_PVP_TAGS_SLOT -> openPvpTags(admin);
            case MAIN_COMBAT_RULES_SLOT -> openCombatItems(admin);
            case MAIN_DRAGON_EGG_SLOT -> openDragonEgg(admin);
            default -> { }
        }
    }

    private void handleCombatLogClick(Player admin, int slot) {
        if (slot == BACK_SLOT) open(admin);
        else if (slot == COMBAT_TIMER_BAR_SLOT) {
            combatTimerBossBarUpdater.accept(!settingsSupplier.get().combatTimerBossBar());
            openCombatLog(admin);
        } else if (slot == INVISIBLE_KILLER_SLOT) {
            invisibleKillerUpdater.accept(!settingsSupplier.get().invisibleKillerObfuscation());
            openCombatLog(admin);
        } else if (slot == COMBAT_TAG_SLOT) {
            beginEdit(admin, new PendingEdit(PendingInput.COMBAT_TAG_DURATION, null, MenuSection.COMBAT_LOG),
                "Type a duration like 0s, 30s, 5m, 1h, or cancel.");
        } else if (slot == PROMPT_TIMEOUT_SLOT) {
            beginEdit(admin, new PendingEdit(PendingInput.PROMPT_TIMEOUT_DURATION, null, MenuSection.COMBAT_LOG),
                "Type a duration like 1s, 10s, 5m, 1h, or cancel.");
        } else if (slot == MODE_SLOT) {
            admin.sendMessage(Component.text("Instant mode is reserved for a future release. Relog remains active.", NamedTextColor.YELLOW));
        }
    }

    private void handlePvpClick(Player admin, int slot) {
        PvpSettings old = pvpSettings.get();
        if (slot == BACK_SLOT) open(admin);
        else if (slot == PVP_DEFAULT_SLOT) {
            pvpUpdater.accept(new PvpSettings(!old.defaultEnabled(), old.toggleCooldownMillis(), old.postCombatDelayMillis(),
                old.preventToggleInEnd(), old.preventToggleInNether()));
            openPvpTags(admin);
        } else if (slot == PVP_END_LOCK_SLOT) {
            pvpUpdater.accept(new PvpSettings(old.defaultEnabled(), old.toggleCooldownMillis(), old.postCombatDelayMillis(),
                !old.preventToggleInEnd(), old.preventToggleInNether()));
            openPvpTags(admin);
        } else if (slot == PVP_NETHER_LOCK_SLOT) {
            pvpUpdater.accept(new PvpSettings(old.defaultEnabled(), old.toggleCooldownMillis(), old.postCombatDelayMillis(),
                old.preventToggleInEnd(), !old.preventToggleInNether()));
            openPvpTags(admin);
        } else if (slot == PVP_COOLDOWN_SLOT || slot == PVP_DELAY_SLOT) {
            PendingInput input = slot == PVP_COOLDOWN_SLOT ? PendingInput.PVP_COOLDOWN : PendingInput.PVP_DELAY;
            beginEdit(admin, new PendingEdit(input, null, MenuSection.PVP_TAGS),
                "Type a duration like 0s, 10m, 24h, or cancel.");
        }
    }

    private void handleDragonEggClick(Player admin, int slot) {
        DragonEggSettings old = dragonEggSettings.get();
        DragonEggSettings updated = switch (slot) {
            case 10 -> new DragonEggSettings(!old.enabled(), old.glow(), old.strength(), old.speed());
            case 12 -> new DragonEggSettings(old.enabled(), !old.glow(), old.strength(), old.speed());
            case 14 -> new DragonEggSettings(old.enabled(), old.glow(), !old.strength(), old.speed());
            case 16 -> new DragonEggSettings(old.enabled(), old.glow(), old.strength(), !old.speed());
            default -> null;
        };
        if (updated != null) {
            dragonEggUpdater.accept(updated);
            openDragonEgg(admin);
        } else if (slot == BACK_SLOT) open(admin);
    }

    private void handleCombatRuleClick(Player admin, int slot, ClickType clickType) {
        if (slot == COMBAT_RULE_BOSS_BAR_SLOT) {
            itemCooldownBossBarUpdater.accept(!settingsSupplier.get().itemCooldownBossBars());
            openCombatItems(admin);
            return;
        }
        if (slot == COMBAT_RULE_BACK_SLOT) {
            open(admin);
            return;
        }
        CombatItemAction action = combatItemActionAt(slot);
        if (action == null) return;
        if (action.explosive() && clickType.isShiftClick() && clickType.isLeftClick()) {
            beginEdit(admin, new PendingEdit(PendingInput.EXPLOSIVE_DAMAGE, action, MenuSection.COMBAT_RULES),
                "Enter a nonnegative player-damage multiplier such as 0, 0.5, or 1, or cancel.");
        } else if (clickType.isRightClick()) {
            combatItemScopeUpdater.accept(action, combatItemSettings.get().scope(action).toggled());
            openCombatItems(admin);
        } else if (clickType.isLeftClick()) {
            beginEdit(admin, new PendingEdit(PendingInput.COMBAT_ITEM, action, MenuSection.COMBAT_RULES),
                "Enter -1 to ban, 0 for no cooldown, or positive cooldown seconds, or cancel.");
        }
    }

    private void beginEdit(Player admin, PendingEdit edit, String prompt) {
        pendingEdits.put(admin.getUniqueId(), edit);
        admin.closeInventory();
        admin.sendMessage(Component.text(prompt, NamedTextColor.YELLOW));
    }

    public boolean handleChat(Player admin, String message) {
        PendingEdit edit = pendingEdits.remove(admin.getUniqueId());
        if (edit == null) return false;
        Bukkit.getScheduler().runTask(plugin, () -> applyChatEdit(admin, message, edit));
        return true;
    }

    private void applyChatEdit(Player admin, String message, PendingEdit edit) {
        if (message.equalsIgnoreCase("cancel")) {
            admin.sendMessage(Component.text("Cancelled setting change.", NamedTextColor.YELLOW));
            reopen(admin, edit.returnMenu());
            return;
        }
        if (!admin.isOnline() || !admin.hasPermission("judgment.admin")) return;
        if (edit.input() == PendingInput.COMBAT_ITEM) {
            Double seconds = parseCombatItemSeconds(message);
            if (seconds == null || edit.action() == null) {
                retry(admin, edit, "Enter -1, 0, or a positive number of seconds, or cancel.");
                return;
            }
            combatItemUpdater.accept(edit.action(), seconds);
            admin.sendMessage(Component.text("Updated " + edit.action().displayName() + " combat rule.", NamedTextColor.GREEN));
            reopen(admin, edit.returnMenu());
            return;
        }
        if (edit.input() == PendingInput.EXPLOSIVE_DAMAGE) {
            Double modifier = parseDamageModifier(message);
            if (modifier == null || edit.action() == null) {
                retry(admin, edit, "Enter a finite nonnegative multiplier such as 0, 0.5, or 1, or cancel.");
                return;
            }
            damageModifierUpdater.accept(edit.action(), modifier);
            admin.sendMessage(Component.text("Updated " + edit.action().displayName() + " player damage.", NamedTextColor.GREEN));
            reopen(admin, edit.returnMenu());
            return;
        }
        Long millis = DurationParser.parseMillis(message);
        if (millis == null) {
            retry(admin, edit, "Use a duration like 0s, 30s, 5m, 1h, or cancel.");
            return;
        }
        switch (edit.input()) {
            case PVP_COOLDOWN, PVP_DELAY -> {
                PvpSettings old = pvpSettings.get();
                pvpUpdater.accept(new PvpSettings(old.defaultEnabled(),
                    edit.input() == PendingInput.PVP_COOLDOWN ? millis : old.toggleCooldownMillis(),
                    edit.input() == PendingInput.PVP_DELAY ? millis : old.postCombatDelayMillis(),
                    old.preventToggleInEnd(), old.preventToggleInNether()));
                admin.sendMessage(Component.text("Updated PvP settings.", NamedTextColor.GREEN));
            }
            case COMBAT_TAG_DURATION -> {
                combatTagUpdater.accept(millis);
                admin.sendMessage(Component.text("Updated combat tag duration.", NamedTextColor.GREEN));
            }
            case PROMPT_TIMEOUT_DURATION -> {
                promptTimeoutUpdater.accept(millis);
                admin.sendMessage(Component.text("Updated prompt timeout.", NamedTextColor.GREEN));
            }
            default -> { }
        }
        reopen(admin, edit.returnMenu());
    }

    private void retry(Player admin, PendingEdit edit, String message) {
        admin.sendMessage(Component.text(message, NamedTextColor.RED));
        pendingEdits.put(admin.getUniqueId(), edit);
    }

    private void reopen(Player admin, MenuSection section) {
        switch (section) {
            case COMBAT_LOG -> openCombatLog(admin);
            case PVP_TAGS -> openPvpTags(admin);
            case COMBAT_RULES -> openCombatItems(admin);
        }
    }

    public static boolean isSettingsHolder(InventoryHolder holder) {
        return holder instanceof SettingsMenuHolder || holder instanceof CombatLogMenuHolder
            || holder instanceof PvpTagsMenuHolder || holder instanceof CombatItemMenuHolder
            || holder instanceof DragonEggMenuHolder;
    }

    private boolean authorized(Player admin) {
        if (admin.hasPermission("judgment.admin")) return true;
        admin.sendMessage(Component.text("You do not have permission to manage Judgment.", NamedTextColor.RED));
        return false;
    }

    private static void addBack(Inventory inventory) { inventory.setItem(BACK_SLOT, backItem()); }

    private static org.bukkit.inventory.ItemStack backItem() {
        return GuiItems.namedItem(Material.BARRIER, Component.text("Back", NamedTextColor.GRAY),
            List.of(Component.text("Return to Judgment settings.")));
    }

    private static CombatItemAction combatItemActionAt(int slot) {
        for (int index = 0; index < MANAGED_SLOTS.length; index++)
            if (MANAGED_SLOTS[index] == slot) return MANAGED_ACTIONS[index];
        for (int index = 0; index < EXPLOSIVE_SLOTS.length; index++)
            if (EXPLOSIVE_SLOTS[index] == slot) return EXPLOSIVE_ACTIONS[index];
        return null;
    }

    static Double parseCombatItemSeconds(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return CombatItemSettings.valid(value) ? value : null;
        } catch (NullPointerException | NumberFormatException exception) {
            return null;
        }
    }

    static Double parseDamageModifier(String raw) {
        try {
            double value = Double.parseDouble(raw.trim());
            return CombatItemSettings.validDamageModifier(value) ? value : null;
        } catch (NullPointerException | NumberFormatException exception) {
            return null;
        }
    }

    private static String formatCombatItemSetting(double seconds) {
        if (seconds == -1.0) return "BANNED";
        if (seconds == 0.0) return "NO COOLDOWN";
        return formatNumber(seconds) + "s";
    }

    private static String formatNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static NamedTextColor settingColor(double seconds) {
        return seconds == -1.0 ? NamedTextColor.RED : seconds == 0.0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }

    private enum PendingInput {
        COMBAT_TAG_DURATION, PROMPT_TIMEOUT_DURATION, PVP_COOLDOWN, PVP_DELAY, COMBAT_ITEM, EXPLOSIVE_DAMAGE
    }

    private enum MenuSection { COMBAT_LOG, PVP_TAGS, COMBAT_RULES }

    private record PendingEdit(PendingInput input, CombatItemAction action, MenuSection returnMenu) {}
}
