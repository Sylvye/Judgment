package com.bountysmp.judgment.pvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class PvpCommand implements TabExecutor {
    private final PvpService service;
    private final Consumer<Player> refresh;

    public PvpCommand(PvpService service, Consumer<Player> refresh) {
        this.service = service;
        this.refresh = refresh;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /pvp.");
            return true;
        }
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            player.sendMessage(Component.text("Usage: /pvp [on|off]", NamedTextColor.YELLOW));
            return true;
        }
        Boolean requested = args.length == 0 ? null : args[0].equalsIgnoreCase("on");
        PvpService.Result result = service.change(player.getUniqueId(), requested);
        String status = "PvP is " + (result.enabled() ? "ON" : "OFF") + ".";
        String message = switch (result.outcome()) {
            case CHANGED -> status;
            case UNCHANGED -> status + " No change was needed.";
            case WAIT -> status + " You can change it in " + formatWait(result.waitMillis()) + ".";
            case STORAGE_ERROR -> "PvP status could not be saved. Contact an administrator; no change was made.";
        };
        player.sendMessage(Component.text(message, result.outcome() == PvpService.Outcome.CHANGED
            ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        if (result.outcome() == PvpService.Outcome.CHANGED) refresh.accept(player);
        return true;
    }

    private static String formatWait(long millis) {
        long seconds = millis / 1_000 + (millis % 1_000 == 0 ? 0 : 1);
        return (seconds / 3_600) + "h " + (seconds / 60 % 60) + "m " + (seconds % 60) + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("on", "off").stream()
            .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of();
    }
}
