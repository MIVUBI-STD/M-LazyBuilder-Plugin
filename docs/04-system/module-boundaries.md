# LazyBuilder Module Boundaries

LazyBuilder is a suite. Each manager owns one responsibility and must be maintainable, testable, and releasable without requiring unrelated managers to change.

## Module ownership

| Module | Runtime | Owns | Must not own |
| --- | --- | --- | --- |
| Server-Manager | Desktop app | Paper process lifecycle, server health, Java/runtime paths, canonical runtime bootstrap | world lifecycle, plugin runtime behavior |
| Plugin-Manager | Desktop app | plugin inventory, category, install/update/disable/remove, compatibility/dependency checks, canonical plugin-storage paths | plugin feature logic, Paper runtime internals |
| World-Manager | Paper plugin | world lifecycle, BUILD_READY, import/export/conversion, archive/backup, world settings | desktop process lifecycle, generic builder utilities |
| Utilities-Manager | Paper plugin | small builder/server convenience features | world lifecycle, performance tuning, plugin installation |
| Map Manager | Fabric client mod | world/map UI, navigation, transfer UI, World-Manager protocol client | generic client QoL, renderer optimization, build editing |
| Utility Manager | Fabric client mod | passive non-building client convenience | build tools, world lifecycle, performance engines |
| Performance Manager | Fabric client mod | performance/resource coordination and capability detection | renderer, shader, culling, or memory engines |

External build tools such as Axiom, FAWE, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external modules and are not wrapped or duplicated by LazyBuilder.

## Modularity rules

1. **One semantic owner per responsibility.** A behavior lives in exactly one manager.
2. **No cross-module implementation imports.** Managers communicate through explicit contracts/protocols, not by importing another manager's internal packages.
3. **One Manager = one deployable artifact.** Client subfeatures do not become standalone Fabric mods/JARs.
4. **Independent module versions.** Every deployable component declares its own artifact version so it can receive feature/bugfix releases independently from the suite version.
5. **Independent configuration.** Each deployable module owns only its configuration. Shared settings must have one canonical owner and other modules read them through a contract.
6. **Independent tests.** Every module keeps its tests beside its source and must be buildable/testable in isolation where practical.
7. **No mandatory central runtime plugin/mod.** LazyBuilder is the product name, not a master runtime component. Modules remain independently deployable unless a proven runtime dependency is required.
8. **Stable public boundary, private internals.** Only contract/protocol packages are intended for cross-module use. Application, infrastructure, filesystem, Paper adapters, and UI implementation remain private to the owning module.
9. **Feature growth stays inside the owner.** Adding a feature to one Manager must not require editing unrelated Managers unless a contract genuinely changes.
10. **Backward-compatible protocol evolution.** Any protocol shared between desktop/Fabric/Paper uses an explicit protocol version and rejects incompatible peers clearly.
11. **No idle subsystem by default.** New modules/features do not add pollers, watchers, workers, or background processes unless the feature requires them while active.
12. **One runtime path owner.** `server/`, `world-system/`, and `tools/lazybuilder/` each have explicit owners; compatibility fallbacks may read/migrate old paths but must not create a second active authority.

## Versioning model

LazyBuilder may have an overall release label, but deployable components keep their own versions:

```text
LazyBuilder Suite                   0.x
World-Manager.jar                   0.1.x
Utilities-Manager.jar               0.1.x
LazyBuilder desktop                 0.1.x
lazybuilder-map-manager.jar         0.1.x
lazybuilder-utility-manager.jar     0.1.x
lazybuilder-performance-manager.jar 0.1.x
```

A feature update to one component does not require artificially bumping every unrelated component.

## Dependency direction

```text
Desktop UI
  ├─ Server-Manager
  ├─ Plugin-Manager
  └─ World-Manager presentation/control
          │
          │ explicit local/control contracts only
          ▼
Paper
  ├─ World-Manager
  └─ Utilities-Manager

Fabric client managers
  ├─ Map Manager ── versioned World-Manager protocol / map integration
  ├─ Utility Manager ── independent passive client convenience
  └─ Performance Manager ── independent performance coordination
```

The three Fabric Managers do not import one another's implementation packages by default. World-Manager and Utilities-Manager also do not depend on each other by default.

## Current source layout

```text
EngineData/Frontend/RustApp/   canonical Tauri/Svelte/Rust desktop
modules/world-manager/         Paper World-Manager
modules/utilities-manager/     Paper Utilities-Manager
client/map-manager/            implemented / scope locked Fabric Map Manager
client/utility-manager/        implemented / scope locked Fabric Utility Manager
client/performance-manager/    implemented baseline / scope locked Fabric Performance Manager
docs/                          canonical product/system/operations docs
```

Do not introduce a generic shared implementation tree unless a stable contract is genuinely consumed by multiple runtimes and cannot remain in its existing canonical owner.

## Maintenance rule

When a future feature is proposed, first answer: **which manager owns this outcome?**

- If exactly one manager owns it, implement it there.
- If no existing manager owns it, add a new module only when the responsibility is substantial and durable.
- If multiple managers appear to own it, fix the boundary before implementing the feature.
