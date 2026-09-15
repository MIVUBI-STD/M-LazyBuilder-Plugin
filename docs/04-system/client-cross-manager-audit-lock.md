# Client Cross-Manager Audit Lock

## Purpose

This document locks the current Map Manager, Utility Manager, and Performance Manager boundaries after the P0-P3 client cleanup.

The goal is to keep the three Managers independent, non-overlapping, first-party where LazyBuilder requires behavior, and free from unnecessary runtime coupling.

## Final ownership model

```text
LazyBuilder Client Suite
├── Map Manager
│   └── world / map / transfer workflow
├── Utility Manager
│   └── passive generic client convenience
└── Performance Manager
    └── first-party performance policy / diagnostics
```

Each Manager is one Fabric mod and one output JAR. Internal feature groups are not separate mods.

## Cross-manager dependency audit

### Map Manager

Allowed dependencies:

- Minecraft/Fabric client APIs;
- shared protocol types required by the World-Manager wire contract.

Forbidden dependencies:

- Utility Manager implementation packages;
- Performance Manager implementation packages.

Map Manager owns its own workload discipline. Performance Manager does not reach into Map Manager implementation packages to schedule map work.

### Utility Manager

Allowed dependencies:

- Minecraft/Fabric client APIs only for its current feature set.

Forbidden dependencies:

- Map Manager implementation packages;
- Performance Manager implementation packages;
- shared World-Manager protocol for generic convenience behavior.

Utility Manager remains screen/event-driven with no client tick loop, poller, worker, or permanent HUD.

### Performance Manager

Allowed dependencies:

- Minecraft/Fabric client APIs.

Forbidden dependencies:

- Map Manager implementation packages;
- Utility Manager implementation packages;
- mandatory runtime dependency on an external optimization mod.

Required LazyBuilder performance behavior is first-party and LazyBuilder-maintained. External optimization mods may coexist, but they are not authoritative owners for required LazyBuilder behavior.

## Ownership overlap audit

| Concern | Owner | Non-owner behavior |
| --- | --- | --- |
| World/map navigation | Map Manager | Utility/Performance do not participate |
| Transfer/world workflow | Map Manager | Utility/Performance do not participate |
| Map surface cache/workload | Map Manager | Performance does not import Map internals |
| Chat convenience | Utility Manager | Map/Performance do not modify chat |
| Borderless/window presentation | Utility Manager | Performance only reads focus/minimized state |
| Screenshot naming | Utility Manager | Map Manager does not become a screenshot dependency |
| Contextual clipboard convenience | Utility Manager | Map owns only map/world metadata actions |
| Frame pressure/workload policy | Performance Manager | Utility owns no performance policy |
| Background FPS limit | Performance Manager | Utility owns no FPS/resource throttling |
| On-demand performance diagnostics | Performance Manager | No permanent monitoring HUD |
| Building/editing systems | Deferred separate scope | Current three Managers do not absorb them |

The only shared concept observed by more than one Manager is window state:

- Utility Manager may change window presentation through startup-only borderless mode;
- Performance Manager reads focus/minimized state for background resource policy.

This is not duplicate ownership: presentation and resource policy are distinct domains.

## Performance ownership lock

Performance Manager currently owns:

- frame-time observation and pressure state;
- LazyBuilder workload budget classification;
- background/minimized FPS policy;
- on-demand FPS, frame-time, memory, render/simulation distance and window diagnostics;
- on-demand Vanilla chunk/entity/particle debug counters.

It does **not** currently own:

- renderer replacement;
- shader implementation;
- generic entity/block-entity culling replacement;
- particle frustum bridge;
- chunk renderer replacement;
- graphics-quality auto-tuning.

Those deeper optimizations require profiling evidence before implementation. This is a scope gate, not external ownership delegation.

## Map workload lock

Map Manager keeps map-specific optimization local to its own owner:

- bounded per-frame terrain work;
- incremental completed-region merge;
- cached viewport sampling;
- primitive/lazy region storage;
- primitive pending coordinate set;
- bounded asynchronous region loads;
- dedicated ordered map I/O lane;
- explicit map I/O shutdown lifecycle.

Performance Manager must not import Map Manager internals merely to control these mechanisms.

## Shared-service audit

No generic cross-manager shared client service is currently justified.

Do not extract a new shared client implementation module for:

- notifications;
- clipboard;
- config persistence;
- window state;
- performance state;
- map UI/cache primitives.

Extraction requires a second real consumer and a stable shared contract.

## Artifact identity lock

```text
Map Manager
Fabric id: lazybuilder_map_manager
Artifact: lazybuilder-map-manager.jar
Source: mods/map-manager/

Utility Manager
Fabric id: lazybuilder_utility_manager
Artifact: lazybuilder-utility-manager.jar
Source: mods/utility-manager/

Performance Manager
Fabric id: lazybuilder_performance_manager
Artifact: lazybuilder-performance-manager.jar
Source: mods/performance-manager/
```

## Repository guards

Repository consistency checks should enforce where practical:

- one Fabric mod identity per Manager;
- one output artifact per Manager;
- no Java import of another Manager's implementation package;
- no Gradle dependency from one Manager to another;
- Utility/Performance remain detached from shared World-Manager protocol;
- Map Manager remains the only client Manager wired to shared World-Manager protocol;
- no mandatory external optimization dependency for required Performance behavior.

## Current decision

```text
Map Manager          implemented / workload stabilized
Utility Manager      implemented / architecture locked
Performance Manager  P0-P3 foundation complete / deeper renderer work deferred pending profiling
Cross-manager audit  architecture locked
```

The next step is verification of this exact repository state, not another Manager expansion.
