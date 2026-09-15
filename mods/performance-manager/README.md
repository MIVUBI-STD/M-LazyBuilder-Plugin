# LazyBuilder Performance Manager

> **Status: deferred / experimental for V1.** Performance Manager remains in the repository as isolated research source. It is not a product requirement unless measured client evidence justifies promotion.
>
> The current `Local` Launcher/client-packaging path may still reference or bundle this module while the active Launcher/installer consolidation is in progress. Treat that as transitional implementation state, not permission to expand Performance Manager or make other systems depend on it. Do not remove or rewire those active Launcher paths in parallel unless that work is explicitly coordinated.

## Goal

Keep a bounded first-party performance experiment available for Minecraft Java 1.21.4 without turning performance tuning into a second product architecture.

The module may be used to measure specific client bottlenecks. It must not become a hidden runtime dependency, graphics-quality controller, general optimization framework, or reason to duplicate behavior already owned by Minecraft, Fabric, Modrinth, or another LazyBuilder manager.

## Current research scope

The existing source provides:

- allocation-free rolling frame timing over a bounded 60-frame window;
- target-aware frame-pressure states: `NORMAL`, `ELEVATED`, and `HEAVY`;
- render-gap protection so world loading, disconnects, and other render discontinuities are not counted as frame spikes;
- first-party unfocused/minimized FPS policy;
- on-demand performance snapshots, including Minecraft chunk/entity/particle debug state;
- no permanent HUD, metrics history database, background worker, graphics auto-tuning, or speculative workload scheduler.

No additional capability should be added until a reproducible client-side performance problem demonstrates that this module is the correct owner.

## Runtime model

```text
PerformanceManagerClient
└── PerformanceRuntime
    ├── FrameMonitor
    ├── BackgroundResourcePolicy
    └── PerformanceSnapshotReader
```

One world-render callback records focused world frame timing. One end-client-tick hook updates the background FPS policy. No dedicated thread or polling worker is created.

## Frame pressure

Frame pressure is diagnostic state. It is intentionally not a graphics-quality controller and does not own scheduling in other LazyBuilder managers.

Thresholds are derived from the user's configured foreground FPS target with conservative absolute floors. This avoids treating an intentional 30 FPS target as a performance fault while still detecting sustained slow frames and severe spikes.

When world rendering stops, the window loses focus, or the client is minimized, the timing clock is reset. Long render gaps are treated as discontinuities instead of fake lag spikes.

## Background resource policy

Existing experimental defaults:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

The policy changes only Minecraft's temporary inactivity FPS limiter. It does not rewrite the user's configured video-option FPS limit. When focus returns, the current user limit is authoritative again.

This behavior must remain isolated inside Performance Manager while the module is deferred. Other LazyBuilder components must not depend on it.

## Diagnostics

`PerformanceManagerClient.currentSnapshot()` captures diagnostics on demand:

```text
FPS
Current frame time
Rolling average frame time
Worst recent frame time
JVM used / max memory
Render distance
Simulation distance
Window focused / minimized state
Current frame pressure
Completed chunk count
Minecraft chunk debug string
Minecraft entity render debug string
Minecraft particle debug string
```

Memory, option, entity, chunk, and particle diagnostics are not sampled continuously.

## Ownership rule

Performance Manager is currently an isolated research owner, not a shared infrastructure layer.

Do not:

- introduce dependencies from Map Manager, Utility Manager, Paper plugins, or shared protocol into Performance Manager;
- introduce dependencies from Performance Manager into unrelated LazyBuilder managers;
- add a second performance/config authority in the Launcher or Paper runtime;
- add renderer replacement, culling engines, shader systems, background schedulers, compatibility matrices, or broad optimization frameworks without measured evidence;
- promote this module to required V1 runtime solely because the source already exists.

Promotion requires a concrete bottleneck, a success metric, representative Minecraft-client proof, and an explicit product decision.

## Configuration

Only the existing bounded experimental decisions are represented:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

Frame-pressure thresholds remain internal. Do not add more knobs unless profiling proves that a real user decision is required.
