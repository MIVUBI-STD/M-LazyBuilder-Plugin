# Performance Manager — Compatibility, lifecycle and proof diagnostics audit

**Review date:** 2026-10-13  
**Branch:** `Local`  
**Scope:** renderer ownership compatibility, shader/resource reload lifecycle, focused-session proof quality, and bounded diagnostics. **Not included:** building tools, world-management operations, renderer replacement, GPU allocator or multi-draw rewrites.

**Verification:** SOURCE_REVIEWED / SOURCE_IMPLEMENTED. This document does not certify a passing Gradle build or native Minecraft 1.21.4 runtime test. The user's current workflow postpones Windows/local testing.

## Existing architecture — keep

- The MixinConfigPlugin applies hooks only for positively recognized optimization domains. Unclassified hooks fail closed rather than silently modifying another renderer.
- `RendererCompatibility.detect` checks Fabric's `fabric-renderer-api-v1:contains_renderer` marker and Iris presence. Under metadata uncertainty, first-party terrain build/upload/submission are withheld; nonoverlapping domains remain available.
- Domain routing intentionally distinguishes immediate/text/upload hooks, entity culling and shader-sensitive terrain submission. It does not impose a blanket “Sodium installed = disable everything” rule.
- Resource-pack reload already clears the `MemoryDeduplicator` cache and invalidates shader-sensitive terrain submission state through existing client resource listeners.
- Exclusive physical terrain arena residency is recovery-first and should not be cleared casually. Do not replace this owner or create a global cache manager.
- Frame proof is opt-in (`-Dlazybuilder.performance.proof=true`) and emits bounded snapshots every 120 valid frames; ordinary UI does not allocate a permanent metrics history.

Reference: Fabric's one-renderer registration model and `fabric-renderer-api-v1:contains_renderer` marker are documented in the Fabric renderer documentation. Fabric Loader defines metadata and mixin ownership, but metadata alone is not a live correctness test.
- https://wiki.fabricmc.net/drafts:rendering
- https://docs.fabricmc.net/develop/loader/
- https://maven.fabricmc.net/docs/fabric-api-0.119.2%2B1.21.4/net/fabricmc/fabric/api/resource/SimpleResourceReloadListener.html

## Source fixes applied this batch

### P1 — stale async shader reload completion may retire a newer reload

**Observed:** a shader-only reload Future queued a callback that unconditionally set `shaderTerrainReloadInFlight=false`, cleared generation when matched, and then consulted the **current** shaderRuntime. When an older reload completed late after another request or shutdown, it could change current state or attribute results to the wrong owner.

**Changed:** callback now verifies both exact `shaderTerrainReloadGeneration` and **identity** of the `FirstPartyShaderRuntime` that submitted the operation before altering flags or recording completion. Obsolete callbacks are discarded. This uses the existing generation/owner, not a new operation manager.

**Required runtime proof:** change shader pack in rapid succession, induce long shader compilation then disable/re-enable, trigger resource pack reload mid-flight, shutdown while compilation is pending, and confirm that the newest request remains authoritative. Also prove that no stage remains “in progress” incorrectly after failure.

### P1/P2 — frame proof histogram mixed foreground sessions across pause

**Observed:** `PerformanceRuntime.recordFrame` already paused the frame-control clock while unfocused/minimized, but `PerformanceRuntimeProofLogger` held its long-run histogram until the *world* changed. A minimized client may also stop firing world-render events entirely; a pause in the render hook alone cannot close a benchmark window.

**Changed:** both inactive-frame and nonforeground **client tick** paths close the proof window. The next focused world frame starts a new proof session. The frame monitor now exposes a primitive fresh-sample flag: first baseline frames and clock discontinuities are not copied into histogram as if they were new observations. The proof logger also requires a finite positive timing and a measured sample.

**Required proof:** alt-tab, minimize, pause/world-exit, frame-time discontinuities and world change under `-Dlazybuilder.performance.proof=true`. Check that a new `LB_PERF_PROOF_BEGIN` denotes a fresh interval; no artificial 0 ms frames, old p95/p99 samples, or background gaps are reported.

### P2 — compatibility guard already exists; do not duplicate

The existing `OptimizationCompatibility` and `PerformanceMixinPlugin` form one conservative permission-to-hook boundary. No extra library/runtime dependency or per-mixin bespoke mod checks were added. Mod compatibility must still be exercised with representative combinations of **vanilla Indigo, Sodium, Iris, ImmediatelyFast, EntityCulling and FerriteCore**. This is a test matrix, not a guarantee of parity or absence of conflict.

### P2 — resource reload and GPU ownership need exact-version acceptance

The existing shader/resource reload invalidator holds shader-sensitive state, while terrain residency remains owned by the renderer and recovery path. Avoid clearing renderer physical arenas or calling destructive GL lifecycle actions from arbitrary worker threads merely to “be thorough.” Validate resource reload under each renderer combination with overlays, chunk reuploads, shader compile failure and world switch. Any changes here must be justified by captured failures or precise API contracts.

## Minimal acceptance matrix

| Scenario | Expected behavior | Current proof |
| --- | --- | --- |
| Vanilla Indigo, first-party pipeline | First-party domains run where declared safe | Static source/tests only |
| Sodium or custom renderer | Overlapping terrain domains stand down | Static policy only |
| Iris | Shader-sensitive terrain submission conservatively stands down | Static policy only |
| ImmediatelyFast | Immediate/text/upload overlaps stand down | Static policy only |
| EntityCulling | First-party entity/block-entity culling stands down | Static policy only |
| Resource-pack reload | Dedup cache and shader-sensitive state invalidate; terrain backing preserved | Source path verified; runtime untested |
| World change/shutdown | Stale shader callback does not mutate newer instance or generation | Regression source added; runtime untested |
| Alt-tab/minimize in proof mode | Histogram window resets; only fresh frame samples count | Regression source added; runtime untested |

## Keep out of scope until test hardware is available

No new renderer implementation, shader engine, cache database, permanent FPS overlay, process monitor, telemetry worker, forced garbage collector, graphics-quality reduction or automatic profile switching. Keep current Minecraft-native Settings vocabulary and expose detailed diagnostics only on demand / in explicit proof runs.

## Verification gate

The repo already has `.github/workflows/performance-manager-verify.yml` for Java 21 Windows/Gradle build and tests, but **a workflow YAML file is not a PASS result**. Verify the final exact SHA, then run native Fabric 1.21.4 with the listed combinations. Record p50/p95/p99 frame times, shader reload failures and generation, chunk-backlog drain, player-visible visual correctness, memory after reload, and whether GPU backing remains recoverable. Never invent FPS/VRAM gains before measurement.

**Conclusion:** Conservative source remediation and reproducible acceptance checklist recorded; no production-ready claim.
