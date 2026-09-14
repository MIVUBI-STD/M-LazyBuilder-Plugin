# Performance Manager Audit Lock

## Purpose

This document defines the current first-party ownership boundary for LazyBuilder Performance Manager.

The primary goal is to keep the Minecraft client smooth, stable, and responsive while preserving user-selected visual quality. Performance work should remove unnecessary client work before considering quality reduction.

## Ownership lock

```text
Performance Manager
├── frame stability signals
├── LazyBuilder workload pressure policy
├── first-party background/minimized FPS policy
├── on-demand diagnostics
└── future measured client optimizations
```

LazyBuilder owns the performance capabilities it requires. Core behavior must not require a third-party optimization mod to be installed, updated, or maintained.

External projects may be studied as problem references, but whole external optimizer stacks must not be copied or made mandatory merely to increase feature count.

## P0 implementation

The current P0 baseline owns:

- bounded rolling frame timing;
- `NORMAL`, `ELEVATED`, and `HEAVY` internal pressure states;
- workload admission for LazyBuilder-owned `CRITICAL`, `NORMAL`, and `DEFERRED` work;
- unfocused and minimized FPS limits;
- on-demand FPS, frame-time, JVM-memory, render-distance, simulation-distance, and window-state snapshots.

Runtime constraints:

- no dedicated background thread;
- no permanent performance HUD;
- no FPS/frame-time history database;
- no periodic JVM memory sampler;
- no automatic graphics-quality changes;
- no manual GC loop;
- no external optimizer capability registry in the core runtime.

## Frame-pressure policy

The frame monitor uses a bounded 60-frame primitive buffer. Thresholds are internal implementation policy and are not user configuration in P0.

Pressure rises quickly under sustained bad/severe frames and falls more slowly after stable frames to avoid rapid oscillation.

```text
NORMAL
→ critical + normal + deferred LazyBuilder work

ELEVATED
→ critical + normal LazyBuilder work

HEAVY
→ critical LazyBuilder work only
```

This policy applies only to LazyBuilder-owned noncritical work. It must not suppress Minecraft correctness, input, network processing, or authoritative session state.

## Background FPS ownership

LazyBuilder directly owns the narrow unfocused/minimized FPS behavior:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

The policy changes Minecraft's temporary inactivity FPS limiter and does not rewrite the configured user FPS option. The current user limit becomes authoritative again when the window is focused.

The previous Dynamic FPS handoff rule is retired. Performance Manager no longer disables its required first-party behavior because another optional performance mod is installed.

## Quality rule

Default performance optimization must not silently reduce:

- render distance;
- simulation distance;
- graphics quality;
- particles;
- shaders;
- texture/resource quality.

The preferred order is:

```text
remove duplicate work
→ reduce redundant updates
→ bound/cache work safely
→ schedule LazyBuilder noncritical work around frame pressure
→ only then review deeper renderer/chunk optimizations
```

## Deferred optimization layers

The following are valid future investigation areas but are not P0 acceptance requirements:

- redundant render/update suppression;
- chunk rebuild coalescing;
- burst workload scheduling;
- allocation hot-path reduction;
- bounded cache lifecycle improvements;
- entity visibility/culling;
- block-face culling;
- immediate-render optimization;
- renderer batching.

Each future optimization requires measurable client benefit, bounded runtime cost, maintainable ownership, and an explicit regression-risk review.

## Rejected by default

Do not add merely to make Performance Manager appear feature-rich:

- permanent FPS HUD;
- long-term metrics database;
- aggressive RAM-cleaner behavior;
- periodic `System.gc()` calls;
- dozens of performance profiles;
- silent graphics downgrades;
- hardware telemetry polling loops;
- mandatory third-party performance dependencies.

## P0 exit criteria

P0 is complete only when:

1. required background resource behavior is first-party;
2. no external optimizer owns a required P0 capability;
3. frame monitoring remains bounded and allocation-free on its hot path;
4. workload pressure only gates LazyBuilder-owned noncritical work;
5. diagnostics that are not frame-critical remain on-demand;
6. no dedicated worker thread or permanent HUD is introduced;
7. focused tests cover frame-pressure and workload-budget policy;
8. the Performance Manager still builds as one Fabric mod and one output JAR.

Runtime smoothness and actual Minecraft behavior still require LOCAL_CODE/gameplay validation beyond repository source proof.
