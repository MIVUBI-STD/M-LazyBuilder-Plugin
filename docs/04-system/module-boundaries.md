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
├── mods/terraform-manager/
├── plugins/terraform-manager/
└── shared/terraform-core/
```

Builder Utilities is not installed by the current V1 Client Setup transaction. Terraform is not a parallel production editor; it remains a legacy/prototype lane pending explicit retirement or a proven distinct responsibility.

Axiom remains the primary builder editor/interaction owner. FAWE, FastAsyncVoxelSniper, ezEdits and MetaBrushes remain external specialist/reference tools unless an explicit product decision assigns a narrow non-overlapping capability to LazyBuilder.

## Modularity rules

1. One semantic owner per responsibility.
2. No cross-component implementation imports; communicate through explicit contracts.
3. One core Fabric Manager = one deployable mod/JAR.
4. Builder Utilities is an independent extension artifact, not a fourth core Manager.
5. Each deployable component owns only its own configuration and tests.
6. LazyBuilder is the product name, not a mandatory master runtime component.
7. Only contract/protocol packages are public cross-runtime boundaries by default.
8. Feature growth stays inside the owning component.
9. Shared protocol evolution is explicit and versioned.
10. No idle poller/watcher/worker without a concrete active requirement.
11. One runtime path owner per persisted/runtime concern.
12. Do not expand Terraform and Builder Utilities as competing owners for the same terrain/build operation.
13. Shared execution abstractions require a real repeated responsibility; Builder Utilities keeps its operation lifecycle inside its own module.

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
- Terraform and Builder Utilities appear to own the same outcome → do not expand either until one authority is selected.
