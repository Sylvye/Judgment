# Judgment

Judgment is a Paper `26.2` combat-log plugin designed for a survival-friendly SMP. Instead of automatically punishing every disconnect, it asks the credited opponent whether the combat logger should be killed.

When enabled in the settings menu, Judgment replaces an invisible killer's name in the public death message with a randomized, fixed-length eight-character obfuscated mask. The original vanilla death reason and weapon details are preserved. This feature is off by default. The real killer remains attached to the death internally, so kill-credit integrations continue to work without revealing their identity or username length.

## Requirements

- Minecraft/Paper `26.2`
- Java 25
- Optional: LifeStealZ

Judgment declares `softdepend: [LifeStealZ]`. When the credited player is online, punishment kills are applied as player-caused kills so LifeStealZ can read normal Bukkit kill credit.

## PvP Status

`/pvp` toggles PvP; `/pvp on` and `/pvp off` set it explicitly. Everyone can use
these commands. A player carrying a dragon egg cannot change their PvP status until
the egg is no longer in their inventory. Both players must have PvP **ON** to fight. New preferences default
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

Use `/judgment` or `/judgment settings` to open the modular settings menu. The PvP Tags
module controls the default status, toggle cooldown, post-combat wait, and optional End
and Nether toggle locks. Duration changes affect existing waits. Enter `24h`, `10m`, or `0s`
(to disable a delay); active combat always prevents disabling PvP.

The **Combat Rules** submenu separates managed items and abilities from explosives. It
controls elytra entry, elytra firework boosts, ender pearls, mace smashes, riptide,
spear lunges, firework-loaded crossbows, and placement of TNT, TNT minecarts, beds,
respawn anchors, and end crystals. Firework crossbows have a separate rule: positive
values start their cooldown when fired, while `-1` prevents loading fireworks. Each rule accepts
`-1` to ban the action, `0` for unrestricted use (the default), or a positive decimal
number of cooldown seconds. Leaving combat, dying, disconnecting, or restarting the
server clears active PvP-only item cooldowns. Optional settings show the combat timer and
each active item cooldown as independently stacked, decreasing boss bars.

Each item rule also has a scope. `PVP ONLY` applies while combat tagged and clears its
timer on combat exit. `GLOBAL` applies everywhere and persists its timer through
reconnects and restarts. In the item grid, left-click edits the cooldown and right-click
toggles the scope.

Every explosive also has a global player-damage multiplier. Shift-left-click its GUI
item to edit it: `0` removes player health damage, `0.5` halves it, `1` keeps vanilla
damage, and values above `1` amplify it. Mob damage, explosion knockback, and block
damage are unchanged.

Player preferences and combat timing are stored by UUID in `plugins/Judgment/pvp-players.yml`.
Do not delete this file when upgrading. If loading or saving fails, PvP attacks and
status changes are blocked until storage is repaired and the server restarted. Errors
are logged; damaged data is never silently replaced with default preferences.

Future modules can use `JudgmentPlugin#getPvpService()`, `isPvpEnabled(UUID)`, and
`canAttack(UUID, UUID)` on the server thread. Dragon-egg pickup restrictions are not implemented.

### Dragon Egg Privilege

Players with PvP disabled cannot pick up a dropped dragon egg. The administrator settings
menu also has a `Dragon Egg` sub-menu. The privilege is off by default. When enabled, a
player with PvP enabled who carries a dragon egg receives whichever effects are
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

- **CombatLog:** combat tag duration, prompt timeout, combat timer boss bar, and invisible-killer masking
- **PvP Tags:** default status, toggle cooldown, post-combat wait, End lock, and Nether lock
- **Combat Rules:** cooldown boss bars, per-rule cooldowns/bans/scopes, and explosive player-damage modifiers
- **Dragon Egg:** privilege and individual effect toggles

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
bossbars:
  item-cooldowns: false
  combat-timer: false
combat-item-cooldowns:
  elytra: 0
  fireworks: 0
  ender-pearls: 0
  mace-smash: 0
  riptide: 0
  lunge: 0
  firework-crossbows: 0
  tnt: 0
  tnt-minecarts: 0
  beds: 0
  end-crystals: 0
  respawn-anchors: 0
combat-item-scopes:
  elytra: pvp
  fireworks: pvp
  ender-pearls: pvp
  mace-smash: pvp
  riptide: pvp
  lunge: pvp
  firework-crossbows: pvp
  tnt: pvp
  tnt-minecarts: pvp
  beds: pvp
  end-crystals: pvp
  respawn-anchors: pvp
combat-item-damage-modifiers:
  tnt: 1.0
  tnt-minecarts: 1.0
  beds: 1.0
  respawn-anchors: 1.0
  end-crystals: 1.0
pvp:
  default-enabled: false
  toggle-cooldown-seconds: 86400
  post-combat-delay-seconds: 600
  prevent-toggle-in-end: false
  prevent-toggle-in-nether: false
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
build/libs/Judgment-0.4.0.jar
```

Every successful `build` also replaces `Judgment.jar` in both
`~/Documents/Minecraft localhost/plugins/` and
`~/Documents/TigerMCE Test Server/plugins/`. Restart the server to load the updated plugin.

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
6. Check TNT, minecart, bed, anchor, and crystal placement rules and player-damage modifiers.
