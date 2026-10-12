# Performance Manager — Long-session and foreground-state audit

**Date:** 2026-10-13  
**Branch:** `Local`  
**Scope:** non-building Minecraft Java 1.21.4 performance: stable FPS control, long-session telemetry/governor counters, background/minimized transitions, resource-pressure ownership, and model-memory cache lifecycle.

**Out of scope:** World Manager, Build Helpers, Minecraft building/editing, GPU arena allocator/physical multi-draw rewrites, shader runtime internals, new profiling HUDs and new background schedulers.

**Evidence state:** SOURCE_IMPLEMENTED / SOURCE_REVIEWED. **No same-revision Windows Fabric Gradle build, live FPS benchmark, modpack interoperability run or native GPU/VRAM proof.** This audit is intentionally a source-first checkpoint because local testing is deferred.

## Preserve the mature foundation

The repo already owns a bounded 60-frame monitor; a small `NORMAL → ELEVATED → HEAVY` pressure policy; a single `PerformanceGovernor` for optional first-party work; Minecraft-native background FPS caps; and on-demand snapshots/proof instrumentation. Renderer optimization is guarded by compatibility and a recovery-first lifecycle. Culling is opt-in by default.

These are not grounds to create a second adaptive-performance system, a new background process, auto graphics-quality downgrade or forced garbage-collection loop. **The priority is removing stale signals and unbounded retention, not inventing more knobs.**

### External architecture references

- [Dynamic FPS](https://github.com/juliand665/dynamic-fps): state transitions among focused, unfocused, hidden and idle. Useful principle: quickly restore interactive behavior on refocus, without changing persistent graphics settings. LazyBuilder only retains its narrow background/minimized FPS policy; battery/idle/volume controls are not approved merely because another mod offers them.
- [Sodium](https://github.com/CaffeineMC/sodium): frame-time smoothness and renderer compatibility. It is a reference, not an additional required dependency.
- [Lithium](https://github.com/CaffeineMC/lithium): conservative, correct-by-construction optimization and reversibility.
- [Caffeine](https://github.com/ben-manes/caffeine): admission and bounded-retention discipline. LazyBuilder must not add an external cache library just to solve one simple model-lookup bound.

## Changes in this source batch

### P1 — Stale frame pressure across background/focus boundaries

**Observed:** `PerformanceRuntime.recordFrame` paused only the timestamp clock when unfocused/minimized/world-unavailable. The governor still ran in `tick`, using `FrameMonitor`'s last measured average frame time and pressure. This could retain an old `HEAVY` or `THROUGHPUT` decision during a background interval, then influence budgets when users returned.

**Changed:** `PerformanceRuntime.tick` recognizes transitions into/out of the focused-world state; clears frame-pressure and governor history once at that boundary; waits for **three new world-rendered frame samples** before accepting pressure data; keeps the existing balanced profile during warm-up. Background FPS limiting and deferred chunk task draining remain active. Optional culling evaluations are paused while unfocused or minimized instead of doing invisible per-tick ray work.

**Deliberate trade-off:** on returning focus, measurements rewarm briefly rather than using possibly stale state. There is no hidden graphics downgrade. Chunk rebuild tasks still drain so correctness does not depend on keeping the window focused. The culling queue is bounded and stale decisions render conservatively.

**Proof needed:** alt-tab repeatedly while a build/chunk backlog exists, minimize while heavily lagging, return with previously smooth frames, and compare governor states, p95 frame times and backlog progress. Validate on a real Fabric client before treating perceived smoothness as proved.

### P1/P2 — Permanent strong-reference growth in model memory lookup

**Observed:** `MemoryDeduplicator` stored every unique baked-quad vertex array under a strongly referenced content key in a `ConcurrentHashMap`, with no admission limit until resource reload. Turning `memoryOptimizations` off did not release this lookup ownership.

**Changed:** retain at most **131,072 unique quads** and **8,388,608 vertex integers** (32 MiB raw vertex data), skipping unusually large arrays (>256 ints per quad). Already-known duplicates still reuse canonical arrays after the cache reaches capacity. New arrays stay valid and unmodified; rendering never depends on cache admission. Lock-free cache hits are retained; insertion and clearing use one narrow lock. `PerformanceRuntime.updatePreferences` clears the one existing dedup cache if the user disables memory optimization. Resource reload still clears it through the existing listener.

**Boundary:** the 32 MiB estimate covers raw int-array payloads **only**, not `IntArrayKey`, hash map nodes, object headers, allocator overhead or baked models still reachable elsewhere. Do **not** report it as a total Java heap cap. For unusually large modded vertex arrays, deduplication is intentionally bypassed.

**Proof needed:** modpack/resource-pack load, reload, change world, toggle memory optimization, take heap/native-memory measurements and confirm no model corruption. Model mutation-after-bake by unrelated mods remains an interoperability issue requiring representative runtime proof.

### P2 — Numerical telemetry corruption / counter overflow

**Observed:** `PerformanceGovernor.Input` used `Math.max/min` directly on doubles. Nonfinite values such as NaN remain NaN. Governor and `FrameMonitor` pressure counters grew for every sustained sample despite having fixed threshold decisions.

**Changed:** convert nonfinite optional telemetry to safe numeric zeros and saturate governor/frame state counters at their respective transition thresholds. This preserves normal numeric policy for valid inputs while stopping unnecessary lifetime growth.

**Proof needed:** malformed telemetry and long-running frame-pressure fixtures, checked against unchanged existing good-value behavior.

## Performance and UX acceptance

- User-configured render/simulation distance, shaders, textures, particles and GUI settings must never silently change due to an adaptive policy.
- Background/minimized caps apply only while appropriate; returning focus must restore the user's FPS limit without a settings-reload hitch.
- The governor must not promote to THROUGHPUT or remain in HEAVY based only on idle/background frames.
- Optional culling must fail open when evidence is missing, never hide entities wrongly for apparent FPS gains.
- Resource-pack reload and user opt-out must not retain unique geometry indefinitely.
- Existing on-demand performance snapshots remain available without enabling always-on instrumentation.
- No new permanent HUD, profiler database, background scheduler, forced GC, custom memory-cleaner or complex presets.
- Deeper renderer/VRAM arena work is **frozen** pending a representative live GPU run.

## Cohesive tests already written but not executed

1. `PerformanceRuntimeFocusPolicyTest`: focus/sample admission.
2. `PerformanceGovernorTest`: nonfinite metrics, extended protective decision.
3. `MemoryDeduplicatorTest`: duplicate reuse, cache limit, bypass oversized modded arrays.
4. Existing frame monitor and rendering tests continue to form the baseline. Test files alone are not executable proof.

## Next verified gate, when hardware is available

Use the **same exact commit SHA** for `.github/workflows/performance-manager-verify.yml` (currently `workflow_dispatch` only) and Minecraft 1.21.4 Fabric. Compare 15–30-minute sessions with focused/unfocused/minimized transitions, chunk streaming, shader/resource reload, multiplayer/world switch and realistic modded build density. Record p50/p95/p99 frame time, stutter count, heap after GC, native/GPU memory where supported, counter growth, backlog progress and visual correctness. Do not quote invented FPS improvements or absolute percentage savings before measurement.

**Release conclusion:** source hardened for conservative foreground policy and bounded cache ownership, **NOT production-verified**.
