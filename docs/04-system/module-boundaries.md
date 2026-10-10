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

## Verifiable dependency boundaries

The canonical dependency graph above is also a checkable build/source contract:

- Paper `world-manager` and `utilities-manager` depend on `shared/protocol` for neutral messages; neither Paper plugin depends on the other plugin's implementation.
- Fabric `map-manager`, `utility-manager`, and `builder-utilities` may compile only their own sources and the explicitly selected neutral shared-protocol sources. `performance-manager` remains independent.
- Neutral `shared/protocol` must not import Bukkit/Paper, Fabric/Minecraft implementation, Launcher, or component-specific implementation classes.
- Current protocol/source versions and canonical documentation must match. The source-owned World Control and Map Action constants, not copied documentation values, determine compatibility.
- `scripts/verify_compatibility.py` derives Java class ownership from existing source roots and checks direct imports (including static and wildcard imports), Maven/Gradle dependency direction, neutral protocol dependencies, channel wiring, and canonical protocol versions. It is a static guard; reflective or fully qualified references without imports, packaging, and runtime interoperability still require targeted build/live proof.

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

## Engine improvement map (cross-industry learning, not another engine)

This is the existing component map for evaluating external architectures. It is not a new runtime, task queue, status register, permanent research backlog, or permission to implement a feature. Current source and its semantic owner still decide actual behavior; external references only supply candidate techniques.

| Existing owner | End-to-end boundary to inspect | Candidate learning | Do not import |
| --- | --- | --- | --- |
| Desktop Runtime (`apps/launcher/src-tauri/src/engine/`) | workspace identity → Java/Paper admission → process lifecycle → recovery | resource admission, lease/identity safety, explicit failure states | container orchestrator, second server registry, polling controller |
| Plugin Manager (`engine/plugin_manager/`) | inventory → dependency and compatibility decision → install/update/disable → rollback | package-manager transaction and dependency-solving discipline | second Paper plugin authority |
| World Manager (`plugins/world-manager/`) | request → admission/authorization → staged filesystem work → registry commit → recovery/result | SQLite atomic-commit/journal principles and idempotent request handling | SQLite/RocksDB as an automatic second world registry |
| Map Manager (`mods/map-manager/`) | server-authoritative world/map result → client state → viewport/action → matching response | viewport locality and selective invalidation | another source of managed-world truth |
| Utility Manager (`mods/utility-manager/`) | client event → owned preference/session/capture action → presentation | bounded resource lifecycle and useful user feedback | generic event broker |
| Performance Manager (`mods/performance-manager/`) | observe → capability gate → optimized render path → safe fallback → diagnostics | graphics frame budgets, dirty-region updates, profiling discipline | a second renderer/performance manager |
| Builder Utilities (`mods/builder-utilities/`) | Axiom UI → deterministic plan → durable mutation → bounded dispatch → reconciliation/history | data-oriented processing, transaction journals, geometric indexing, functional fields | competing Axiom editor, generic job framework |
| Shared protocol (`shared/protocol/`) | bounded request → server validation/authority → correlated result | idempotent receiver and explicit acknowledgement semantics | transport-specific domain state, generic RPC/event platform |

### Builder Utilities: canonical execution partitions

This describes the currently implemented responsibilities, not a claim that every tool exercises every branch or has passed live runtime proof.

```text
Axiom public client API / CustomTool
  → axiom/                      input, selection, preview and progress presentation
  → operation/                  preflight, lifecycle, seed and cancellation
  → region/                     deterministic chunk-local work partition
  → material/ | spline/ | placement/ | structure/
                                 resolve desired changes without world side effects
  → history/ + mutation/        prepare and durably commit BEFORE/AFTER changes
  → mutation/ + axiom/          bounded application and authoritative result handling
  → mutation/                   reconcile actual state with prepared changes
  → history/                    publish undo/redo or preserve recovery evidence
```

For biome, block-entity and entity changes, `net/` + the neutral Builder Extension wire in `shared/protocol/` communicate with the already-authoritative Paper Utilities Manager adapter; these changes do not bypass server validation. Do not assume a client-side acknowledgement means that server mutation is complete. Desktop World Manager lifecycle/import/export stays a separate ownership path.

- **Single operation owner:** the existing `BuilderRuntime` and operation/mutation controllers coordinate active Builder operations. Tool classes own interaction and tool-specific planning, not independent undo stacks or schedulers.
- **Single durable mutation authority:** reuse `HistoryStorageRouter`, `PreparedMutationSession`, `HistoryTimeline` and recovery/reconciliation paths. No second WAL, transaction coordinator, checkpoint database or automated retry owner.
- **Deterministic planning:** preserve world scope, seed, ordered chunk work, cancellation checks and explicit resource limits. Separate read-only computation from Minecraft/Axiom/Paper state mutation; never move unsafe game API access to worker threads.
- **Budgeted execution:** preserve explicit yield, pressure, cancellation and terminal-result semantics. First measure planner time, dispatch time, main-thread stalls, memory, write amplification and retry/recovery outcomes before changing an execution budget or introducing concurrency.
- **Preview is not authority:** previews may sample or decimate for responsiveness, but actual mutation must use the validated full plan; a preview cache must never become a second persistent world snapshot.
- **Failure is not success:** unknown completion, partial batch response, disconnect and corrupt journal must stay distinguishable. Any automated replay must be justified against the existing authoritative request identity and recovery semantics.

