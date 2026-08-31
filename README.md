# Judgment

Judgment is a Paper `26.2` combat-log plugin designed for a survival-friendly SMP. Instead of automatically punishing every disconnect, it asks the credited opponent whether the combat logger should be killed.

## Requirements

- Minecraft/Paper `26.2`
- Java 25
- Optional: LifeStealZ

Judgment declares `softdepend: [LifeStealZ]`. When the credited player is online, punishment kills are applied as player-caused kills so LifeStealZ can read normal Bukkit kill credit.

## PvP Status

`/pvp` toggles PvP; `/pvp on` and `/pvp off` set it explicitly. Everyone can use
these commands. Both players must have PvP **ON** to fight. New preferences default
to **OFF**; admins can change that default without changing existing saved statuses.
The first change is immediately available unless combat prevents disabling.

Each successful change starts a shared **24-hour cooldown**, whether enabling or
disabling. Repeating the current status or a rejected request does not restart it.
Turning PvP off also requires no active combat tag and **10 minutes since the final
combat tag ended**. Both waits must finish. Death starts the post-combat wait if it
clears the final tag, including for affected opponents. Renewed combat delays eligibility.
All waits include offline time and survive restarts; logging out does not end combat early.

Enabled players have a `[PvP]` prefix above their head and in the tab list, with red
`PvP` lettering and gray brackets. Turning PvP off removes it. Existing name formatting
and team settings are preserved and restored; external scoreboard managers are not supported.

Protection covers melee, projectiles, harmful splash/lingering potions, owned pets,
and explosions with an identifiable player source. Harmful or neutral potion mixtures
are withheld from protected targets; beneficial-only potions and other targets remain
unaffected. Blocked attacks do not create combat tags. Self-damage, ordinary mobs,
environmental damage, and untraceable traps (such as placed lava) are unchanged.
This is not a general anti-grief system. Approved Judgment punishments still execute
regardless of PvP status and do not start new combat tags.

Use `/judgment settings` to change the default status, toggle cooldown, and
post-combat wait. Duration changes affect existing waits. Enter `24h`, `10m`, or `0s`
(to disable a delay); active combat always prevents disabling PvP.

Player preferences and combat timing are stored by UUID in `plugins/Judgment/pvp-players.yml`.
Do not delete this file when upgrading. If loading or saving fails, PvP attacks and
status changes are blocked until storage is repaired and the server restarted. Errors
are logged; damaged data is never silently replaced with default preferences.

Future modules can use `JudgmentPlugin#getPvpService()`, `isPvpEnabled(UUID)`, and
`canAttack(UUID, UUID)` on the server thread. Dragon-egg pickup restrictions are not implemented.

### Dragon Egg Privilege

The administrator settings menu has a `Dragon Egg` sub-menu. The privilege is off by
default. When enabled, a player carrying a dragon egg receives whichever effects are
enabled there: Glow, Strength I, and Speed I. Effects are permanent while the egg is
held and are removed when the player no longer has one. The plugin marks only its own
effects, so unrelated potion effects are preserved. This privilege is cosmetic and
does not change who may pick up an egg.

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

### `/judgment pvp <player> <on|off>`

Administrators with `judgment.admin` can correct a player's PvP status. This
intentionally bypasses that player's cooldown and combat wait, and notifies both
the administrator and affected player. The target must be online; use tab completion
for player names and status values.

### `/judgment settings`

Opens the admin settings GUI.

Editable settings:

- Combat tag duration
- Prompt timeout
- Default PvP status for new preferences
- PvP toggle cooldown
- PvP post-combat wait

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
pvp:
  default-enabled: false
  toggle-cooldown-seconds: 86400
  post-combat-delay-seconds: 600
```

### `combat-tag-seconds`

How long each combat tag lasts after a PvP hit.

### `prompt-timeout-seconds`

How long the credited player has to click `YES` or `NO`.

### `punishment-mode`

Current supported behavior is `relog`.

`instant` is reserved for a future release. If set manually, Judgment warns and falls back to relog behavior.

## Build

Use JDK 25. The included Gradle wrapper downloads Gradle 9.1.0 automatically.

macOS/Linux:

```sh
./gradlew clean build
```

Windows:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

The plugin jar is created under:

```text
build/libs/Judgment-0.3.0.jar
```

Every successful `build` also copies the plugin to
`~/Documents/Minecraft localhost/plugins/Judgment.jar`, replacing that file on
subsequent builds. Restart the local server to load the updated plugin.

To update, stop your Paper 26.2 server, replace the old Judgment jar in `plugins/`
with this jar, and restart. Keep the existing `plugins/Judgment/` folder to preserve
settings and pending punishments. This release requires Java 25 and does not target
Paper 1.21.11. Verify that your optional LifeStealZ version also supports Paper 26.2.

## Local Verification

Automated tests cover status transitions, timing, persistence, attack protection,
command registration, punishment behavior, and prefix restoration.
For an in-game check after restarting Paper, connect two clients and verify:

1. Both start with PvP off and cannot damage each other.
2. Enable one: damage remains blocked. Enable both: combat and `[PvP]` prefixes work.
3. Use shortened admin delays to check combat expiry, death, and disabling.
4. Check red prefix lettering above players and in tab, with no duplicated prefix.
5. Reconnect/restart and confirm saved status and waits; check normal PvE damage.
