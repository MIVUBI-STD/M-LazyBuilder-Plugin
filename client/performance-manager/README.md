# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the Fabric client performance coordination layer for Minecraft Java 1.21.4.

## Boundary

Performance Manager owns performance status, lightweight resource policy, and awareness of optional optimizer capabilities. It does not replace renderer, shader, culling, memory, or immediate-render optimization engines.

External specialist foundations remain external, including Sodium, Iris, ImmediatelyFast, FerriteCore, EntityCulling, and MoreCulling.

## Product rules

- one Manager = one Fabric mod = one output JAR;
- no dependency on Map Manager or Utility Manager implementation packages;
- no renderer/shader/culling algorithm is copied into LazyBuilder;
- no graphics-quality setting is silently changed;
- no default keybind is required;
- capability detection is passive;
- performance metrics are captured on demand and no history database is maintained;
- native background FPS policy must automatically stand down when Dynamic FPS is installed.

## Implemented C3 layers

The current implementation provides three layers:

1. `PerformanceManagerClient.capabilities()` — immutable optional-mod capability snapshot;
2. `PerformanceManagerClient.currentState()` — lightweight on-demand performance snapshot;
3. guarded native background FPS policy for clients without Dynamic FPS.

Detected capabilities currently include:

```text
Sodium
Iris
ImmediatelyFast
FerriteCore
EntityCulling
MoreCulling
Sodium Extra
Reese's Sodium Options
Dynamic FPS
```

Detection uses Fabric Loader mod presence only. Performance Manager does not import those mods' implementation packages and does not require them to be installed.

The current state snapshot observes:

```text
FPS
Approximate frame time derived from FPS
JVM used / max memory
Render distance
Simulation distance
Window focused state
Window minimized state
Detected optimizer capabilities
```

State capture is read-only and only runs when requested. No performance-history sampler, renderer hook, or permanent HUD is registered.

Exact Iris shader-active detection is intentionally not guessed through fragile reflection. Iris presence is exposed as a capability first; active-shader integration may be added only if a stable public boundary is available.

## Background FPS policy

When `dynamic_fps` is installed, LazyBuilder does not apply its native background limiter.

When Dynamic FPS is absent, the default policy is:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

The policy changes only Minecraft's temporary window framerate limit. It does not rewrite the user's configured video-option FPS limit. When the window becomes focused again, the current user FPS limit becomes authoritative again.

The controller uses one lightweight end-client-tick hook because focus/minimize state can change at runtime. It performs no sampling, persistence, renderer work, or external-mod calls on that tick.

## Deferred

The following remain out of the active product surface until independently justified:

- performance HUD or permanent overlay;
- performance profiles;
- automatic graphics-quality changes;
- Sodium/Iris settings cloning;
- renderer hooks;
- memory optimization;
- chunk/render optimization engines;
- replacement implementations for specialist optimization mods.

The next review should decide whether any additional coordination is actually needed beyond capability awareness, state inspection, and guarded background resource policy before introducing profiles or settings surfaces.
