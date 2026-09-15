# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the first-party Fabric client performance runtime for Minecraft Java 1.21.4.

## Goal

Keep the Minecraft client smooth, stable, and responsive without lowering visual quality by default. LazyBuilder owns the performance behavior it requires; third-party optimization mods are not mandatory runtime owners.

## Current scope

The current runtime provides:

- allocation-free rolling frame timing over a bounded 60-frame window;
- target-aware frame-pressure states: `NORMAL`, `ELEVATED`, and `HEAVY`;
- render-gap protection so world loading, disconnects, and other render discontinuities are not counted as frame spikes;
- first-party unfocused/minimized FPS policy;
- on-demand performance snapshots, including Minecraft chunk/entity/particle debug state;
- no permanent HUD, metrics history database, background worker, graphics auto-tuning, or speculative workload scheduler.

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

Frame pressure is diagnostic state. It is intentionally not a graphics-quality controller and currently does not own scheduling in other LazyBuilder managers.

Thresholds are derived from the user's configured foreground FPS target with conservative absolute floors. This avoids treating an intentional 30 FPS target as a performance fault while still detecting sustained slow frames and severe spikes.

When world rendering stops, the window loses focus, or the client is minimized, the timing clock is reset. Long render gaps are treated as discontinuities instead of fake lag spikes.

## Background resource policy

Default policy:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

The policy changes only Minecraft's temporary inactivity FPS limiter. It does not rewrite the user's configured video-option FPS limit. When focus returns, the current user limit is authoritative again.

This behavior is owned by LazyBuilder. It does not yield ownership to Dynamic FPS or another optional provider.

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

Performance capabilities required by LazyBuilder must have first-party implementations maintained and versioned with LazyBuilder. External projects may inform problem analysis, but LazyBuilder must not require them for its core performance behavior.

Generic renderer replacement, culling engines, shader systems, and other broad optimization engines are not added merely because they exist elsewhere. They require runtime evidence of a specific bottleneck and a bounded first-party scope before implementation.

## Configuration

Only real user decisions are configurable:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

Frame-pressure thresholds remain internal until runtime profiling proves a user-facing setting is necessary.
