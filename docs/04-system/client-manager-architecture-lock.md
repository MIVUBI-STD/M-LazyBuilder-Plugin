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

Current map workload safeguards include bounded frame work, incremental cache merge, viewport sample caching, primitive/lazy region storage, primitive pending coordinates, bounded region loads, and a dedicated map I/O lane.

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
- reconnect target remains session-only and is not persisted;
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

Current P0-P3 scope:

- actual frame-time monitoring with rolling pressure state;
- `NORMAL / ELEVATED / HEAVY` frame pressure;
- LazyBuilder workload-budget classification;
- first-party unfocused/minimized FPS policy;
- on-demand FPS/frame-time/JVM-memory status;
- on-demand render-distance and simulation-distance status;
- on-demand window focus/minimized state;
- on-demand Vanilla chunk/entity/particle workload diagnostics.

Performance Manager has no mandatory external optimization dependency. External optimization mods may coexist, but required LazyBuilder behavior does not hand ownership to them.

Current explicit non-scope until profiling justifies it:

- renderer replacement;
- shader implementation/management;
- generic entity or block-entity culling replacement;
- particle-frustum bridge;
- chunk-renderer replacement;
- automatic visual-quality reduction;
- permanent performance HUD/history database.

Vanilla 1.21.4 already owns important entity visibility and block-entity render-distance behavior. LazyBuilder should not duplicate those paths merely to claim an optimization.

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

## Repository shape

```text
mods/
├── map-manager/          -> lazybuilder-map-manager.jar
├── utility-manager/      -> lazybuilder-utility-manager.jar
└── performance-manager/  -> lazybuilder-performance-manager.jar
```

Shared protocol types genuinely consumed by Paper and Fabric remain under the existing shared protocol ownership. Do not create a generic shared client implementation tree merely for convenience.

## Phase status

```text
Map Manager          implemented / map workload stabilized
Utility Manager      implemented / architecture locked
Performance Manager  P0-P3 foundation complete
Cross-manager audit  architecture locked
```

Renderer-level P4 work is not automatically next. It is gated by runtime profiling evidence from the on-demand diagnostics already exposed by Performance Manager.

## Next gate

The next step for the current client suite is exact-state verification and defect correction, not feature expansion.
