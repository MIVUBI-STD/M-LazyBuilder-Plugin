# LazyBuilder Client Manager Architecture Lock

Status: architecture lock for the `Local` branch after Map, Utility, Performance, and cross-manager cleanup.

## Purpose

LazyBuilder client-side functionality is split by semantic ownership. The goal is to keep the client familiar, minimize redundant systems, keep required LazyBuilder behavior first-party, and prevent cross-manager coupling.

Build-specific helper systems remain outside this lock and are reviewed separately.

## Client-side manager model

```text
LazyBuilder Client Suite
├── Map Manager          -> 1 Fabric mod / 1 JAR
├── Utility Manager      -> 1 Fabric mod / 1 JAR
└── Performance Manager  -> 1 Fabric mod / 1 JAR
```

All three Managers are active client components. The launcher, verification workflow, and client artifact must package the same three-manager set.

Each Manager owns one responsibility. Features must have exactly one owner.

## Map Manager

Owns:

- world list and world-management UI;
- world map surface/navigation;
- world settings and create/add/duplicate/delete flows;
- world transfer UI and transfer preferences;
- client map surface cache;
- Fabric-to-Paper World-Manager transport and payload handling;
- map-specific workload, cache, and I/O discipline.

Map-specific performance work stays inside Map Manager because the workload belongs to Map Manager. Performance Manager must not import Map internals to control it.

Current map safeguards include bounded per-client-tick terrain work, incremental cache merge, viewport sample caching, primitive/lazy region storage, primitive pending coordinates, retryable bounded region loads, revision-based persistence, dedicated map/transfer I/O lanes, generation-guarded transfer continuations, and bounded shutdown draining.

Canonical source:

```text
mods/map-manager/
```

Fabric id: `lazybuilder_map_manager`

Artifact: `lazybuilder-map-manager.jar`

## Utility Manager

Owns passive, non-building client convenience only.

Locked scope:

- borderless window presentation;
- extended chat history;
- unsent chat draft preservation within the active connection context;
- reconnect button;
- contextual connection/disconnect copy action;
- resource-reload completion notice;
- native-toast utility notification surface;
- contextual screenshot naming while preserving vanilla `F2`;
- preference persistence for implemented behavior only.

Runtime rules:

- no client tick loop;
- no background poller/worker;
- no permanent manager HUD;
- reconnect target remains session-only and is captured at connection attempt time, then confirmed on JOIN;
- chat draft is cleared on disconnect;
- borderless mode is startup-only and takes effect on the next client start after a preference change.

Extended Chat History intentionally stays a small mapping-sensitive Vanilla patch instead of becoming a replacement chat system. Minecraft upgrades must reverify its ChatHud constant hooks.

Canonical source:

```text
mods/utility-manager/
```

Fabric id: `lazybuilder_utility_manager`

Artifact: `lazybuilder-utility-manager.jar`

The Fabric Utility Manager remains distinct from the Paper Utilities-Manager server plugin.

## Performance Manager

Owns required LazyBuilder performance behavior first-party.

Current scope:

- target-aware actual frame-time monitoring with rolling `NORMAL / ELEVATED / HEAVY` pressure state;
- render-discontinuity protection during world/loading/focus transitions;
- first-party unfocused/minimized FPS policy;
- conservative entity/block-entity and render-side culling;
- chunk rebuild/backpressure, upload, visibility, buffer, and terrain-submission efficiency;
- targeted memory/deduplication;
- terrain GPU residency/reclamation and region/layer allocation;
- guarded physical-arena ownership, reversible vanilla backing recovery, per-draw transform streaming, and multi-draw submission when compatibility permits;
- pressure-aware distant-particle suppression;
- on-demand diagnostics and opt-in runtime proof logging.

Performance Manager intentionally does not expose an unused cross-manager workload scheduler. Each Manager bounds its own workload at its actual source owner until a second real consumer proves a shared scheduling contract is necessary.

Performance Manager has no mandatory external optimization dependency. Custom FRAPI renderer ownership, Iris, ImmediatelyFast, FerriteCore, and compatibility uncertainty gate overlapping first-party paths so only one owner is authoritative.

Explicit non-scope remains:

- automatic visual-quality reduction;
- generic shader/resource-pack replacement;
- permanent performance HUD/history database;
- cross-manager workload ownership.

Source/build proof establishes the implementation boundary. Representative gameplay still has to prove smoothness, visual correctness, frame-time improvement, and safe recovery under the actual renderer/mod stack.

Canonical source:

```text
mods/performance-manager/
```

Fabric id: `lazybuilder_performance_manager`

Artifact: `lazybuilder-performance-manager.jar`

## Ownership rules

1. One Manager = one Fabric mod = one output JAR.
2. Map Manager = world/map/transfer workflow and its own workload discipline.
3. Utility Manager = generic client convenience/usability.
4. Performance Manager = first-party performance policy and diagnostics.
5. Required LazyBuilder behavior must not require an external mod to function.
6. Vanilla behavior remains authoritative where it already solves the problem efficiently.
7. Do not add a shortcut when an existing Vanilla interaction or contextual action is sufficient.
8. Do not create duplicate renderer/culling systems without profiling evidence.
9. Shared services require a second real consumer and a stable contract before extraction.
10. No Manager imports another Manager's implementation packages.
11. Build-specific utilities stay outside this architecture lock.
12. New Utility/Performance features must pass an ownership, overlap, and runtime-cost review.

## Repository and release shape

```text
mods/
├── map-manager/          -> lazybuilder-map-manager.jar
├── utility-manager/      -> lazybuilder-utility-manager.jar
└── performance-manager/  -> lazybuilder-performance-manager.jar
```

The Fabric CI job must build all three JARs, the client artifact must stage all three, and Launcher Client Setup must install/repair all three as one coherent suite.

Shared protocol types genuinely consumed by Paper and Fabric remain under the existing shared protocol ownership. Do not create a generic shared client implementation tree merely for convenience.

## Phase status

```text
Map Manager          implemented / correctness hardening active
Utility Manager      implemented / architecture locked
Performance Manager  implemented / active client component
Cross-manager audit  architecture locked
```

Further renderer expansion is not automatically next. The implemented renderer path now requires exact-state verification and representative runtime evidence before any ownership is widened.

## Next gate

The next step for the current client suite is exact-state verification, runtime smoke testing, and defect correction, not feature expansion.
