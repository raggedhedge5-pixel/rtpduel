# RTP Duels

A standalone plugin for **Bukkit, Spigot, Paper and Folia** (Minecraft 1.8 up to the newest release, one jar): `/duel <player>` to challenge someone, with clickable chat buttons
and a full confirmation GUI, and on accept a two-phase movement-locked countdown before both
players land on the same random LAND-ONLY spot in the Overworld.

## Platforms and versions

One jar runs everywhere - it is not a different plugin per version, so every version behaves exactly the same.

| Platform | Minecraft versions | Notes |
|---|---|---|
| Bukkit / Spigot | 1.8 - newest (26.x) | Everything works. A brand-new random chunk generates on the main thread, so a very occasional brief pause is normal. |
| Paper (and forks such as Purpur) | 1.8 - newest (26.x) | Same as above, but the random spot is loaded asynchronously and the teleport uses `teleportAsync`, so no pause. |
| Folia | 1.19.4 - newest | `folia-supported: true`. Every player action runs on that player's own region thread, the random spot is read on the thread that owns that chunk, and both duelists are teleported with `teleportAsync`, so the two players can be in different regions. |

Java: runs on Java 8 and up (the jar is built as Java 8 bytecode), so it loads on every server Java version from 8 to 25.

How one jar covers 1.8 to 26.x: anything that changed between versions (item names after 1.13, sound names,
titles, the `Biome` type, `teleportAsync`, the Folia schedulers) is looked up at run time and has a fallback, so
a missing method can never crash a duel. On servers older than 1.13 the head and glass panes use their old item
ids; on servers without the title API the countdown is sent as chat lines instead.

## Build

Push to GitHub and grab the artifact from Actions (the workflow is already included at
`.github/workflows/build.yml`), or build locally with `mvn clean package` (needs JDK 21 + Maven).
Output is `target/RTPDuels.jar`.

`pom.xml` compiles against the Paper API `1.21.11-R0.1-SNAPSHOT` (the same one the old QuickDuel build used) but
produces Java 8 bytecode. If that API ever fails to resolve, the workflow automatically retries against the old
1.16.5 API.

### Testing on real servers (optional, recommended before uploading)

Actions tab -> **Test on real servers** -> *Run workflow*. It boots a real Paper server for 22 versions
(1.8.8 up to 26.3) and a real Folia server for 9 versions, installs the jar, lets the plugin run its built-in
self-check (items, GUI, scheduler, random land spot) and shows a green tick or a red cross per version. Versions that
have no download on the PaperMC site are shown as skipped. The check only runs when the server is started with
`-Drtpduels.selftest=true`, so it never runs on a normal server. Spigot itself cannot be downloaded the same way,
but Paper is a superset of the Spigot API, so a Paper pass is a strong signal for Spigot too.

## How it works

1. **`/duel <player>`** sends a challenge. The target gets a chat message with clickable
   **[Accept]** / **[Decline]** buttons (real click-to-run-command buttons; on any server where those can't be
   sent, the line falls back to plain text telling the player to type the commands), plus `/duel accept` / `/duel decline` work as typed commands too. Only the most recent
   request to a given player is tracked, so accept/decline always resolves to the latest one.
   Requests expire after `request-timeout-seconds` (default 60).

2. **Accepting opens a confirmation GUI** - it does NOT immediately start the duel. The GUI shows:
   - The challenger's head (their real skin), with hover text showing the **biome of the exact
     spot you're about to be teleported to** (not wherever they currently are), their name, and
     their TimeSMP rank if both TimeSMP and PlaceholderAPI are installed.
   - A green **ACCEPT** pane and a red **CANCEL** pane.

   The destination is rolled once, the moment the GUI opens - the biome shown and the spot you
   actually land on are guaranteed to be the same roll, not two different random rolls.

