package com.bountysmp.judgment.command;

import com.bountysmp.judgment.gui.SettingsGui;
import com.bountysmp.judgment.service.JudgmentService;
import com.bountysmp.judgment.pvp.PvpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class JudgmentCommand implements CommandExecutor, TabCompleter {
    private final JudgmentService judgmentService;
    private final SettingsGui settingsGui;
    private final PvpService pvpService;
    private final Consumer<Player> pvpRefresh;

    public JudgmentCommand(JudgmentService judgmentService, SettingsGui settingsGui) {
        this(judgmentService, settingsGui, null, ignored -> {});
    }

    public JudgmentCommand(JudgmentService judgmentService, SettingsGui settingsGui, PvpService pvpService, Consumer<Player> pvpRefresh) {
        this.judgmentService = judgmentService;
        this.settingsGui = settingsGui;
        this.pvpService = pvpService;
        this.pvpRefresh = pvpRefresh;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("settings"))) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Only players can open Judgment settings.", NamedTextColor.RED));
                return true;
            }
            settingsGui.open(player);
            return true;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("pvp")) {
            if (!sender.hasPermission("judgment.admin")) {
                sender.sendMessage(Component.text("You do not have permission to change another player's PvP status.", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || (!args[2].equalsIgnoreCase("on") && !args[2].equalsIgnoreCase("off"))) {
                sender.sendMessage(Component.text("Usage: /judgment pvp <player> <on|off>", NamedTextColor.RED));
                return true;
            }
            boolean enabled = args[2].equalsIgnoreCase("on");
            PvpService.Result result = pvpService == null
                ? new PvpService.Result(PvpService.Outcome.STORAGE_ERROR, false, 0)
                : pvpService.adminSet(target.getUniqueId(), enabled);
            if (result.outcome() == PvpService.Outcome.STORAGE_ERROR) {
                sender.sendMessage(Component.text("PvP status could not be saved; no change was made.", NamedTextColor.RED));
            } else {
                pvpRefresh.accept(target);
                sender.sendMessage(Component.text(target.getName() + " PvP is now " + (result.enabled() ? "ON" : "OFF") + ".", NamedTextColor.GREEN));
                target.sendMessage(Component.text("An administrator set your PvP status to " + (result.enabled() ? "ON" : "OFF") + ".", NamedTextColor.YELLOW));
            }
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("stack")) {
            if (!sender.hasPermission("judgment.admin")) {
                sender.sendMessage(Component.text("You do not have permission to debug Judgment.", NamedTextColor.RED));
                return true;
            }

            Player target;
            if (args.length >= 3) {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player not found: " + args[2], NamedTextColor.RED));
                    return true;
                }
            } else if (sender instanceof Player player) {
                target = player;
            } else {
                sender.sendMessage(Component.text("Usage: /judgment debug stack <player>", NamedTextColor.RED));
                return true;
            }

            for (Component line : judgmentService.debugStack(target)) {
                sender.sendMessage(line);
            }
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
            sender.sendMessage(Component.text("Usage: /judgment settings|pvp <player> <on|off>|debug stack [player]", NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text("Judgment is running.", NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("judgment.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("settings", "debug", "pvp").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
        }
        if (args.length == 2 && sender.hasPermission("judgment.admin") && args[0].equalsIgnoreCase("pvp")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
        }
        if (args.length == 3 && sender.hasPermission("judgment.admin") && args[0].equalsIgnoreCase("pvp")) {
            return Arrays.asList("on", "off").stream().filter(value -> value.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && sender.hasPermission("judgment.admin") && args[0].equalsIgnoreCase("debug")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            if ("stack".startsWith(prefix)) {
                return List.of("stack");
            }
        }
        if (args.length == 3 && sender.hasPermission("judgment.admin") && args[0].equalsIgnoreCase("debug") && args[1].equalsIgnoreCase("stack")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        }
        return List.of();
    }
}
