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
| Performance Manager | `mods/performance-manager/` | Fabric | performance/resource coordination and capability detection | renderer/shader/culling/memory engines |
| Shared Protocol | `shared/protocol/` | Paper + Fabric | neutral versioned request/result/value contracts | Paper/Fabric implementation logic |

External build tools such as Vanilla, Axiom, FAWE, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external specialist owners.

## Modularity rules

1. One semantic owner per responsibility.
2. No cross-component implementation imports; communicate through explicit contracts.
3. One Fabric Manager = one deployable mod/JAR.
4. Each deployable component owns only its own configuration and tests.
5. LazyBuilder is the product name, not a mandatory master runtime component.
6. Only contract/protocol packages are public cross-runtime boundaries by default.
7. Feature growth stays inside the owning component.
8. Shared protocol evolution is explicit and versioned.
9. No idle poller/watcher/worker without a concrete active requirement.
10. One runtime path owner per persisted/runtime concern.

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
  └─ performance-manager/ ── independent
```

The three Fabric Managers do not import one another's implementation packages. `plugins/world-manager/` and `plugins/utilities-manager/` also remain independent by default.

## Versioning model

```text
LazyBuilder Suite                   0.x
World-Manager.jar                   0.1.x
Utilities-Manager.jar               0.1.x
LazyBuilder desktop                 0.1.x
lazybuilder-map-manager.jar         0.1.x
lazybuilder-utility-manager.jar     0.1.x
lazybuilder-performance-manager.jar 0.1.x
```

A feature update to one component does not require artificial version changes in unrelated components.

## Maintenance rule

When a future feature is proposed, first answer: **which component owns this outcome?**

- one clear owner → implement there;
- no current owner → add a new runtime/module only when the responsibility is substantial and durable;
- multiple apparent owners → repair the boundary before implementation.
