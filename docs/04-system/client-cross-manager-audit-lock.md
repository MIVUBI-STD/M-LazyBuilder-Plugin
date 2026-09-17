# Client Cross-Manager Ownership Lock

## Purpose

This document locks the independent ownership boundaries of the LazyBuilder client modules. The earlier three-Manager-only lock is superseded by the explicit Builder Utilities product scope: building extensions are now allowed, but Axiom remains the primary editor/UX and the existing Managers remain independent.

## Current ownership model

```text
LazyBuilder Client Suite
├── Map Manager
│   └── world / map / transfer workflow
├── Utility Manager
│   └── passive generic client convenience
├── Performance Manager
│   └── first-party performance policy / diagnostics
├── Terraform Manager
│   └── existing terrain prototype under evaluation
└── Builder Utilities
    └── Axiom-first builder extension layer
```

Each module is one Fabric mod and one output JAR. Internal feature groups are not separate mods.

## Cross-module dependency rules

### Map Manager

Allowed dependencies:
- Minecraft/Fabric client APIs;
- shared protocol types required by the World-Manager wire contract.

Forbidden dependencies:
- other LazyBuilder Manager implementation packages.

### Utility Manager

Allowed dependencies:
- Minecraft/Fabric client APIs for passive convenience behavior.

Forbidden dependencies:
- other LazyBuilder Manager implementation packages;
- building/editing ownership;
- shared World-Manager protocol for generic convenience behavior.

### Performance Manager

Allowed dependencies:
- Minecraft/Fabric client APIs.

Forbidden dependencies:
- other LazyBuilder Manager implementation packages;
- mandatory runtime dependency on external optimization mods.

### Builder Utilities

Allowed dependencies:
- Minecraft/Fabric client APIs;
- the exact supported Axiom artifact and its public client API;
- neutral LazyBuilder contracts only when a real cross-platform builder capability requires them.

Forbidden dependencies:
- Map/Utility/Performance implementation packages;
- copied/repackaged Axiom implementation code;
- permanent FAWE or ezEdits runtime ownership used as a shortcut for native LazyBuilder capabilities;
- direct access to Axiom internals when the public client API can satisfy the capability;
- speculative mixins or compatibility layers without a proven missing public hook.

Builder Utilities owns only capabilities that improve the Axiom building workflow. Axiom remains the canonical editor interaction surface. The first implementation boundary is therefore the Axiom public client API, not a replacement editor.

## Builder donor model

```text
Axiom
→ primary editor, interaction model, selection/gizmo/preview/tool UX

FAWE
→ infrastructure reference for large-operation execution, history scaling,
  region/chunk processing, material/pattern semantics, limits and cancellation

 ezEdits
→ algorithm reference for spline, procedural placement/texturing,
  symmetry, flow and geometry modifiers
```

FAWE and ezEdits are migration references, not final authorities. Capability adoption must be reimplemented under the Axiom-first LazyBuilder architecture rather than preserving duplicate command/session/editing systems.

## Existing ownership overlap audit

| Concern | Owner | Non-owner behavior |
| --- | --- | --- |
| World/map navigation | Map Manager | Other modules do not participate |
| Transfer/world workflow | Map Manager | Other modules do not participate |
| Chat/window/screenshot convenience | Utility Manager | Builder Utilities does not absorb generic convenience |
| Frame pressure/resource policy | Performance Manager | Builder Utilities may consume a budget contract later but does not import Performance internals |
| Building/editor extension | Builder Utilities + Axiom public API | Existing Managers do not implement generic building tools |
| Existing Terraform prototype | Terraform Manager pending migration decision | Do not expand duplicate terrain ownership while Builder Utilities foundation is being established |

## Shared-service rule

Do not extract a generic cross-manager implementation module for notifications, clipboard, config, window state, performance state, or map primitives. Builder-specific primitives may exist inside Builder Utilities only after repeated building responsibilities prove they are shared within that module.

## Artifact identities

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

Builder Utilities
Fabric id: lazybuilder_builder_utilities
Artifact: lazybuilder-builder-utilities.jar
Source: mods/builder-utilities/
```

## Repository guards

Repository checks should enforce where practical:
- one Fabric mod identity per module;
- one output artifact per module;
- no imports of another LazyBuilder Manager's implementation package;
- Builder Utilities pins its supported Axiom line deliberately;
- Builder Utilities uses the Axiom public API as the first integration boundary;
- FAWE/ezEdits do not become required runtime dependencies for the final architecture.

## Current decision

```text
Axiom                     primary building/editor architecture
Builder Utilities         active extension scope
FAWE                       donor/reference; planned retirement after capability parity
 ezEdits                    donor/reference; planned retirement after capability parity
Terraform Manager          freeze expansion pending capability migration decision
Existing client Managers   ownership remains independent
```
