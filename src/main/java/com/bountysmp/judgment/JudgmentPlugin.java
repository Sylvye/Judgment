package com.bountysmp.judgment;

import com.bountysmp.judgment.command.JudgmentCommand;
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

public final class JudgmentPlugin extends JavaPlugin {
    private JudgmentSettings settings;
    private PendingKillStore pendingKillStore;
    private JudgmentService judgmentService;
    private SettingsGui settingsGui;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadJudgmentSettings();

        pendingKillStore = new PendingKillStore(getDataFolder().toPath().resolve("pending-kills.yml"));
        pendingKillStore.load();

        judgmentService = new JudgmentService(this, settings, pendingKillStore, System::currentTimeMillis);
        settingsGui = new SettingsGui(this, () -> settings, this::setCombatTagMillis, this::setPromptTimeoutMillis);

        JudgmentCommand judgmentCommand = new JudgmentCommand(judgmentService, settingsGui);
        PluginCommand command = Objects.requireNonNull(getCommand("judgment"), "judgment command missing from plugin.yml");
        command.setExecutor(judgmentCommand);
        command.setTabCompleter(judgmentCommand);

        getServer().getPluginManager().registerEvents(new JudgmentListener(judgmentService, settingsGui), this);
    }

    @Override
    public void onDisable() {
        if (pendingKillStore != null) {
            try {
                pendingKillStore.save();
            } catch (IOException exception) {
                getLogger().log(Level.SEVERE, "Failed to save pending combat log kills", exception);
            }
        }
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
