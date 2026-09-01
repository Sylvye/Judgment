package com.bountysmp.judgment;

import com.bountysmp.judgment.command.JudgmentCommand;
import com.bountysmp.judgment.combatitem.*;
import com.bountysmp.judgment.pvp.*;
import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.gui.SettingsGui;
import com.bountysmp.judgment.listener.JudgmentListener;
import com.bountysmp.judgment.module.InvisibleKillerObfuscation;
import com.bountysmp.judgment.service.JudgmentService;
import com.bountysmp.judgment.storage.PendingKillStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

public class JudgmentPlugin extends JavaPlugin {
    private JudgmentSettings settings;
    private PendingKillStore pendingKillStore;
    private JudgmentService judgmentService;
    private SettingsGui settingsGui;
    private PvpSettings pvpSettings;
    private PvpService pvpService;
    private PvpPresentation pvpPresentation;
    private DragonEggSettings dragonEggSettings;
    private DragonEggPrivilege dragonEggPrivilege;
    private CombatItemSettings combatItemSettings;
    private CombatItemCooldownManager combatItemCooldownManager;
    private CombatBossBarController combatBossBarController;

    public PvpService getPvpService() {
        return pvpService;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadJudgmentSettings();
        getConfig().options().copyDefaults(true);
        saveConfig();

        pendingKillStore = new PendingKillStore(getDataFolder().toPath().resolve("pending-kills.yml"));
        pendingKillStore.load();

        judgmentService = new JudgmentService(this, settings, pendingKillStore, System::currentTimeMillis);
        pvpSettings = PvpSettings.fromConfig(getConfig(), getLogger());
        dragonEggSettings = DragonEggSettings.fromConfig(getConfig(), getLogger());
        combatItemSettings = CombatItemSettings.fromConfig(getConfig(), getLogger());
        pvpService = new PvpService(new PvpStore(getDataFolder().toPath().resolve("pvp-players.yml")),
            () -> pvpSettings, System::currentTimeMillis,
            id -> judgmentService.getCombatTag(id).isPresent(),
            id -> {
                org.bukkit.entity.Player player = getServer().getPlayer(id);
                return player != null && dragonEggPrivilegeHasEgg(player);
            }, id -> {
                org.bukkit.entity.Player player = getServer().getPlayer(id);
                return player != null && player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END;
            }, id -> {
                org.bukkit.entity.Player player = getServer().getPlayer(id);
                return player != null && player.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
            }, getLogger());
        pvpPresentation = new PvpPresentation(pvpService);
        dragonEggPrivilege = new DragonEggPrivilege(this, pvpService, () -> dragonEggSettings);
        combatItemCooldownManager = new CombatItemCooldownManager(
            new CombatItemCooldownStore(getDataFolder().toPath().resolve("combat-item-cooldowns.yml")),
            () -> combatItemSettings, id -> judgmentService.getCombatTag(id).isPresent(),
            System::currentTimeMillis, getLogger());
        settingsGui = new SettingsGui(this, () -> settings, this::setCombatTagMillis, this::setPromptTimeoutMillis,
            this::setInvisibleKillerObfuscation,
            () -> pvpSettings, this::setPvpSettings, () -> dragonEggSettings, this::setDragonEggSettings,
            () -> combatItemSettings, this::setCombatItemSetting, this::setCombatItemScope,
            this::setCombatItemDamageModifier,
            this::setItemCooldownBossBars, this::setCombatTimerBossBar);
        PvpCommand pvpCommand = new PvpCommand(pvpService, player -> pvpPresentation.refreshAll());
        PluginCommand pvp = Objects.requireNonNull(getCommand("pvp"), "pvp command missing from plugin.yml");
        pvp.setExecutor(pvpCommand);
        pvp.setTabCompleter(pvpCommand);
        getServer().getPluginManager().registerEvents(new PvpListener(this, pvpService, judgmentService, pvpPresentation), this);
        getServer().getPluginManager().registerEvents(new CombatItemListener(this, combatItemCooldownManager), this);
        combatBossBarController = new CombatBossBarController(this, judgmentService, combatItemCooldownManager,
            () -> combatItemSettings, () -> settings);
        pvpPresentation.refreshAll();
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) dragonEggPrivilege.refresh(player);
        }, 1L, 20L);

        JudgmentCommand judgmentCommand = new JudgmentCommand(judgmentService, settingsGui, pvpService,
            player -> pvpPresentation.refreshAll());
        PluginCommand command = Objects.requireNonNull(getCommand("judgment"), "judgment command missing from plugin.yml");
        command.setExecutor(judgmentCommand);
        command.setTabCompleter(judgmentCommand);

        getServer().getPluginManager().registerEvents(new JudgmentListener(judgmentService, settingsGui, dragonEggPrivilege), this);
        getServer().getPluginManager().registerEvents(
            new InvisibleKillerObfuscation(() -> settings.invisibleKillerObfuscation()), this);
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) dragonEggPrivilege.refresh(player);
    }

    @Override
    public void onDisable() {
        if (combatBossBarController != null) combatBossBarController.close();
        if (pvpPresentation != null) pvpPresentation.close();
        if (pendingKillStore != null) {
            try {
                pendingKillStore.save();
            } catch (IOException exception) {
                getLogger().log(Level.SEVERE, "Failed to save pending combat log kills", exception);
            }
        }
    }

    private void setPvpSettings(PvpSettings updated) {
        pvpSettings = updated;
        getConfig().set("pvp.default-enabled", updated.defaultEnabled());
        getConfig().set("pvp.toggle-cooldown-seconds", updated.toggleCooldownMillis() / 1_000.0);
        getConfig().set("pvp.post-combat-delay-seconds", updated.postCombatDelayMillis() / 1_000.0);
        getConfig().set("pvp.prevent-toggle-in-end", updated.preventToggleInEnd());
        getConfig().set("pvp.prevent-toggle-in-nether", updated.preventToggleInNether());
        saveConfig();
    }

    private void setDragonEggSettings(DragonEggSettings updated) {
        dragonEggSettings = updated;
        getConfig().set("pvp.dragon-egg.enabled", updated.enabled());
        getConfig().set("pvp.dragon-egg.effects.glow", updated.glow());
        getConfig().set("pvp.dragon-egg.effects.strength", updated.strength());
        getConfig().set("pvp.dragon-egg.effects.speed", updated.speed());
        saveConfig();
        if (dragonEggPrivilege != null) for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) dragonEggPrivilege.refresh(player);
    }

    private boolean dragonEggPrivilegeHasEgg(org.bukkit.entity.Player player) {
        return java.util.Arrays.stream(player.getInventory().getContents())
            .filter(java.util.Objects::nonNull)
            .anyMatch(item -> item.getType() == org.bukkit.Material.DRAGON_EGG && item.getAmount() > 0);
    }

    private void reloadJudgmentSettings() {
        reloadConfig();
        settings = JudgmentSettings.fromConfig(getConfig(), getLogger());
    }

    private void setCombatTagMillis(long millis) {
        settings = settings.withCombatTagMillis(millis);
        getConfig().set("combat-tag-seconds", millis / 1_000.0);
        saveConfig();
        judgmentService.updateSettings(settings);
    }

    private void setPromptTimeoutMillis(long millis) {
        settings = settings.withPromptTimeoutMillis(millis);
        getConfig().set("prompt-timeout-seconds", millis / 1_000.0);
        saveConfig();
        judgmentService.updateSettings(settings);
    }

    private void setInvisibleKillerObfuscation(boolean enabled) {
        settings = settings.withInvisibleKillerObfuscation(enabled);
        getConfig().set("invisible-killer-obfuscation", enabled);
        saveConfig();
        judgmentService.updateSettings(settings);
    }

    private void setCombatItemSetting(CombatItemAction action, Double seconds) {
        combatItemSettings = combatItemSettings.with(action, seconds);
        getConfig().set(CombatItemSettings.CONFIG_ROOT + "." + action.configKey(), seconds);
        saveConfig();
        combatItemCooldownManager.settingChanged(action, seconds);
    }

    private void setCombatItemScope(CombatItemAction action, CombatItemScope scope) {
        combatItemSettings = combatItemSettings.withScope(action, scope);
        getConfig().set(CombatItemSettings.SCOPE_CONFIG_ROOT + "." + action.configKey(), scope.configValue());
        saveConfig();
        combatItemCooldownManager.scopeChanged(action);
    }

    private void setCombatItemDamageModifier(CombatItemAction action, Double modifier) {
        combatItemSettings = combatItemSettings.withDamageModifier(action, modifier);
        getConfig().set(CombatItemSettings.DAMAGE_MODIFIER_CONFIG_ROOT + "." + action.configKey(), modifier);
        saveConfig();
    }

    private void setItemCooldownBossBars(boolean enabled) {
        settings = settings.withItemCooldownBossBars(enabled);
        getConfig().set("bossbars.item-cooldowns", enabled);
        saveConfig();
        judgmentService.updateSettings(settings);
    }

    private void setCombatTimerBossBar(boolean enabled) {
        settings = settings.withCombatTimerBossBar(enabled);
        getConfig().set("bossbars.combat-timer", enabled);
        saveConfig();
        judgmentService.updateSettings(settings);
    }
}
