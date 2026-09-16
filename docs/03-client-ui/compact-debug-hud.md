# Compact Debug HUD

## Purpose

Compact Debug is the builder-facing replacement presentation for the normal F3 information wall. It keeps F3 as the familiar entry point, shows only daily-use information, and remains part of the existing LazyBuilder Utility Manager client mod.

```text
F3
→ Coordinate first at the top-left
→ Client + Minecraft context below it
→ Server condition at the top-right
→ no diagnostic wall
```

This document owns presentation and interaction semantics. Fabric implementation stays in `mods/utility-manager/**`.

## Product contract

```text
COORDINATE                         SERVER
X -11   Y 71   Z -481             World      TanaSamawa
F3+C  Copy                        CPU        18%
                                  RAM        3.7 / 8 GB
CLIENT
FPS        143
CPU        28%
GPU        14%
RAM        664 / 6144 MiB

WORLD
Facing     North (-Z)
Biome      Taiga
Time       08:34
```

Coordinate is deliberately separate from `WORLD` and is the first visible datum.

## Information hierarchy

### Coordinate — highest priority

Show:

```text
COORDINATE
X -11   Y 71   Z -481
F3+C  Copy
```

Rules:

- use the explicit label `COORDINATE`;
- always show axis labels beside their own values;
- order is always `X → Y → Z`;
- `X` = east/west, `Y` = elevation, `Z` = north/south;
- display integer block coordinates;
- keep this as the strongest left-side datum without making it a large banner.

### Client

Show only:

```text
CLIENT
FPS
CPU
GPU
RAM
```

Rules:

- FPS is current frame rate;
- CPU is bounded Minecraft/client process CPU usage;
- GPU is utilization only when a truthful supported source exists;
- RAM is JVM used / maximum memory;
- unsupported metrics show `Unavailable` rather than estimates;
- do not show hardware models, drivers, OpenGL details, frame graphs, allocation rate, render queues, or profiler internals.

### World

Show only:

```text
WORLD
Facing
Biome
Time
```

Facing examples:

```text
North (-Z)
South (+Z)
West (-X)
East (+X)
```

Do not show yaw/pitch, vectors, chunk/region data, velocity, light, targeted-block details, entity counts, or packets.

### Server

Show only:

```text
SERVER
World
CPU
RAM
```

CPU and RAM are server-provided telemetry only. The client must never infer server machine usage from ping, TPS, packets, or local CPU.

When telemetry is unavailable:

```text
SERVER
World      Overworld
Status     Metrics unavailable
```

Server telemetry is invalidated immediately on join/disconnect/server switch.

## Coordinate copy

The visible axis labels are presentation aids. Clipboard output stays clean:

```text
-11 71 -481
```

V1 uses the existing Minecraft debug chord:

```text
F3 + C
→ copy complete XYZ triplet
→ Utility toast: Coordinates copied
```

This deliberately avoids a second permanent keybind. It also avoids pretending the normal gameplay HUD has a reliable clickable pointer while Minecraft keeps the cursor captured.

If a future native cursor-enabled debug interaction is introduced, the whole Coordinate block may become clickable, but X/Y/Z must never become three independent copy targets.

## F3 behavior

```text
F3 → Compact Debug on
F3 → Compact Debug off
```

Do not create another debug-screen keybind or a parallel debug UI.

Useful Vanilla F3 combinations should remain untouched except the explicitly owned `F3+C` coordinate-copy behavior while Compact Debug is enabled.

## Visual treatment

Use a semi-custom Minecraft-native HUD, not a desktop dashboard.

Approved treatment:

```text
Coordinate
→ compact translucent background
→ slightly stronger visual priority

Client / World / Server
→ restrained translucent background
→ native Minecraft font
→ consistent label/value alignment
→ small section gaps
```

Rules:

- no large opaque cards;
- no decorative icons;
- no charts, progress rings, gauges, animation, tabs, or draggable layout;
- labels are quieter than values;
- headings are clear but compact;
- coordinate value is the strongest single left-side value;
- readability must survive bright sky, snow, Nether, caves, shaders, and no-shader scenes.

## Layout

```text
coordinate anchor  small safe margin from top-left
client anchor      immediately below Coordinate
world anchor       below Client
server anchor      small safe margin from top-right
column width       content-driven and bounded
row spacing        compact and consistent
section gap        larger than row spacing
```

At narrow widths, reduce non-essential spacing before compromising text readability. Coordinate priority is preserved. No horizontal scrolling.

GUI-scale changes recompute placement from the current scaled window size.

## Performance

Compact Debug is a HUD, not a profiler.

```text
Fast state
FPS / coordinate / facing / biome / time
→ current client state

Machine state
CPU
→ bounded cached sample

RAM
→ lightweight JVM runtime value

GPU
→ supported truthful source or Unavailable

Server metrics
→ event/payload-driven or low-frequency bounded update
→ never every frame
```

Do not introduce a background worker solely for this HUD.

## Ownership

```text
LazyBuilder Utility Manager.jar
├── existing utility features
├── Instant Creative Search
└── Compact Debug
```

Do not create `F3 Manager`, `Debug Manager`, or another client JAR.

Performance Manager may remain isolated research source, but Compact Debug must not depend on it at runtime or become a performance optimizer.

## Configuration

```properties
hud.compact_debug=true
```

No individual row toggles in V1.

## Acceptance contract

```text
F3 renders one compact LazyBuilder presentation
Coordinate is first and most prominent at top-left
X/Y/Z are explicit and cannot be confused
F3+C copies raw integer XYZ in X Y Z order
copy confirmation uses existing Utility toast
Client shows only FPS/CPU/GPU/RAM
World shows only Facing/Biome/Time
Server shows only World/CPU/RAM or truthful unavailable state
server state cannot leak across sessions
common GUI scales do not overlap
machine metrics do not perform heavyweight work each frame
BetterF3 and Compact Debug are not rendered together in the final pack
one Utility Manager JAR remains the client owner
```

## Proof boundary

Remote/source proof can establish layout logic, state ownership, mixin/input wiring, telemetry invalidation, and deterministic tests.

Final visual/input acceptance still requires real Minecraft rendering and local/native interaction testing. That testing remains deferred until the maintainer requests it.
