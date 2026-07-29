package com.bountysmp.judgment.command;

import com.bountysmp.judgment.gui.SettingsGui;
import com.bountysmp.judgment.service.JudgmentService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class JudgmentCommand implements CommandExecutor, TabCompleter {
    private final JudgmentService judgmentService;
    private final SettingsGui settingsGui;

    public JudgmentCommand(JudgmentService judgmentService, SettingsGui settingsGui) {
        this.judgmentService = judgmentService;
        this.settingsGui = settingsGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("settings")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can open Judgment settings.", NamedTextColor.RED));
                return true;
            }
            settingsGui.open(player);
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("choice")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can answer combat log prompts.", NamedTextColor.RED));
                return true;
            }
            if (args[2].equalsIgnoreCase("yes")) {
                judgmentService.handleChoice(player, args[1], true);
                return true;
            }
            if (args[2].equalsIgnoreCase("no")) {
                judgmentService.handleChoice(player, args[1], false);
                return true;
            }
        }

        if (sender.hasPermission("judgment.admin")) {
            sender.sendMessage(Component.text("Usage: /judgment settings", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("Judgment is running.", NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("judgment.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("settings".startsWith(prefix)) {
                return List.of("settings");
            }
        }
        return List.of();
    }
}
