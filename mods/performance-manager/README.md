# LazyBuilder Performance Manager

LazyBuilder Performance Manager is the first-party Fabric client performance runtime for Minecraft Java 1.21.4.

## Goal

Keep the Minecraft client smooth, stable, and responsive without lowering visual quality by default. LazyBuilder owns the performance behavior it requires; third-party optimization mods are not mandatory runtime owners.

## P0 scope

The current baseline provides:

- allocation-free rolling frame timing over a bounded 60-frame window;
- internal frame-pressure states: `NORMAL`, `ELEVATED`, and `HEAVY`;
- a workload budget for LazyBuilder-owned work classes (`CRITICAL`, `NORMAL`, `DEFERRED`);
- first-party unfocused/minimized FPS policy;
- on-demand performance snapshots;
- no permanent HUD, metrics history database, background worker, or graphics auto-tuning.

## Runtime model

```text
PerformanceManagerClient
└── PerformanceRuntime
    ├── FrameMonitor
    ├── WorkloadBudget
    ├── BackgroundResourcePolicy
    └── PerformanceSnapshotReader
```

One client render callback records frame timing. One end-client-tick hook updates the workload budget and background FPS policy. No dedicated thread or polling worker is created.

## Frame pressure

Frame pressure is an internal scheduling signal, not a graphics-quality controller.

```text
NORMAL
→ CRITICAL + NORMAL + DEFERRED LazyBuilder work allowed

ELEVATED
→ CRITICAL + NORMAL allowed

HEAVY
→ CRITICAL only
```

Critical correctness work such as input, network handling, and authoritative session state must never be suppressed merely to improve FPS.

## Background resource policy

Default policy:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

The policy changes only Minecraft's temporary inactivity FPS limiter. It does not rewrite the user's configured video-option FPS limit. When focus returns, the current user limit is authoritative again.

This behavior is owned by LazyBuilder. It no longer yields ownership to Dynamic FPS or another optional provider.

## Diagnostics

`PerformanceManagerClient.currentSnapshot()` captures diagnostics on demand:

```text
FPS
Approximate current frame time
Rolling average frame time
Worst recent frame time
JVM used / max memory
Render distance
Simulation distance
Window focused / minimized state
Current frame pressure
```

Memory and option reads are not sampled continuously.

## Ownership rule

Performance capabilities required by LazyBuilder must have first-party implementations maintained and versioned with LazyBuilder. External projects may inform problem analysis, but LazyBuilder must not require them for its core performance behavior.

Deeper rendering, chunk, culling, memory, and batching optimizations are intentionally outside P0. They require separate measurable-benefit and regression-risk review before implementation.

## Configuration

Only real user decisions are configurable:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

Frame-pressure thresholds remain internal until runtime profiling proves a user-facing setting is necessary.
