# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the Fabric client performance coordination layer for Minecraft Java 1.21.4.

## Boundary

Performance Manager owns performance status, resource policy, and awareness of optional optimizer capabilities. It does not replace renderer, shader, culling, memory, or immediate-render optimization engines.

External specialist foundations remain external, including Sodium, Iris, ImmediatelyFast, FerriteCore, EntityCulling, and MoreCulling.

## Product rules

- one Manager = one Fabric mod = one output JAR;
- no dependency on Map Manager or Utility Manager implementation packages;
- no renderer/shader/culling algorithm is copied into LazyBuilder;
- no graphics setting is silently changed during detection or state capture;
- no default keybind is required;
- capability detection is passive;
- performance state is captured on demand instead of through a permanent polling loop.

## C3 baseline

The current implementation provides two passive layers:

1. `PerformanceManagerClient.capabilities()` — immutable optional-mod capability snapshot;
2. `PerformanceManagerClient.currentState()` — lightweight on-demand client performance snapshot.

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

State capture is read-only and only runs when requested. No background sampler, tick hook, renderer hook, or permanent HUD is registered.

Exact Iris shader-active detection is intentionally not guessed through fragile reflection. Iris presence is exposed as a capability first; an active-shader integration may be added only if a stable public integration boundary is available.

## Deferred until after state-model review

The following are intentionally not implemented yet:

- background/unfocused/minimized FPS policy;
- performance HUD or permanent overlay;
- performance profiles;
- automatic graphics-quality changes;
- Sodium/Iris settings cloning;
- renderer hooks;
- memory optimization;
- chunk/render optimization engines.

The next architecture decision is whether native background FPS control adds enough value when Dynamic FPS is absent, while remaining completely disabled when an external background-FPS provider is present.
