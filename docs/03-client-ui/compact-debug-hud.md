# Compact Debug HUD

## Purpose

Compact Debug is the builder-facing replacement presentation for the normal F3 information overload. It keeps F3 as the familiar entry point, shows only information that is useful during daily building, and remains part of the existing LazyBuilder Utility Manager client mod rather than creating another Fabric mod or another debug ecosystem.

The design goal is immediate comprehension:

```text
F3
→ two small information groups
→ client + current Minecraft context on the left
→ server condition on the right
→ no diagnostic wall
```

This document owns presentation and interaction semantics. Fabric implementation mechanics stay under the existing Utility Manager module and must follow the Fabric Mod Development skill after this UI contract is frozen.

## Product contract

Compact Debug is deliberately narrow:

```text
LEFT                                RIGHT
CLIENT                              SERVER
FPS        143                      World      TanaSamawa
CPU        28%                      CPU        18%
GPU        14%                      RAM        3.7 / 8 GB
RAM        664 / 6144 MB

WORLD
XYZ        -11 71 -481
Biome      Taiga
Time       08:34
```

The left side answers two questions:

```text
How is my client running?
Where am I in Minecraft?
```

The right side answers:

```text
Which world am I on?
How is the server machine doing?
```

Anything that does not help answer those questions stays out of the default F3 surface.

## Information hierarchy

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
XYZ
Biome
Time
```

Rules:

- XYZ uses integer block coordinates for builder readability.
- Biome is the biome at the player's current position. Prefer a readable display name; fall back to the registry identifier only when a readable name is unavailable.
- Time is current in-game world time formatted for human reading. Do not expose raw tick counts in the default presentation.
- Do not show chunk coordinates, region file, facing vector, velocity, light values, targeted block tags, entity details, packets, or world-generation internals.

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

The XYZ row is the only interactive row in the default Compact Debug surface.

Normal state:

```text
XYZ        -11 71 -481
```

Hover/focus affordance:

```text
XYZ        -11 71 -481
           Click to copy coordinates
```

Click copies exactly:

```text
-11 71 -481
```

Default copy deliberately excludes commas, labels, dimension text, and `/tp` syntax so the value is easy to paste into chat, commands, notes, or external tools.

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
left anchor      small safe margin from top-left
right anchor     small safe margin from top-right
column width     content-driven, bounded
row spacing      consistent and compact
section gap      visibly larger than row spacing
label/value gap  consistent within each side
background       no large opaque cards
```

Visual hierarchy:

```text
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
→ left and right stay at their respective edges

narrow screen
→ preserve both groups without overlap
→ reduce non-essential spacing before reducing text readability

GUI scale change
→ recompute positions from current scaled window dimensions
→ no stale coordinates or duplicated render state
```

If the screen becomes too narrow to fit both sides safely, truthful information takes priority over decorative symmetry. Do not introduce horizontal scrolling.

## Update cadence and performance

Compact Debug is a HUD, not a profiler.

Fast-changing presentation:

```text
FPS / XYZ / biome / time
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
FPS       client render/game state
CPU       bounded client machine metric source
GPU       bounded supported GPU metric source
RAM       JVM/client memory source
XYZ       current player position
Biome     current player world biome
Time      current player world time
World     canonical current server/world identity available to the client
Srv CPU   server telemetry only
Srv RAM   server telemetry only
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

Do not expose individual toggles for FPS, CPU, GPU, RAM, XYZ, biome, time, world, server CPU, or server RAM in V1. The contract is intentionally opinionated so the F3 surface stays simple.

If a metric is unsupported or unavailable, show the defined unavailable state rather than requiring a user preference to hide it.

## Acceptance contract

A future implementation is accepted only when all of the following are true:

```text
F3 opens one compact LazyBuilder debug presentation
F3 closes it predictably
left side contains only approved client/world rows
right side contains only approved server rows
XYZ copies the exact integer coordinate triplet
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