# Client Cross-Manager Audit Lock

## Purpose

This document closes Phase C4 for the LazyBuilder client architecture by auditing Map Manager, Utility Manager, and Performance Manager together.

The goal is to verify that the three Managers remain independent, non-overlapping, and compatible with the existing Vanilla/Axiom/WorldEdit/performance-mod workflow before any build-specific client work begins.

## Final ownership model

```text
LazyBuilder Client Suite
├── Map Manager
│   └── world / map / transfer workflow
├── Utility Manager
│   └── passive client convenience
└── Performance Manager
    └── performance status / lightweight resource policy

External specialist tools
├── Vanilla Minecraft
├── Axiom / WorldEdit
└── Sodium / Iris / optimization stack
```

Each Manager is one Fabric mod and one output JAR. No subfeature is allowed to become an additional runtime component without a separate architecture review.

## Cross-manager dependency audit

### Map Manager

Allowed dependencies:

- Minecraft/Fabric client APIs;
- shared protocol source required by the World-Manager wire contract.

Forbidden dependencies:

- Utility Manager implementation packages;
- Performance Manager implementation packages.

Map Manager must preserve existing map/world behavior when installed without Utility Manager or Performance Manager.

### Utility Manager

Allowed dependencies:

- Minecraft/Fabric client APIs only for its current feature set.

Forbidden dependencies:

- Map Manager implementation packages;
- Performance Manager implementation packages;
- shared World-Manager protocol for generic convenience features.

Utility features must not require map/project ownership to function.

### Performance Manager

Allowed dependencies:

- Minecraft/Fabric client APIs;
- Fabric Loader presence checks for optional optimization capabilities.

Forbidden dependencies:

- Map Manager implementation packages;
- Utility Manager implementation packages;
- direct implementation dependencies on Sodium, Iris, Dynamic FPS, or other specialist optimization mods.

Performance Manager may detect external mods, but it must not absorb or copy their engines.

## Ownership overlap audit

| Concern | Owner | Non-owner behavior |
| --- | --- | --- |
| World/map navigation | Map Manager | Utility/Performance do not participate |
| Transfer/world workflow | Map Manager | Utility/Performance do not participate |
| Chat convenience | Utility Manager | Map/Performance do not modify chat |
| Borderless/window presentation | Utility Manager | Performance only reads focus/minimize state |
| Screenshot naming | Utility Manager | Map Manager does not become a screenshot dependency |
| Clipboard convenience | Utility Manager | Map Manager owns its own project/world metadata actions |
| FPS/memory/status observation | Performance Manager | Utility does not create a performance HUD |
| Background FPS limit | Performance Manager | Utility owns no FPS/resource throttling |
| Renderer/shaders/culling | External optimization stack | No LazyBuilder Manager reimplements it |
| Building/editing tools | Axiom/WorldEdit/Vanilla | No LazyBuilder Manager duplicates them |

The only shared concept currently observed by more than one Manager is window state:

- Utility Manager may change window presentation through borderless mode;
- Performance Manager may read focus/minimized state for background FPS policy.

This is not duplicate ownership because one Manager owns presentation and the other owns resource policy. No shared implementation package is required.

## Shared-service audit

No generic cross-manager shared client service is justified at this stage.

Do **not** extract a new shared client module for:

- notifications;
- clipboard;
- config persistence;
- window state;
- performance state;
- map UI primitives.

A shared implementation is only justified after a second real consumer needs the same stable contract. Premature extraction would create coupling without reducing ownership ambiguity.

## Artifact identity lock

```text
Map Manager
Fabric id: lazybuilder_map_manager
Artifact: lazybuilder-map-manager.jar

Utility Manager
Fabric id: lazybuilder_utility_manager
Artifact: lazybuilder-utility-manager.jar

Performance Manager
Fabric id: lazybuilder_performance_manager
Artifact: lazybuilder-performance-manager.jar
```

All three remain independent Fabric source authorities under `client/`.

## External compatibility lock

### Vanilla

Vanilla controls and familiar interactions remain authoritative where they already exist. LazyBuilder should augment context rather than replace core interaction patterns.

### Axiom / WorldEdit

Build/edit functionality remains external. The client suite must not add measurement, selection, palette, brush, placement, terrain, symmetry, freecam, precision-build, or other build systems merely because they are useful to builders.

### Performance stack

Sodium, Iris, ImmediatelyFast, FerriteCore, EntityCulling, and MoreCulling remain external specialist foundations.

Dynamic FPS is treated as an optional external owner for background-FPS behavior. When present, LazyBuilder Performance Manager's native fallback must remain inactive.

## Automated repository guards

Repository consistency checks should enforce the architecture lock where practical:

- exact Fabric mod ids and artifact names;
- exactly one `fabric.mod.json` per Manager source authority;
- no Java references to another Manager's implementation package;
- no Gradle dependency from one Manager to another;
- Utility/Performance remain detached from shared World-Manager protocol;
- Map Manager remains the only client Manager wired to shared World-Manager protocol.

These guards are architectural checks, not a substitute for behavior testing.

## C4 decision

Phase C4 is considered complete when the repository reflects these boundaries and automated consistency checks guard against obvious cross-manager coupling.

```text
C1 Map Manager          complete
C2 Utility Manager      complete / scope locked
C3 Performance Manager  complete baseline / scope locked
C4 Cross-manager audit  complete / architecture locked
```

The next client design phase is **not** another Manager expansion. Any new builder-facing feature must be reviewed separately against Vanilla, Axiom, WorldEdit, and the existing three Manager ownership boundaries before implementation.
