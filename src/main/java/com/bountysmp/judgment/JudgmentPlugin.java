package com.bountysmp.judgment;

import com.bountysmp.judgment.command.JudgmentCommand;
import com.bountysmp.judgment.pvp.*;
import com.bountysmp.judgment.config.JudgmentSettings;
import com.bountysmp.judgment.gui.SettingsGui;
import com.bountysmp.judgment.listener.JudgmentListener;
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
        pvpService = new PvpService(new PvpStore(getDataFolder().toPath().resolve("pvp-players.yml")),
            () -> pvpSettings, System::currentTimeMillis,
            id -> judgmentService.getCombatTag(id).isPresent(), getLogger());
        pvpPresentation = new PvpPresentation(pvpService);
        settingsGui = new SettingsGui(this, () -> settings, this::setCombatTagMillis, this::setPromptTimeoutMillis,
            () -> pvpSettings, this::setPvpSettings);
        PvpCommand pvpCommand = new PvpCommand(pvpService, player -> pvpPresentation.refreshAll());
        PluginCommand pvp = Objects.requireNonNull(getCommand("pvp"), "pvp command missing from plugin.yml");
        pvp.setExecutor(pvpCommand);
        pvp.setTabCompleter(pvpCommand);
        getServer().getPluginManager().registerEvents(new PvpListener(this, pvpService, judgmentService, pvpPresentation), this);
        pvpPresentation.refreshAll();

        JudgmentCommand judgmentCommand = new JudgmentCommand(judgmentService, settingsGui);
        PluginCommand command = Objects.requireNonNull(getCommand("judgment"), "judgment command missing from plugin.yml");
        command.setExecutor(judgmentCommand);
        command.setTabCompleter(judgmentCommand);

        getServer().getPluginManager().registerEvents(new JudgmentListener(judgmentService, settingsGui), this);
    }

    @Override
    public void onDisable() {
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
        saveConfig();
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
}
