# Compact Debug HUD

## Purpose

Compact Debug is the builder-facing replacement presentation for the normal F3 information overload. It keeps F3 as the familiar entry point, shows only information that is useful during daily building, and remains part of the existing LazyBuilder Utility Manager client mod rather than creating another Fabric mod or another debug ecosystem.

The design goal is immediate comprehension:

```text
F3
→ coordinate first at the top-left
→ compact client + Minecraft context below it
→ server condition on the right
→ no diagnostic wall
```

This document owns presentation and interaction semantics. Fabric implementation mechanics stay under the existing Utility Manager module and must follow the Fabric Mod Development skill after this UI contract is frozen.

## Product contract

Compact Debug is deliberately narrow. Coordinate is the highest-priority builder datum and is visually separated from the other groups:

```text
COORDINATE
X -11   Y 71   Z -481

LEFT                                RIGHT
CLIENT                              SERVER
FPS        143                      World      TanaSamawa
CPU        28%                      CPU        18%
GPU        14%                      RAM        3.7 / 8 GB
RAM        664 / 6144 MB

WORLD
Facing     North (-Z)
Biome      Taiga
Time       08:34
```

The top-left coordinate block answers first:

```text
Where exactly am I?
```

The remaining left side answers:

```text
How is my client running?
What Minecraft context am I in?
```

The right side answers:

```text
Which world am I on?
How is the server machine doing?
```

Anything that does not help answer those questions stays out of the default F3 surface.

## Information hierarchy

### Top-left — Coordinate

Coordinate is a dedicated primary block, not a normal row inside `WORLD`.

Show:

```text
COORDINATE
X -11   Y 71   Z -481
```

Rules:

- Use the explicit label `COORDINATE`, not only `XYZ`, so the meaning is immediately obvious.
- Keep `X`, `Y`, and `Z` visibly attached to their own values so builders cannot confuse axis order.
- Default inline order is always `X`, then `Y`, then `Z`.
- `X` is east/west position, `Y` is vertical/elevation position, and `Z` is north/south position; this axis meaning is the canonical interpretation behind the displayed values.
- The coordinate value uses integer block coordinates for builder readability.
- This block sits at the highest top-left priority position before the `CLIENT` section.
- Give it slightly stronger visual emphasis than normal rows while remaining Minecraft-native and compact.
- Do not turn it into a large card, banner, badge, or center-screen element.
- Coordinate remains the only interactive datum in the default Compact Debug surface.

### Left — Client

Show only:

```text
CLIENT
FPS
CPU
GPU
RAM
```

Rules:

- FPS is the current useful player-facing frame-rate value, not a graph or frame-time diagnostic panel.
- CPU is client/machine CPU usage expressed as a simple percentage.
- GPU is GPU utilization expressed as a simple percentage when a supported source is available.
- RAM is Minecraft/JVM memory usage shown as used / available-to-Minecraft, not a list of heap/allocation/internal buffer values.
- Do not show CPU model, GPU model, driver, OpenGL version, allocation rate, render queues, chunk builder internals, buffer pools, mod renderer state, or performance-engine internals.

### Left — World context

Show only:

```text
WORLD
Facing
Biome
Time
```

Rules:

- Facing shows the player's current horizontal cardinal direction in a builder-readable form such as `North (-Z)`, `South (+Z)`, `West (-X)`, or `East (+X)`. Do not show yaw/pitch numbers, facing vectors, or other orientation internals in the default surface.
- Biome is the biome at the player's current position. Prefer a readable display name; fall back to the registry identifier only when a readable name is unavailable.
- Time is current in-game world time formatted for human reading. Do not expose raw tick counts in the default presentation.
- Do not duplicate coordinate inside the `WORLD` section.
- Do not show chunk coordinates, region file, velocity, light values, targeted block tags, entity details, packets, or world-generation internals.

### Right — Server

Show only:

```text
SERVER
World
CPU
RAM
```

Rules:

- World is the current server world/dimension-facing world name intended for the builder, not an internal folder path or protocol id.
- CPU and RAM are server-provided telemetry. The client must not pretend vanilla protocol data is server machine utilization.
- Do not add TPS, MSPT, ping, player count, entity count, loaded chunks, packet counts, disk usage, thread counts, GC details, or plugin diagnostics to the default surface unless a later explicit requirement justifies them.

