# Judgment

Judgment is a Paper `1.21.11` combat-log plugin designed for a survival-friendly SMP. Instead of automatically punishing every disconnect, it asks the credited opponent whether the combat logger should be killed.

## Requirements

- Minecraft/Paper `1.21.11`
- Java 21
- Optional: LifeStealZ

Judgment declares `softdepend: [LifeStealZ]`. When the credited player is online, punishment kills are applied as player-caused kills so LifeStealZ can read normal Bukkit kill credit.

## Combat Tags

Judgment tracks combat with a per-player stack of directional tags.

- `INCOMING`: another player hit this player.
- `OUTGOING`: this player hit another player.
- Incoming tags have higher priority than outgoing tags.
- Within the same priority, the newest or most recently refreshed tag is on top.
- Duplicate tags for the same owner, opponent, and direction refresh the timer instead of adding another stack entry.
- Each tag expires independently.

Example:

- If `A` hits `B`, `A` gets an outgoing tag credited to `B`, and `B` gets an incoming tag credited to `A`.
- If `B` then hits `A`, both players keep both directions in their stack.
- If `A` logs out, Judgment uses only `A`'s top active tag to decide who receives the prompt.

## Combat Log Flow

When a tagged player logs out:

1. Judgment prunes expired tags from that player's stack.
2. Judgment checks the top active tag only.
3. If the credited player on that top tag is offline, no prompt is sent.
4. If the credited player is online, they receive:
   `[PLAYER] combat logged. Would you like to kill them? [YES] [NO]`
5. The prompt expires after the configured prompt timeout.
6. If the credited player clicks `YES`, the offender is killed on relog.
7. If the offender is already online when `YES` is clicked, they are killed immediately.
8. If the credited player clicks `NO` or the prompt expires, no punishment is stored.

If the credited killer is offline when an approved offender relogs, Judgment still kills the offender without kill credit and clears the pending punishment.

## Player Messages

- On combat entry: `You are now in combat for [X] seconds!`
- On normal combat expiry: `You have left combat, you may now log out.`
- When a death clears a combat tag for the surviving player: `You are no longer in combat with [PLAYER].`
- Prompt recipients are told how long the prompt lasts.
- Prompt recipients are notified when their prompt expires.

## Death Cleanup

When a player dies, Judgment clears:

- the dead player's full combat stack,
- every tag on other players that references the dead player.

This prevents death-screen disconnects, such as clicking `Title screen`, from being interpreted as combat logs.

## Commands

### `/judgment settings`

Opens the admin settings GUI.

Editable settings:

- Combat tag duration
- Prompt timeout

Displayed but locked for now:

- Punishment mode

### `/judgment debug stack [player]`

Prints a player's current combat tag stack in priority order.

- Requires `judgment.admin`.
- `[player]` defaults to yourself when run by a player.
- Console must provide a player name.
- Tab completion supports `debug`, `stack`, and online player names.

Each stack line includes:

- rank,
- direction,
- opponent,
- credited player,
- remaining time,
- whether the top credited player is currently online and prompt-eligible.

### `/judgment choice <caseId> <yes|no>`

Internal command used by clickable chat buttons. Players normally do not type this manually.

## Permissions

### `judgment.admin`

Default: `op`

Allows:

- opening `/judgment settings`,
- using `/judgment debug stack [player]`,
- receiving admin tab completions.

## Config

Default `config.yml`:

```yaml
combat-tag-seconds: 30
punishment-mode: relog
prompt-timeout-seconds: 10
```

### `combat-tag-seconds`

How long each combat tag lasts after a PvP hit.

### `prompt-timeout-seconds`

How long the credited player has to click `YES` or `NO`.

### `punishment-mode`

Current supported behavior is `relog`.

`instant` is reserved for a future release. If set manually, Judgment warns and falls back to relog behavior.

## Build

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

The plugin jar is created under:

```text
build/libs/Judgment-0.1.0.jar
```
