# Cope Nights

Jump scares, Herobrine, Blood Moons and hordes for Minecraft servers, by Cope Studios.

- **Minecraft:** 1.20.1 and newer (tested against the 1.20 and 1.21.11 APIs)
- **Servers:** Spigot, Paper, Purpur, Arclight
- **Java:** 17 or newer (1.20.5+ servers need Java 21 anyway)
- **Needs:** [CopeCore](../CopeCore) 1.1.0 or newer
- **Optional:** Cope Bosses (horde bosses), PlaceholderAPI (placeholders)

---

## Contents

1. [Install](#install)
2. [Upgrading from 0.0.32 or older](#upgrading-from-0032-or-older)
3. [Features](#features)
4. [Commands and permissions](#commands-and-permissions)
5. [Files](#files)
6. [Placeholders](#placeholders)
7. [Working with other Cope plugins](#working-with-other-cope-plugins)
8. [For developers (API)](#for-developers-api)
9. [Building](#building)
10. [Version notes](#version-notes)
11. [Changelog](#changelog)

---

## Install

1. Put `CopeCore-1.1.0.jar` **and** `CopeNights-0.1.0.jar` in `plugins/`.
2. Start the server. Cope Nights creates `plugins/CopeNights/` with `config.yml`, `bloodmoon.yml`,
   `messages.yml` and `data.yml`.
3. `/coni toggle on` turns on random server-wide scares. Blood Moons run on their own (every 7 days by default).

If CopeCore is missing or too old, Cope Nights tells you in the console and disables itself.

## Upgrading from 0.0.32 or older

Drop in the new jar (and CopeCore). On the first start Cope Nights:

- moves every **text** you customized from `config.yml` (prefix, whisper, jumpscare, sky-drop titles,
  fake-join) and `bloodmoon.yml` (`messages:`, `death-messages-text:`) into `messages.yml`;
- leaves texts you never changed alone, so you get the new defaults;
- removes the old text keys from `config.yml` / `bloodmoon.yml`;
- adds any new settings to your files (your values are kept);
- keeps your auto-scare ON/OFF state and exempt players (`data.yml`).

Your old formatting keeps working: `&c`, `&#FF8C1A`, `<gradient:#A:#B>` and `%player%` are all still
understood. New texts use MiniMessage (`<#FF8C1A>`, `<bold>`, `<hover:...>`, `<click:...>`).

## Features

### Scares

All scares are harmless and undo themselves. Fake blocks and fake entities are only visible to the scared
player.

| Scare | What happens |
|---|---|
| `herobrine` | Herobrine appears in the distance, stares, and vanishes when looked at or approached |
| `stalker` | Herobrine stands right behind them until they turn around |
| `jumpscare` | Elder guardian face, screams and a title |
| `sounds` | A random creepy sound right behind them |
| `footsteps` | Footsteps creeping up from behind (sometimes he's really there) |
| `creeper` | A creeper hiss in their ear (no explosion) |
| `whisper` | A chat whisper "from Herobrine" |
| `blackout` | Blinded in the dark with a heartbeat, then a roar |
| `lightning` | Harmless lightning next to them |
| `sky_drop` | Yanked 100 blocks up, free fall, then put back exactly where they stood (no fall damage) |
| `buried` | Gravel caves in around them for a few seconds |
| `lights_out` | Nearby torches and lanterns blow out one by one |
| `fake_tnt` | Lit TNT appears behind them and "explodes" (no damage) |
| `turn_around` | Their camera spins around... Herobrine is right there |
| `fake_join` | "Herobrine joined the game", a chat message, then he leaves |
| `knock` | Knocking on the nearest door, then it creaks open by itself |
| `surrounded` | Mob groans closing in from every side, then a breath behind them |
| `phantom_hit` | Something invisible hits them 3 times and laughs (no damage) |
| `random` | A weighted random pick (`auto.weights` in config.yml) |

If a scare can't run (no room for Herobrine, no torches nearby...), a similar one that always works runs instead.

**Auto mode** (`/coni toggle on`): every `auto.check-interval-seconds`, each online player has
`auto.chance-per-check` chance to be scared, with a per-player cooldown. Filters: night only, dark only,
survival only, worlds.

### Blood Moon

Every `every-days` Minecraft days:

- a warning at sunset, a big title at moonrise, a red boss bar (time left, wave, kills), a red screen tint and
  a thunderstorm;
- more hostile mobs, with more health, damage and speed; some are swapped for special mobs, some are giants;
- **hordes** in waves, aimed at random players, growing every wave, led by a glowing captain, sometimes with
  a Cope Bosses boss;
- random Blood Moon scares all night (even when auto mode is off);
- sleeping is blocked;
- more drops and XP; at dawn the mobs vanish and players who never died get rewards (`reward-commands`);
- custom death messages for horde, Blood Moon, mob and player kills.

Almost everything is editable in-game with `/coni bloodmoon` (a chest menu with three pages: main, hordes and
special mobs). Number buttons: left click +, right click −, shift ×10, **Q** to type an exact value.

## Commands and permissions

Main command `/coni` (aliases `/cosc`, `/copenights`). `/coni help` shows a clickable list.

| Command | What it does |
|---|---|
| `/coni <player\|all\|random> [type]` | Scare someone (type defaults to random) |
| `/coni toggle [on\|off]` | Server-wide random scares |
| `/coni list` | All scare types (click one to try it on yourself) |
| `/coni status` | Auto-scare settings |
| `/coni exempt <player>` | Toggle a player's exemption from auto scares |
| `/coni clear` | Remove every active Herobrine |
| `/coni bloodmoon` | Open the Blood Moon menu (alias `bm`) |
| `/coni bloodmoon start [world]` | Start a Blood Moon now (skips to night) |
| `/coni bloodmoon stop` | End every active Blood Moon |
| `/coni bloodmoon status` | Next Blood Moon per world |
| `/coni bloodmoon horde <player\|all\|random> [size]` | Summon a horde (works without a Blood Moon) |
| `/coni reload` | Reload config.yml, bloodmoon.yml and messages.yml |

| Permission | Default | Meaning |
|---|---|---|
| `copenights.admin` | op | Every command and the menu |
| `copenights.exempt` | false | Never targeted by random scares (auto and Blood Moon) |

## Files

| File | What's in it |
|---|---|
| `config.yml` | Auto scares, Herobrine, sky drop settings |
| `bloodmoon.yml` | Every Blood Moon and horde setting |
| `messages.yml` | **Every text**: chat lines, titles, boss bar, menu, death messages, whispers |
| `data.yml` | Saved state (auto ON/OFF, exempt players). Don't edit while the server runs |

New settings from updates are added to your files automatically. A broken file is repaired: the broken copy
is kept as `<name>.broken-<date>`.

### Text formatting (messages.yml)

```yaml
# MiniMessage
start-title: "<gradient:#5A0000:#FF2A00:#5A0000><bold>BLOOD MOON</bold></gradient>"
# hover + click
help-line: "<click:suggest_command:'/<command> '><hover:show_text:'Click!'>/<command></hover></click>"
# old codes still work
end: "&#FFB366The blood moon fades."
```

Placeholders can be written `<player>`, `{player}` or `%player%`. Each message lists the ones it supports.
Set a message to `""` to turn it off. Names that come from players (renamed mobs and weapons in death
messages) are always inserted as plain text, so they can't inject colors or click events.

### Player states (skip lists)

Other Cope plugins mark players with shared CopeCore **states**: `combat` (CSCombat), `in-duel` /
`in-arena` (CSSPVP), `boss-fight` (Cope Bosses), `editing` (someone typing in an editor).

- `config.yml` → `auto.skip-states`: these players are never scared (auto and Blood Moon scares).
- `bloodmoon.yml` → `horde-skip-states`: these players are never picked for automatic hordes.

## Placeholders

Needs PlaceholderAPI. Provided through CopeCore, so no extra expansion is needed.

| Placeholder | Value |
|---|---|
| `%cope_nights_auto%` | `ON` / `OFF` |
| `%cope_nights_exempt%` | `true` / `false` |
| `%cope_nights_bloodmoon%` | `true` when a Blood Moon is active in the player's world |
| `%cope_nights_wave%` | Current horde wave (0 = none) |
| `%cope_nights_next%` | Days until the next Blood Moon (0 = tonight) |

## Working with other Cope plugins

- **Cope Bosses:** hordes can bring a boss (`horde-boss-chance`, `horde-bosses`, `horde-boss-command`).
  The command runs as the horde's target by default (with `horde-boss-permissions` for that one command), so the
  boss spawns where they stand. Set the chance to 0 if you don't use Cope Bosses.
- **CSCombat / CSSPVP / Cope Bosses:** their player states are respected (see above).
- **Any plugin:** can listen to the events or use the service below.

## For developers (API)

Add Cope Nights as a `provided` dependency and `softdepend: [CopeNights]`.

### Events

| Event | When | Cancellable |
|---|---|---|
| `BloodMoonStartEvent` | Just before a Blood Moon starts (`isForced()` = started by an admin) | yes |
| `BloodMoonEndEvent` | When it ends: kills, waves, survivors, `isNatural()` | no |
| `HordeSpawnEvent` | Before a horde is sent; `setSize()` changes it | yes |

```java
@EventHandler
public void onHorde(HordeSpawnEvent event) {
    if (myArena.contains(event.getTarget())) {
        event.setCancelled(true);
    }
}
```

### Service

```java
Services.find(NightsService.class).ifPresent(nights -> {
    if (nights.isBloodMoon(player.getWorld())) {
        // double rewards during a Blood Moon
    }
    nights.scare(player, "knock");
    nights.spawnHorde(player, 10);
});
```

`NightsService` also has `wave`, `daysUntilBloodMoon`, `isBloodMoonMob`, `isHordeMob` and `scareTypes`.

## Building

```
# 1) CopeCore first (it must be in your local Maven repository)
cd CopeCore
mvn clean install

# 2) Cope Nights
cd ../CopeNights
mvn clean package
```

The jar is `target/CopeNights-0.1.0.jar`. It does not bundle CopeCore; CopeCore is its own plugin jar.

### Project layout

```
src/main/java/us/copestudios/copenights/
  CopeNights.java          main class: startup, shared state, reload
  LegacyMigration.java     one-time upgrade from 0.0.32 (texts -> messages.yml)
  NightsPlaceholders.java  %cope_nights_...%
  ScareType.java           the scare list
  scare/                   ScareManager, ExtraScares, HerobrineManager, AutoScareTask, ScareListener
  bloodmoon/               BloodMoonManager, BloodMoonSettings, BloodMoonListener, BloodMoonMenu, DeathMessageListener
  command/                 NightsCommand (/coni)
  api/                     events + NightsService
src/main/resources/
  plugin.yml, config.yml, bloodmoon.yml, messages.yml
  legacy/old-defaults.yml  old default texts, only used to detect what you customized when upgrading
```

## Version notes

| Feature | 1.20 – 1.20.4 | 1.20.5+ |
|---|---|---|
| Everything else | ✔ | ✔ |
| Giants and the oversized captain (`scale` attribute) | skipped (normal size) | ✔ |
| Special mobs BOGGED, BREEZE | skipped | 1.21+ |
| Directional hurt shake (`phantom_hit`, `fake_tnt`) | classic hurt flash | ✔ |

Particles, attributes and entity spawning go through CopeCore, which picks the right names and methods for the
running version (for example `LARGE_SMOKE` vs `SMOKE_LARGE`).

## Changelog

### 0.1.0
- Now built on **CopeCore**: messages, commands, menu, config loading, cooldowns, placeholders.
- Works on **1.20.1+** on Spigot, Paper, Purpur and Arclight (was Paper 1.21.1 only). Java 17.
- Every text moved to `messages.yml` (MiniMessage + old codes), with automatic migration of customized texts.
- New: `auto.skip-states` and `horde-skip-states` (no scares or hordes during duels, combat, boss fights...).
- New: `BloodMoonStartEvent`, `BloodMoonEndEvent`, `HordeSpawnEvent`, `NightsService`, PlaceholderAPI placeholders.
- New: `<boss>`, `<player>`, `<x>`... placeholders in `horde-boss-command` and `reward-commands` (old `%x%` still works).
- New: Q in the Blood Moon menu to type an exact value; per-setting names can be translated in messages.yml.
- Fix: renamed weapons and mobs could inject formatting into death messages.
- Fix: LIGHTS_OUT kept scanning after reaching its light limit.
- Fix: crashes on servers older than 1.20.5 (particle names, item glow, entity spawning).

### 0.0.32
- Last version before CopeCore.