## Server telemetry states

Compact Debug must remain useful on servers that do not provide LazyBuilder telemetry.

When telemetry is available:

```text
SERVER
World      TanaSamawa
CPU        18%
RAM        3.7 / 8 GB
```

When world identity is available but machine telemetry is not:

```text
SERVER
World      world
Status     Metrics unavailable
```

Do not show fake zeroes, stale values from a previous server, or placeholder CPU/RAM values that look authoritative.

Disconnect or server switch invalidates all server telemetry immediately.

## Coordinate copy interaction

The dedicated Coordinate block is the only interactive datum in the default Compact Debug surface.

Normal state:

```text
COORDINATE
X -11   Y 71   Z -481
```

Hover/focus affordance:

```text
COORDINATE
X -11   Y 71   Z -481
Click to copy coordinates
```

Clicking anywhere on the Coordinate block copies the complete coordinate triplet in canonical XYZ order:

```text
-11 71 -481
```

The visible `X`, `Y`, and `Z` labels are presentation aids only and are intentionally omitted from the clipboard value. Default copy also excludes commas, dimension text, and `/tp` syntax so the result is easy to paste into chat, commands, notes, or external tools.

Do not make the individual X, Y, or Z values separate copy targets in V1. One click copies the complete location to avoid partial or mixed-axis clipboard states.

After a successful copy, use the existing Utility Manager native notification/toast path:

```text
Coordinates copied
```

Do not write a chat message for successful copy.

Important interaction must not rely on hover alone. If Minecraft's active debug HUD input path cannot provide reliable pointer interaction while F3 is open, implementation must provide an equally simple keyboard-accessible copy action without introducing a second permanent keybind. The UI owner must approve any such fallback before implementation.

## F3 behavior

F3 remains the single familiar entry point.

```text
F3 → Compact Debug on
F3 → Compact Debug off
```

Do not add a second default keybind for opening Compact Debug.

Existing F3 shortcut combinations that remain useful should not be broken merely to simplify rendering. One physical input must have one logical owner; Compact Debug must not double-dispatch with vanilla/BetterF3 handling.

## BetterF3 / competing debug owners

The final LazyBuilder client pack must not render Compact Debug and BetterF3 simultaneously.

Target ownership:

```text
LazyBuilder Utility Manager
→ owns the Compact Debug presentation

BetterF3
→ not required for this presentation once Compact Debug is active
```

Do not build compatibility logic that merges two debug overlays. If BetterF3 remains temporarily installed during development, the production pack must still converge to one F3 presentation owner before acceptance.

## Visual layout

Use Minecraft-native text rendering and spacing. The surface should feel like a cleaned-up Minecraft debug view, not a desktop dashboard placed over the game.

Layout rules:

```text
coordinate anchor small safe margin from top-left; first visible block
client anchor     directly below coordinate with a clear section gap
world anchor      below client with a clear section gap
right anchor      small safe margin from top-right
column width      content-driven, bounded
row spacing       consistent and compact
section gap       visibly larger than row spacing
label/value gap   consistent within each side
background        no large opaque cards
```

Coordinate emphasis:

```text
COORDINATE label  clear section label
X/Y/Z labels      compact axis identifiers attached to each value
coordinate value strongest single value on the left
copy hint         secondary and shown only when relevant
```

Visual hierarchy:

```text
coordinate value highest-priority left-side datum
section heading  strongest text weight/contrast available in native style
value            primary readable text
label            slightly quieter than value
hint/status      secondary text
```

Do not use decorative icons, charts, progress rings, colored gauges, animations, or large panels in V1.

Color must not be the only carrier of state. Normal readability must survive bright sky, snow, Nether, dark caves, and shader/no-shader scenes.

## Density and scale

The default surface should remain comfortably readable at common GUI scales without approaching the center crosshair or covering major gameplay space.

Required behavior:

```text
wide screen
→ coordinate remains first at top-left
→ X/Y/Z remain unambiguous at a glance
→ left and right stay at their respective edges

narrow screen
→ preserve coordinate priority and explicit X/Y/Z labels
→ preserve both groups without overlap
→ reduce non-essential spacing before reducing text readability

GUI scale change
→ recompute positions from current scaled window dimensions
→ no stale coordinates or duplicated render state
```

