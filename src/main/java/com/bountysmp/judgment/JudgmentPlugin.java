package com.bountysmp.judgment;

import com.bountysmp.judgment.damage.DamageAttribution;
import com.bountysmp.judgment.migration.LegacyEggEffectCleanup;
import com.bountysmp.judgment.command.JudgmentCommand;
import com.bountysmp.judgment.combatitem.*;
import com.bountysmp.judgment.enchantment.EnchantmentSettings;
import com.bountysmp.judgment.enchantment.EnchantmentController;
import com.bountysmp.judgment.enchantment.EasyEnchantsIntegration;
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
    private CombatItemSettings combatItemSettings;
    private CombatItemCooldownManager combatItemCooldownManager;
    private CombatBossBarController combatBossBarController;
    private NativeWeaponCooldownController nativeWeaponCooldownController;
    private FlightLandingRequirement flightLandingRequirement;
    private EnchantmentSettings enchantmentSettings;
    private EnchantmentController enchantmentController;

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
        combatItemSettings = CombatItemSettings.fromConfig(getConfig(), getLogger());
        enchantmentSettings = EnchantmentSettings.fromConfig(getConfig(), getLogger());
        pvpService = new PvpService(new PvpStore(getDataFolder().toPath().resolve("pvp-players.yml")),
            () -> pvpSettings, System::currentTimeMillis,
            id -> judgmentService.getCombatTag(id).isPresent(),
            event -> getServer().getPluginManager().callEvent(event), id -> {
                org.bukkit.entity.Player player = getServer().getPlayer(id);
                return player != null && player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END;
            }, id -> {
                org.bukkit.entity.Player player = getServer().getPlayer(id);
                return player != null && player.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER;
            }, getLogger());
        pvpPresentation = new PvpPresentation(pvpService);
        combatItemCooldownManager = new CombatItemCooldownManager(
            new CombatItemCooldownStore(getDataFolder().toPath().resolve("combat-item-cooldowns.yml")),
            () -> combatItemSettings, id -> judgmentService.getCombatTag(id).isPresent(),
            System::currentTimeMillis, getLogger());
        settingsGui = new SettingsGui(this, () -> settings, this::setCombatTagMillis, this::setPromptTimeoutMillis,
            this::setInvisibleKillerObfuscation,
            () -> pvpSettings, this::setPvpSettings,
            () -> combatItemSettings, this::setCombatItemSetting, this::setCombatItemScope,
            this::setCombatItemDamageModifier,
            this::setRequireLanding, this::setItemCooldownBossBars, this::setCombatTimerBossBar,
            () -> enchantmentSettings, this::setEnchantmentSettings);
        enchantmentController = new EnchantmentController(this, () -> enchantmentSettings);
        getServer().getPluginManager().registerEvents(enchantmentController, this);
        if (getServer().getPluginManager().isPluginEnabled("EasyEnchants")) {
            try {
                getServer().getPluginManager().registerEvents(new EasyEnchantsIntegration(() -> enchantmentSettings), this);
            } catch (LinkageError error) {
                getLogger().warning("Update EasyEnchants to enable immediate enchantment-rule enforcement for drag-and-drop books.");
            }
        }
        enchantmentController.rulesChanged();
        PvpCommand pvpCommand = new PvpCommand(pvpService, player -> pvpPresentation.refreshAll());
        PluginCommand pvp = Objects.requireNonNull(getCommand("pvp"), "pvp command missing from plugin.yml");
        pvp.setExecutor(pvpCommand);
        pvp.setTabCompleter(pvpCommand);
        DamageAttribution attribution = new DamageAttribution(this);
        getServer().getPluginManager().registerEvents(new PvpListener(this, pvpService, judgmentService, pvpPresentation, attribution), this);
        flightLandingRequirement = new FlightLandingRequirement(this, () -> combatItemSettings, attribution,
            judgmentService::isExecutingPunishment, new FlightLandingStore(getDataFolder().toPath().resolve("flight-landing.yml")));
        getServer().getPluginManager().registerEvents(flightLandingRequirement, this);
        nativeWeaponCooldownController = new NativeWeaponCooldownController(this, combatItemCooldownManager);
        getServer().getPluginManager().registerEvents(nativeWeaponCooldownController, this);
        getServer().getPluginManager().registerEvents(
            new CombatItemListener(this, combatItemCooldownManager, nativeWeaponCooldownController, attribution,
                judgmentService::isExecutingPunishment), this);
        combatBossBarController = new CombatBossBarController(this, judgmentService, combatItemCooldownManager,
            () -> combatItemSettings, () -> settings);
        pvpPresentation.refreshAll();

        JudgmentCommand judgmentCommand = new JudgmentCommand(judgmentService, settingsGui, pvpService,
            player -> pvpPresentation.refreshAll());
        PluginCommand command = Objects.requireNonNull(getCommand("judgment"), "judgment command missing from plugin.yml");
        command.setExecutor(judgmentCommand);
        command.setTabCompleter(judgmentCommand);

        getServer().getPluginManager().registerEvents(new JudgmentListener(judgmentService, settingsGui), this);
        getServer().getPluginManager().registerEvents(
            new InvisibleKillerObfuscation(() -> settings.invisibleKillerObfuscation()), this);
        getServer().getPluginManager().registerEvents(new LegacyEggEffectCleanup(), this);
        getServer().getOnlinePlayers().forEach(LegacyEggEffectCleanup::clear);
    }

    @Override
    public void onDisable() {
        if (flightLandingRequirement != null) flightLandingRequirement.close();
        if (enchantmentController != null) enchantmentController.close();
        if (nativeWeaponCooldownController != null) nativeWeaponCooldownController.close();
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
        getConfig().set("pvp.enabled", updated.enabled());
        getConfig().set("pvp.default-enabled", updated.defaultEnabled());
        getConfig().set("pvp.toggle-cooldown-seconds", updated.toggleCooldownMillis() / 1_000.0);
        getConfig().set("pvp.post-combat-delay-seconds", updated.postCombatDelayMillis() / 1_000.0);
        getConfig().set("pvp.prevent-toggle-in-end", updated.preventToggleInEnd());
        getConfig().set("pvp.prevent-toggle-in-nether", updated.preventToggleInNether());
        getConfig().set("pvp.protect-untagged-from-player-damage", updated.protectUntaggedFromPlayerDamage());
        saveConfig();
        if (pvpPresentation != null) pvpPresentation.refreshAll();
    }

    private void setEnchantmentSettings(EnchantmentSettings updated) {
        enchantmentSettings = updated;
        getConfig().set(EnchantmentSettings.CONFIG_ROOT + ".enabled", updated.enabled());
        // Preserve literal IDs (including dots in custom keys) and unrecognized config entries.
        java.util.Map<String, Object> limits = new java.util.LinkedHashMap<>();
        limits.putAll(EnchantmentSettings.configuredLimits(getConfig()));
        updated.limits().forEach((key, limit) -> limits.put(key.toString(), limit));
        getConfig().set(EnchantmentSettings.CONFIG_ROOT + ".limits", limits);
        saveConfig();
        enchantmentController.rulesChanged();
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
        flightLandingRequirement.settingsChanged();
        nativeWeaponCooldownController.resynchronize(action);
    }

    private void setCombatItemScope(CombatItemAction action, CombatItemScope scope) {
        combatItemSettings = combatItemSettings.withScope(action, scope);
        getConfig().set(CombatItemSettings.SCOPE_CONFIG_ROOT + "." + action.configKey(), scope.configValue());
        saveConfig();
        combatItemCooldownManager.scopeChanged(action);
        nativeWeaponCooldownController.resynchronize(action);
    }

    private void setCombatItemDamageModifier(CombatItemAction action, Double modifier) {
        combatItemSettings = combatItemSettings.withDamageModifier(action, modifier);
        getConfig().set(CombatItemSettings.DAMAGE_MODIFIER_CONFIG_ROOT + "." + action.configKey(), modifier);
        saveConfig();
    }

    private void setRequireLanding(boolean enabled) {
        combatItemSettings = combatItemSettings.withRequireLanding(enabled);
        getConfig().set(CombatItemSettings.REQUIRE_LANDING_CONFIG, enabled);
        saveConfig();
        flightLandingRequirement.settingsChanged();
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