### External techniques worth studying, with adoption limits

| External source / pattern | Specific lesson to compare | Smallest possible adaptation; prerequisite |
| --- | --- | --- |
| [SQLite atomic commit](https://www.sqlite.org/atomiccommit.html) | recoverably distinguish incomplete writes from committed state | inspect existing Builder journal and World Manager import publication ordering; only close a proven durability gap |
| [RocksDB overview](https://github.com/facebook/rocksdb/wiki/RocksDB-Overview) and [write stalls](https://github.com/facebook/rocksdb/wiki/Write-Stalls) | backpressure, write amplification and compaction economics | measure existing history storage and dispatcher counters; do **not** add RocksDB, a background compactor or more storage tiers by default |
| [Blender Geometry Nodes fields](https://docs.blender.org/manual/en/latest/modeling/geometry_nodes/fields.html) | function/field evaluation depends on its context | improve the existing `ScalarField` / `BuilderMaterial` composition if a concrete procedural output is missing; no generic node-editor graph |
| [Apache Arrow memory layout](https://arrow.apache.org/docs/format/Columnar.html) | contiguous scan-friendly data representation | profile existing chunk ChangeSet/codec allocation and iteration before considering a localized layout change; do not replace the file/wire format |
| [JTS STRtree](https://github.com/locationtech/jts/blob/master/modules/core/src/main/java/org/locationtech/jts/index/strtree/STRtree.java) | spatial pruning for footprint-overlap queries | evaluate `StructurePlacementPlanner` only if collision checks dominate large placements; a local spatial grid may be sufficient; no new geometry dependency by default |
| [Kubernetes controller model](https://kubernetes.io/docs/concepts/architecture/controller/) | reconcile intended versus observed state at one authority | use the existing mutation and lifecycle reconciliation points; no persistent control loop, watcher or second system-state database |
| [OpenUSD](https://github.com/PixarAnimationStudios/OpenUSD) / scene change tracking | dirty-only updates and separation of data from presentation | examine existing Axiom preview and Map Manager caches before proposing scoped invalidation; no USD scene graph or second rendering engine |
| [Idempotent Receiver](https://martinfowler.com/articles/patterns-of-distributed-systems/idempotent-receiver.html) | repeated requests should not duplicate effects | apply only to a demonstrated Paper/Client request-result ambiguity at its existing protocol and semantic owner; no global request registry |
| [Rayon parallel iterators](https://docs.rs/rayon/latest/rayon/iter/trait.ParallelIterator.html) | partition immutable work before ordered aggregation | conceptual reference for pure planning only; no Rust/Rayon dependency or unsafely parallel Minecraft mutation |

These references are research evidence, not version-compatible implementations, permanent dependencies or proof of superiority. Learn the mechanism rather than porting a framework. Check licenses and version/API contracts before considering source reuse.

### Promotion gate for any engine optimization

1. **Name one first-wrong owner and a real outcome.** Record the current implementation path and the exact failure, missing capability or measured hotspot; preserve `No change required` as a legitimate result.
2. **Compare with the existing path, not an invented baseline.** Characterize deterministic output, mutation correctness, p95 frame/slice pressure, allocation, working-set bytes, disk writes, rollback/recovery and user-visible errors only where applicable. A synthetic dispatcher benchmark is not Minecraft/Axiom runtime evidence.
3. **Choose the least new ownership:** eliminate duplication → improve the existing function/algorithm → use a stable existing API/dependency → add a narrow adapter only if necessity is proven. Never introduce a new Manager, scheduler, durable state or wire version solely to imitate an external engine.
4. **Keep semantic invariants:** Axiom retains primary editor authority, Paper remains authoritative for server-side effects, Builder History precedes world mutation, world identity never crosses scopes, request results are correlated and renderer fallbacks remain reversible.
5. **Change one owner and its direct contract consumers.** Use scoped code and matching proof; preserve source/CI/live-runtime proof ceilings. No test-harness or CI expansion merely to justify speculative optimization; finish with the canonical STOP rule.

For the existing Builder foundation, prioritize *investigating* whether durable history, bounded dispatch, and mixed-extension reconciliation share consistent completion semantics before pursuing algorithms that merely promise higher throughput. Any actual fix remains conditional on source evidence and an authorized development scope.

## Maintenance rule

When a future feature is proposed, first answer: **which component owns this outcome?**

- one clear owner → implement there;
- Axiom already owns the workflow well → keep it in Axiom;
- Builder Utilities adds a narrow missing capability without replacing Axiom → implement in Builder Utilities;
- no current owner → add a new runtime/module only when the responsibility is substantial and durable;
- multiple apparent owners → repair the boundary before implementation;
- A proposed builder subsystem overlaps Builder Utilities → keep Builder Utilities authoritative unless a distinct non-overlapping responsibility is proven.
