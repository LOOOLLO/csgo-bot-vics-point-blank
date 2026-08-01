# CS:GO style BOT (Vic's Point Blank)

Bomb-defusal rounds in Minecraft. Plant and defuse a real C4 on custom bomb sites, fight
squad-based T/CT bots that use Point Blank weapon stats, with warmup, freeze time, half-time
and a synced HUD.

**Minecraft 1.21.11 · Fabric · Java 21**

📦 **Download:** [Modrinth](https://modrinth.com/mod/csgo-bot-vics-point-blank)
📦 **Download:** [CurseForge]([https://modrinth.com/mod/csgo-bot-vics-point-blank](https://www.curseforge.com/minecraft/mc-mods/csgo-style-bot-vics-point-blank)

---

## ⚠️ Please read this first

Three things you should know before you download, open an issue, or read the source:

1. **This mod was written with AI.** I used AI (Claude) to write essentially all of the Java
   code in this repository. I directed it, tested it in-game, and iterated until it played the
   way I wanted — but I did not hand-write this codebase.
2. **I am not a full-time modder.** I don't do this professionally and I have no formal
   background in Minecraft modding. This is a hobby.
3. **I make mods to play alone.** I build them for my own singleplayer worlds and I normally
   use them only for myself. This one turned out fun enough that I decided to publish it —
   it's the only one I've released.

So: expect hobby-quality code, expect rough edges, and please set your expectations
accordingly. It works, I play it, and I hope you enjoy it. But it is not a professionally
maintained mod.

---

## Features

- **Plantable, defusable C4** with an accelerating beep and a real detonation timer.
- **Terrorist and Counter-Terrorist bots** with squad roles and coordinated tactics.
- **Four difficulty levels** (EASY → UNFAIR) with per-bot variance so they don't feel robotic.
- **Full match structure**: warmup, freeze time, round timer, half-time side swap, first to 13.
- **Server-synced HUD** showing score, alive counts, round timer and bomb countdown.
- **Bomb sites you define yourself** with a wand, plus map save/load.
- **Kill feed** with headshot markers, and spectating your living teammates while dead.
- **~40 configurable JSON values** in `<world>/csgo_mc/config.json`.

Bots pull their weapon stats from **Vic's Point Blank**, so the guns behave like Point Blank
guns rather than like generic Minecraft mobs.

## Requirements

| Dependency | Notes |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | ≥ 0.19.3 |
| Fabric API | required |
| [Vic's Point Blank](https://modrinth.com/mod/vics-point-blank) | required — this mod is built on top of it |
| [GeckoLib](https://modrinth.com/mod/geckolib) | required |
| Java | 21+ |

## Quick start

1. Place a **Bomb Site** block flush with the floor.
2. Take the **Bomb Site Wand**: click the site block, then two opposite corners to define the
   plantable area.
3. `/csgo match spawnT ~ ~ ~` while standing on the T spawn.
4. `/csgo match spawnCT ~ ~ ~` while standing on the CT spawn.
5. `/csgo join T` (or `CT`), then `/csgo start`.

`/csgo help` prints the full command list in-game.

## Commands

### Everyone

| Command | What it does |
| --- | --- |
| `/csgo join <T\|CT>` | Pick a team (gives weapon, armour, ammo) |
| `/csgo spec` | Follow the next living teammate while dead |
| `/csgo spec free` | Detach to the free camera |
| `/csgo status` | Phase, score, spawns, sites |
| `/csgo site list` | List every registered bomb site |
| `/csgo site show` | Outline the site areas with particles |
| `/csgo help` | Full help page |

### Admin (permission level 2)

| Command | What it does |
| --- | --- |
| `/csgo start [T\|CT]` | Start the match (joins the team first) |
| `/csgo nextround` | Force the next round |
| `/csgo skipwarmup` | End the warmup immediately |
| `/csgo reset` | Wipe scores, teams, bots and live C4s |
| `/csgo match difficulty <easy\|normal\|hard\|unfair>` | Set bot difficulty |
| `/csgo match spawnT <x y z>` / `spawnCT <x y z>` | Set spawns |
| `/csgo numberofT <0-20>` / `numberofCT <0-20>` | Set bot counts |
| `/csgo site name <name>` | Rename the selected site |
| `/csgo site pos1 <x y z>` / `pos2 <x y z>` | Set the area by command |
| `/csgo site clear` | Remove every registered site |
| `/csgo map save <name>` | Store spawns, bots and sites |
| `/csgo map load <name>` / `delete <name>` / `list` | Manage saved maps |
| `/csgo config show` / `reload` / `save` | Manage the config file |

## Configuration

The config is written to `<world>/csgo_mc/config.json` the first time the world loads, and can
be reloaded in-game with `/csgo config reload`. Some of the values worth knowing:

| Key | Default | Meaning |
| --- | --- | --- |
| `roundTimeSeconds` | `115` | Round length |
| `roundsToWin` | `13` | Rounds needed to win the match |
| `halfTimeRound` | `12` | Round after which the sides swap (`0` disables) |
| `freezeTimeSeconds` | `10` | Freeze time at round start |
| `bombTimerSeconds` | `40` | C4 detonation timer |
| `playerDefuseTicks` | `200` | Defuse time without a kit (`100` with one) |
| `plantCancelsOnDamage` | `true` | Getting shot cancels a plant/defuse |
| `botsT` / `botsCT` | `5` / `5` | Bots per side |
| `friendlyFire` | `false` | Team damage |
| `usePointBlankDamage` | `false` | Use Point Blank's own damage numbers for bots |
| `botsCanHeadshot` | `true` | Headshots, at `headshotMultiplier` (2.5×) |
| `showKillFeed` | `true` | Kill feed HUD |

Each difficulty (`easy`, `normal`, `hard`, `unfair`) has its own `health`, `speed`,
`reactionTicksMin/Max`, `spread` and `damageMultiplier`.

## Building from source

The build needs the **Point Blank jar as a local file**, because it's consumed through a
`flatDir` repository pointing at the parent folder (see `build.gradle`):

```
<parent folder>/
├── pointblank-fabric-1.21.11-2.0.1.jar   <- must be here
└── csgo-mc-mod/                          <- this repository
```

That jar is not redistributable, so it is **not** included here — download it from
[Vic's Point Blank](https://modrinth.com/mod/vics-point-blank) and drop it next to the
repository folder. Then:

```bash
./gradlew build
```

The finished jar lands in `build/libs/`.

There is no CI workflow in this repository, for exactly this reason: the build cannot run on a
clean machine without that third-party jar present.

## Issues and contributions

Bug reports are welcome and I'll read them, but please keep in mind everything in the
["Please read this first"](#️-please-read-this-first) section: **this is a hobby project,
written with AI, by someone who is not a full-time modder and normally only plays his own mods
alone.** I fix things when I have time and when I can reproduce them. I may not be able to help
with complex modpack conflicts, and response times will be slow.

Pull requests are fine too. Since I'm not a professional modder, a PR that explains *why* in
plain language is much more useful to me than one that assumes I'll recognise the pattern.

## Credits

- **Vic's Point Blank** — the weapon mod everything here is built on top of.
- **GeckoLib** — model and animation library.
- **Fabric** — loader and API.
- **AI (Claude)** — wrote the code in this repository, under my direction and testing.

## License

All Rights Reserved. See [LICENSE](LICENSE).

You may download and use the mod, including in modpacks with credit and a link back. You may
not redistribute modified versions or re-upload it elsewhere as your own.

---

*One last time, so nobody is surprised: this mod was programmed with AI, I'm not a full-time
modder, and I make mods for myself to play alone — I just decided to publish this one.*