3. **Clicking CANCEL only closes the confirmation** - it does **not** decline the underlying duel
   request. The request stays pending exactly as it was; running `/duel accept` again just reopens
   the GUI. Only `/duel decline` (or clicking the Decline button in chat) actually declines and
   notifies the challenger. Pressing Escape to close the GUI without clicking anything behaves the
   same as clicking CANCEL.

4. **Clicking ACCEPT starts the countdown:**
   - Both players are frozen in place (can't move, can't deal or take damage, **can't use any
     command at all** - no exceptions, players with `quickduel.admin` bypass this) with a
     "5, 4, 3, 2, 1 - Don't move!" title countdown at their **current** location.
   - **Nobody else can teleport within `duel-teleport-protection-radius` blocks of either player**
     (default 15) for the entire time they're frozen - blocks `/tpa`, warps, anything landing near
     an active duel. This doesn't affect the duelists' own scripted teleport, since they're still
     marked frozen themselves at that moment.
   - They're then teleported to the pre-rolled destination, **landing on exactly the same Y level**
     (only offset in X) **and facing each other** (each one looks at the other's exact landing spot,
     computed after both teleports land so the direction is always correct).
   - A second "3, 2, 1, GO!" title countdown plays, still frozen - same move/damage/command/
     teleport-proximity lock as the first phase.
   - Only after "GO!" are they unfrozen and able to move/fight/use commands again.
   - If either player disconnects mid-countdown, it's detected within about a second and both are
     cleanly unfrozen and released from the sequence.

5. **Land only, never water** - the random spot search explicitly excludes water (and lava, and
   the void), re-rolling up to `max-location-attempts` times before falling back to world spawn.
   Always in the Overworld specifically (`teleport-world` in config, default `"world"`),
   regardless of which dimension either player started in.

## Commands

- `/duel <player>` - challenge someone
- `/duel accept` - open the confirmation GUI for your most recent pending request
- `/duel decline` - actually decline your most recent pending request

Permission `quickduel.use` (default: everyone).

## Config (`config.yml`)

- `teleport-world` - exact world name duels happen in (always the Overworld)
- `teleport-radius` - max distance from that world's spawn, in blocks
- `request-timeout-seconds` - how long a challenge stays valid
- `max-location-attempts` - re-roll attempts before falling back to spawn
- `spawn-separation` - how many blocks apart the two duelists land
- `duel-teleport-protection-radius` - how close (blocks) a third party can't teleport to a frozen duelist

## Optional integrations

- **PlaceholderAPI** (soft-depend) - if installed, and TimeSMP is also installed with its own
  PlaceholderAPI expansion registered, the confirmation GUI shows the challenger's TimeSMP rank in
  the head's hover text. RTP Duels has no direct dependency on TimeSMP itself - it just asks
  PlaceholderAPI to resolve `%timesmp_rank_tag%` the way anything else would. If either plugin/
  placeholder isn't present, that line is silently omitted - nothing breaks.

## Notes / assumptions

- No arena/return system, no health/inventory reset, no win/loss tracking - just the challenge/
  confirm/countdown/teleport flow you asked for. Say the word if you want any of that layered on.
- Landing in unloaded terrain at a random spot up to `teleport-radius` blocks out can force that
  chunk to generate on the spot, which may cause a brief pause. This isn't meant to be called in
  rapid succession.
- This is a completely separate plugin from TimeSMP and RexonRTP - it doesn't touch either, beyond
  the optional read-only PlaceholderAPI rank lookup described above. (PlaceholderAPI is called by
  reflection, so no PlaceholderAPI jar is needed to build or run it.)
- Permissions are still `quickduel.use` and `quickduel.admin` (unchanged from QuickDuel). The plugin's name
  is now `RTPDuels`, so its config folder is `plugins/RTPDuels/` - copy your old `plugins/QuickDuel/config.yml` over
  if you changed any values. Remove the old `QuickDuel.jar` so you don't run both.
- `plugin.yml` describes `quickduel.admin` as bypassing both locks, but (as in the original code) only the command
  lock is bypassed; the teleport-protection check applies to everyone who is not a frozen duelist. This is kept
  exactly as it was.
