# LazyBuilder Module Boundaries

LazyBuilder is a suite. Each manager owns one responsibility and must be maintainable, testable, and releasable without requiring unrelated managers to change.

## Module ownership

| Module | Runtime | Owns | Must not own |
| --- | --- | --- | --- |
| Server-Manager | Desktop app | Paper process lifecycle, server health, basic server settings | world lifecycle, plugin runtime behavior |
| Plugin-Manager | Desktop app | plugin inventory, category, install/update/disable/remove, compatibility/dependency checks | plugin feature logic, Paper runtime internals |
| World-Manager | Paper plugin | world lifecycle, BUILD_READY, import/export/conversion, archive/backup, world settings | desktop process lifecycle, generic builder utilities |
| Utilities-Manager | Paper plugin | small builder/server convenience features | world lifecycle, performance tuning, plugin installation |

External build tools such as Axiom, FAWE, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external modules and are not wrapped or duplicated by LazyBuilder.

## Modularity rules

1. **One semantic owner per responsibility.** A behavior lives in exactly one manager.
2. **No cross-module implementation imports.** Managers communicate through explicit contracts/protocols, not by importing another manager's internal packages.
3. **Independent module versions.** Every deployable plugin declares its own artifact version so it can receive feature/bugfix releases independently from the suite version.
4. **Independent configuration.** Each deployable module owns only its configuration. Shared settings must have one canonical owner and other modules read them through a contract.
5. **Independent tests.** Every module keeps its tests beside its source and must be buildable/testable in isolation where practical.
6. **No mandatory central runtime plugin.** LazyBuilder is the product name, not a master Paper plugin. Paper modules remain independently deployable unless a proven runtime dependency is required.
7. **Stable public boundary, private internals.** Only contract/protocol packages are intended for cross-module use. Application, infrastructure, filesystem, Paper adapters, and UI implementation remain private to the owning module.
8. **Feature growth stays inside the owner.** Adding a World-Manager feature must not require editing Utilities-Manager or Plugin-Manager unless a contract genuinely changes.
9. **Backward-compatible protocol evolution.** Any protocol shared between desktop/Fabric/Paper uses an explicit protocol version and rejects incompatible peers clearly.
10. **No idle subsystem by default.** New modules/features do not add pollers, watchers, workers, or background processes unless the feature requires them while active.

## Versioning model

LazyBuilder may have an overall release label, but deployable components keep their own versions:

```text
LazyBuilder Suite          0.x
World-Manager.jar          0.1.x
Utilities-Manager.jar      0.1.x
LazyBuilder desktop        0.1.x
LazyBuilder Fabric client  0.1.x
```

A feature update to one plugin does not require artificially bumping every other plugin.

## Dependency direction

```text
Desktop UI
  ├─ Server-Manager
  └─ Plugin-Manager
          │
          │ explicit local/control contracts only
          ▼
Paper
  ├─ World-Manager
  └─ Utilities-Manager

Fabric client
  └─ versioned World-Manager protocol / map integration
```

World-Manager and Utilities-Manager do not depend on each other by default.

## Source layout target

```text
apps/
  lazybuilder-desktop/

modules/
  world-manager/
  utilities-manager/

client/
  fabric/

shared/
  protocol/
  models/
```

`shared/` is intentionally small. It may contain immutable DTOs, protocol codecs, IDs, and version contracts. It must not become a generic common-utilities module.

## Maintenance rule

When a future feature is proposed, first answer: **which manager owns this outcome?**

- If exactly one manager owns it, implement it there.
- If no existing manager owns it, add a new module only when the responsibility is substantial and durable.
- If multiple managers appear to own it, fix the boundary before implementing the feature.
