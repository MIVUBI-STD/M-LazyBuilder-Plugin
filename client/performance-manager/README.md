# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the Fabric client performance coordination layer for Minecraft Java 1.21.4.

## Boundary

Performance Manager owns performance status, resource policy, and awareness of optional optimizer capabilities. It does not replace renderer, shader, culling, memory, or immediate-render optimization engines.

External specialist foundations remain external, including Sodium, Iris, ImmediatelyFast, FerriteCore, EntityCulling, and MoreCulling.

## Product rules

- one Manager = one Fabric mod = one output JAR;
- no dependency on Map Manager or Utility Manager implementation packages;
- no renderer/shader/culling algorithm is copied into LazyBuilder;
- no graphics setting is silently changed during capability detection;
- no default keybind is required;
- capability detection is passive and event-free.

## C3 baseline

The current implementation only detects optional capabilities present in the Fabric environment and exposes one immutable snapshot through `PerformanceManagerClient.capabilities()`.

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

## Deferred until after baseline review

The following are intentionally not implemented yet:

- background/unfocused/minimized FPS policy;
- performance HUD or permanent overlay;
- performance profiles;
- automatic graphics-quality changes;
- Sodium/Iris settings cloning;
- renderer hooks;
- memory optimization;
- chunk/render optimization engines.

The next implementation step should add a small native performance-state model (FPS/frame time/memory and client focus state) before any active resource policy is introduced.
