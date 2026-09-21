# LazyBuilder Module Boundaries

LazyBuilder is a suite. Each component owns one responsibility and must remain maintainable/testable without forcing unrelated components to change.

## Module ownership

| Component | Source | Runtime | Owns | Must not own |
| --- | --- | --- | --- | --- |
| Server-Manager | `apps/launcher/` | Desktop | Paper process lifecycle, Java/runtime paths, workspace bootstrap, health/resources | world lifecycle, plugin feature logic |
| Plugin-Manager | `apps/launcher/` | Desktop | third-party Paper plugin lifecycle | plugin feature logic, Paper runtime internals |
| World-Manager | `plugins/world-manager/` | Paper | world lifecycle, BUILD_READY, import/export/conversion, archive/backup, settings | desktop process lifecycle, generic builder utilities |
| Utilities-Manager | `plugins/utilities-manager/` | Paper | small builder/server conveniences | world lifecycle, client QoL, performance tuning |
| Map Manager | `mods/map-manager/` | Fabric | world/map/transfer UI and World-Manager protocol client | generic client QoL, renderer optimization, build editing |
| Utility Manager | `mods/utility-manager/` | Fabric | passive non-building client convenience | build tools, world lifecycle, performance engines |
| Performance Manager | `mods/performance-manager/` | Fabric | first-party client performance behavior, renderer/chunk/resource policy and diagnostics | Map/Utility workload ownership, build editing |
| Builder Utilities | `mods/builder-utilities/` | Fabric + Axiom | Axiom-first extension capabilities, bounded mutation lifecycle, history/recovery and builder-specific execution | replacing Axiom's primary editor, becoming a fourth core Manager, generic client QoL/performance ownership |
| Shared Protocol | `shared/protocol/` | Paper + Fabric | neutral versioned request/result/value contracts | Paper/Fabric implementation logic |
| System Coordination | `apps/launcher/src-tauri/src/engine/system/` | Desktop | read-only system snapshot, readiness/capability/activity projection, composition | durable domain state, business logic, bypassing owner validation |

## Product lanes

The distinction between the core client suite and builder extension is intentional:

```text
V1 Client Setup / required runtime
├── Map Manager
├── Utility Manager
└── Performance Manager

Builder development extension
└── Builder Utilities
    └── requires Axiom >=5.3.0 <5.5.0

Legacy/prototype terrain lane
```

Builder Utilities is not installed by the current V1 Client Setup transaction. The former Terraform prototype is retired from active source; Git history is its archive.

Axiom remains the primary builder editor/interaction owner. FAWE, FastAsyncVoxelSniper, ezEdits and MetaBrushes remain external specialist/reference tools unless an explicit product decision assigns a narrow non-overlapping capability to LazyBuilder.

## Modularity rules

1. One semantic owner per responsibility.
2. No cross-component implementation imports; communicate through explicit contracts.
3. One core Fabric Manager = one deployable mod/JAR.
4. Builder Utilities is an independent extension artifact, not a fourth core Manager.
5. Each deployable component owns only its own configuration and tests.
6. LazyBuilder has no master business runtime component. The Launcher may host a thin composition/system-coordination layer that reads existing authorities and projects system readiness/capabilities without owning their domain state.
7. Only contract/protocol packages are public cross-runtime boundaries by default.
8. Feature growth stays inside the owning component.
9. Shared protocol evolution is explicit and versioned.
10. No idle poller/watcher/worker without a concrete active requirement.
11. One runtime path owner per persisted/runtime concern.
12. Do not reintroduce a second LazyBuilder terrain/build owner beside Builder Utilities without a proven distinct responsibility.
13. Shared execution abstractions require a real repeated responsibility; Builder Utilities keeps its operation lifecycle inside its own module.
14. Cross-component coordination uses typed contracts or read-only projections; do not introduce a generic mutable event bus or duplicate state store.
15. A projected capability never replaces execution-time validation by the semantic owner.
16. Tauri command modules stay transport-thin; cross-owner composition belongs in `engine/system/`.
17. Repeated UI observation of the same projection uses one shared feed rather than parallel polling loops.
18. Large integration owners may split internal transport/types/status files while preserving one external semantic owner.

## Dependency direction

```text
apps/launcher/
  ├─ Server-Manager
  ├─ Plugin-Manager
  └─ desktop World-Manager control client
          │
          │ authenticated loopback contract
          ▼
plugins/
  ├─ world-manager/
  └─ utilities-manager/

mods/
  ├─ map-manager/ ── shared/protocol ── world-manager
  ├─ utility-manager/ ── independent
  ├─ performance-manager/ ── independent
  └─ builder-utilities/ ── Axiom public client API / explicit extension boundary
```

The three core Fabric Managers do not import one another's implementation packages. Builder Utilities also must not import core Manager implementation packages merely for convenience.

## Versioning model

```text
LazyBuilder Suite                   0.x
World-Manager.jar                   0.1.x
Utilities-Manager.jar               0.1.x
LazyBuilder desktop                 0.1.x
lazybuilder-map-manager.jar         0.1.x
lazybuilder-utility-manager.jar     0.1.x
lazybuilder-performance-manager.jar 0.1.x
lazybuilder-builder-utilities.jar   independent extension artifact
```

A feature update to one component does not require artificial version changes in unrelated components.

## Maintenance rule

When a future feature is proposed, first answer: **which component owns this outcome?**

- one clear owner → implement there;
- Axiom already owns the workflow well → keep it in Axiom;
- Builder Utilities adds a narrow missing capability without replacing Axiom → implement in Builder Utilities;
- no current owner → add a new runtime/module only when the responsibility is substantial and durable;
- multiple apparent owners → repair the boundary before implementation;
- A proposed builder subsystem overlaps Builder Utilities → keep Builder Utilities authoritative unless a distinct non-overlapping responsibility is proven.
