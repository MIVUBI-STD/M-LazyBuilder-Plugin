# Performance Manager Audit Lock

## Purpose

This document defines the current first-party ownership boundary for LazyBuilder Performance Manager.

The primary goal is to keep the Minecraft client smooth, stable, and responsive while preserving user-selected visual quality. Performance work should remove unnecessary client work before considering quality reduction.

## Ownership lock

```text
Performance Manager
├── frame stability + pressure signals
├── first-party background/minimized FPS policy
├── conservative culling + pressure-aware pacing
├── chunk/render/buffer efficiency
├── targeted memory reduction
├── terrain residency + guarded physical arena ownership
├── compatibility-gated terrain submission / multi-draw
└── on-demand diagnostics + opt-in runtime proof
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

## Current deeper optimization boundary

The current source now implements measured first-party work for chunk rebuild/upload pacing, conservative visibility/culling, allocation hot-path reduction, buffer reuse, terrain residency/reclamation, physical shared arenas, transform streaming, and guarded multi-draw submission.

These paths remain acceptance-gated:

- external renderer ownership or compatibility uncertainty must disable overlapping first-party hooks;
- exclusive arena ownership must remain recoverable to vanilla backing before fallback/disable;
- disabling rendering optimizations must release deferred work and terrain residency/arena resources;
- custom/sorted-index and unsupported shader states remain conservative fallbacks;
- further renderer ownership expansion requires representative runtime evidence, not feature-count pressure.

The implementation may evolve, but a second renderer, scheduler, residency registry, or generic performance framework is not justified while these owners remain sufficient.

## Capability ownership and remote verification

Compatibility decisions are now made per optimization domain instead of through a growing list of
mixin-specific mod checks. Current domains are terrain build, terrain upload, terrain submission, immediate rendering, text
rendering, entity culling, and model-memory ownership.

This preserves conservative fail-closed behavior for uncertain renderer ownership while keeping
unrelated first-party optimizations active. For example, Iris blocks shader-sensitive terrain
submission without disabling first-party chunk build policy, ImmediatelyFast owns only overlapping
immediate/text/upload work, EntityCulling owns only entity/block-entity culling, and
FerriteCore owns only model-memory deduplication.

Vertex-buffer responsibilities are split deliberately. Terrain residency accounting and reversible
vanilla-backing recovery remain part of first-party terrain ownership, while writable GPU growth is
an optional overlapping optimization that may stand down independently. External immediate-render
ownership must never disable terrain recovery safety.

Terrain clear and disable lifecycle is recovery-first. Exclusive arena residents must recover their
vanilla backing before physical arenas and logical residency state are destroyed. If any recovery
fails, clear is aborted, authoritative arena state is retained for retry/fallback, and the failure is
surfaced through lifecycle diagnostics instead of being silently discarded. Off-render-thread clear
requests reschedule the complete transaction onto the render thread.

Direct development on `Local` is now covered by the dedicated Performance Manager workflow. Changes
under `mods/performance-manager/**` or the workflow itself trigger the same build-and-test lane that
previously required a pull request or manual dispatch.

Diagnostic consumers should prefer the stable grouped views exposed by `PerformanceSnapshot`
(`frameStats`, `resourceStats`, `chunkStats`, and `compatibilityStats`) instead of coupling new
code to the complete flat diagnostic schema.

Pipeline telemetry is disabled by default and becomes active only when proof mode is enabled, the
explicit metrics system property is set, or an on-demand diagnostics snapshot is requested. This
keeps observability useful without paying permanent LongAdder traffic on meshing/render hot paths.

Entity/block-entity culling is opt-in by default because its raycast cost must be proven on the
representative builder workload before becoming a default optimization. When disabled, runtime tick
work and block-entity renderer lookup are bypassed. When an external EntityCulling owner is present,
LazyBuilder's corresponding render mixins do not apply. Culling ray evaluation uses lazy sample
construction and primitive direction math to reduce short-lived allocation churn.

Terrain runtime state is scoped to the active `ChunkBuilder` session. A new renderer claims a new
session generation; reset rotates that generation, stop clears only if the caller still owns the
active session, and queued upload work carries the owner+generation token it was created under.
Stale upload work is discarded before any GL bind or residency mutation. World identity changes also
reset frame-pressure history and culling state so one world cannot bias scheduling decisions in the
next.

Physical terrain arenas use session-scoped failure containment. Structural GL/provisioning failures
are isolated to the affected resident whenever possible; repeated failures open a small circuit
breaker that disables only the physical-arena fast path for that session. Vanilla terrain rendering
remains authoritative fallback, and the breaker resets with safe terrain-session cleanup.

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

Runtime smoothness and actual Minecraft behavior still require LOCAL_CODE/gameplay validation beyond repository source proof. Representative proof must now include frame-time percentiles/stutter counts, adaptive-governor decisions, low-rate culling profitability, non-blocking GPU timing for terrain/shadow/post-process when timer queries are available, multi-draw profitability cooldown state, GPU capability tier, shader runtime degradation mode, and recovery/error counters. These measurements are evidence; they must not become always-on background telemetry.