If the screen becomes too narrow to fit both sides safely, truthful information and coordinate priority take precedence over decorative symmetry. Do not introduce horizontal scrolling.

## Update cadence and performance

Compact Debug is a HUD, not a profiler.

Fast-changing presentation:

```text
coordinate / FPS / facing / biome / time
→ may consume current client state during normal render/update flow
```

Expensive machine metrics:

```text
CPU / GPU / RAM
→ bounded cached sampling
→ never query heavyweight native/system APIs every rendered frame
```

Server telemetry:

```text
→ event/payload driven or low-frequency bounded update
→ never request every frame
→ stale/expired data becomes unavailable rather than silently remaining current
```

The HUD must not introduce a background worker merely to keep text fresh when a bounded event/tick/sample mechanism already exists.

## Ownership and one-mod rule

Production client shape remains:

```text
LazyBuilder Utility Manager.jar
├── existing utility features
├── Instant Creative Search
└── Compact Debug
```

Do not create `F3 Manager`, `Debug Manager`, or a second client JAR.

Performance Manager remains isolated/deferred research source unless separately promoted. Useful metric-reading ideas may be reimplemented or consolidated into the Utility Manager under this explicit Compact Debug requirement, but Utility Manager must not become a general performance optimizer, frame scheduler, graphics tuner, or second performance architecture.

The distinction is:

```text
Compact Debug
→ observes and presents a tiny bounded metric set

Performance optimization engine
→ changes performance policy/graphics/runtime behavior
→ remains outside this feature
```

## Data truth rules

Every displayed value must have one authoritative source and an explicit unavailable state.

```text
Coordinate current player position, presented explicitly as X / Y / Z
FPS        client render/game state
CPU        bounded client machine metric source
GPU        bounded supported GPU metric source
RAM        JVM/client memory source
Facing     current player horizontal facing direction
Biome      current player world biome
Time       current player world time
World      canonical current server/world identity available to the client
Srv CPU    server telemetry only
Srv RAM    server telemetry only
```

Never infer server CPU/RAM from ping, TPS, packet timing, client CPU usage, or local machine metrics.

## Interaction states

The default HUD has only four meaningful presentation states:

```text
OFF
VISIBLE
COORDINATE_HOVER_OR_FOCUS
SERVER_METRICS_UNAVAILABLE
```

Do not create settings screens, tabs, collapsible cards, edit modes, drag-layout modes, or per-row customization in V1.

## Configuration

Keep configuration minimal. Recommended product surface:

```properties
hud.compact_debug=true
```

The feature is opened/closed by F3; the preference controls whether LazyBuilder owns the compact F3 presentation at all.

Do not expose individual toggles for Coordinate, FPS, CPU, GPU, RAM, facing, biome, time, world, server CPU, or server RAM in V1. The contract is intentionally opinionated so the F3 surface stays simple.

If a metric is unsupported or unavailable, show the defined unavailable state rather than requiring a user preference to hide it.

## Acceptance contract

A future implementation is accepted only when all of the following are true:

```text
F3 opens one compact LazyBuilder debug presentation
F3 closes it predictably
Coordinate is the first and most prominent top-left datum
Coordinate is labeled explicitly as COORDINATE
X, Y, and Z are individually labeled beside their respective values
axis order is always X → Y → Z
coordinate interaction copies the complete integer triplet in XYZ order
clipboard output is the raw triplet without X/Y/Z labels
Coordinate is visually separated from CLIENT and WORLD
left side contains only approved client/world rows, including Facing
right side contains only approved server rows
copy confirmation uses existing Utility notification presentation
unsupported server metrics are truthful and uncluttered
server switch/disconnect cannot leak stale telemetry
common GUI scales do not overlap or duplicate rows
no competing BetterF3 + LazyBuilder overlay remains in the production pack
machine metrics do not perform heavyweight work every frame
one Utility Manager JAR remains the client owner
```

## Proof boundary

Remote/source proof can establish layout logic, data ownership, mixin/event wiring, telemetry state handling, and deterministic tests.

Do not claim final visual or interaction acceptance until real Minecraft rendering/input is exercised. Local/native proof is intentionally deferred until the maintainer requests the testing phase.