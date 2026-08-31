package com.bountysmp.judgment.pvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

public final class PvpPresentation {
    private static final Component PREFIX = Component.text("[", NamedTextColor.GRAY)
        .append(Component.text("PvP", NamedTextColor.RED))
        .append(Component.text("] ", NamedTextColor.GRAY));
    private record TeamChange(String original, Team derived) {}
    private record TabChange(Component original, Component applied) {}
    private final PvpService service;
    private final Map<Scoreboard, Map<String, TeamChange>> teams = new IdentityHashMap<>();
    private final Map<UUID, TabChange> tabs = new HashMap<>();

    public PvpPresentation(PvpService service) {
        this.service = service;
    }

    public void refreshAll() {
        var boards = new HashSet<Scoreboard>();
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player viewer : Bukkit.getOnlinePlayers()) boards.add(viewer.getScoreboard());
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (service.isPvpEnabled(player.getUniqueId())) {
                if (!tabs.containsKey(player.getUniqueId())) {
                    Component original = player.playerListName();
                    Component applied = PREFIX.append((original == null ? Component.text(player.getName()) : original).colorIfAbsent(NamedTextColor.WHITE));
                    tabs.put(player.getUniqueId(), new TabChange(original, applied));
                    player.playerListName(applied);
                }
                for (Scoreboard board : boards) applyTeam(board, player);
            } else {
                remove(player);
            }
        }
    }

    private void applyTeam(Scoreboard board, Player player) {
        var changes = teams.computeIfAbsent(board, ignored -> new HashMap<>());
        if (changes.containsKey(player.getName())) return;
        Team original = board.getEntryTeam(player.getName());
        String originalName = original == null ? null : original.getName();
        Team shared = changes.values().stream()
            .filter(change -> java.util.Objects.equals(change.original(), originalName))
            .map(TeamChange::derived).findFirst().orElse(null);
        if (shared != null) {
            changes.put(player.getName(), new TeamChange(originalName, shared));
            if (original != null) original.removeEntry(player.getName());
            shared.addEntry(player.getName());
            return;
        }
        String name = "judp" + player.getUniqueId().toString().replace("-", "").substring(0, 12);
        // Never take over a team owned by another plugin or by server administrators.
        if (board.getTeam(name) != null) return;
        Team derived = board.registerNewTeam(name);
        if (original != null) {
            derived.displayName(original.displayName());
            derived.prefix(PREFIX.append(original.prefix()));
            derived.suffix(original.suffix());
            if (original.color() != null) derived.color(NamedTextColor.nearestTo(original.color()));
            derived.setAllowFriendlyFire(original.allowFriendlyFire());
            derived.setCanSeeFriendlyInvisibles(original.canSeeFriendlyInvisibles());
            for (Team.Option option : Team.Option.values()) derived.setOption(option, original.getOption(option));
        } else {
            derived.prefix(PREFIX);
        }
        changes.put(player.getName(), new TeamChange(original == null ? null : original.getName(), derived));
        if (original != null) original.removeEntry(player.getName());
        derived.addEntry(player.getName());
    }

    public void remove(Player player) {
        TabChange tab = tabs.remove(player.getUniqueId());
        if (tab != null && tab.applied().equals(player.playerListName())) player.playerListName(tab.original());
        teams.forEach((board, changes) -> {
            TeamChange change = changes.remove(player.getName());
            if (change == null) return;
            if (change.derived().equals(board.getEntryTeam(player.getName()))) {
                change.derived().removeEntry(player.getName());
                Team original = change.original() == null ? null : board.getTeam(change.original());
                if (original != null) original.addEntry(player.getName());
            }
            if (change.derived().getEntries().isEmpty()) change.derived().unregister();
        });
    }

    public void close() {
        for (Player player : Bukkit.getOnlinePlayers()) remove(player);
        teams.clear();
        tabs.clear();
    }
}
