---
name: lazybuilder-world-management
description: Own World Manager semantics and Paper world behavior: ACTIVE/ARCHIVED lifecycle, automatic runtime loading, creation/settings, archive/restore/backup/duplicate/delete, import/export/conversion, registry/persistence, Paper runtime boundaries, and world filesystem safety. Do not use for desktop process/runtime or presentation-only work.
---

# LazyBuilder World Management

Own World Manager domain semantics and Paper world behavior. Follow `docs/04-system/development-discipline.md` and `docs/02-world-management/README.md`.

## Entry gate

Use this Skill only when the decision changes world-domain truth, world lifecycle, world filesystem publication, conversion, or Paper-facing world behavior.

Do not enter because a Launcher/Fabric surface displays worlds. Presentation stays with `lazybuilder-ui`; desktop server/process ownership stays with `lazybuilder-desktop-runtime`.

Before mutation, separate durable lifecycle from transient runtime/operation state using the smallest authoritative evidence.

## Owns

```text
create/settings + BUILD_READY policy
ACTIVE / ARCHIVED lifecycle
automatic runtime load / idle unload coordination
teleport runtime preparation
archive/restore/backup/duplicate/delete
import/export/conversion
import inspection/review/discard domain semantics
world registry/persistence
world operation leases/tasks
Paper/Bukkit world adapters
world filesystem safety/publication
```

## Does not own

```text
desktop server/process/provisioning → lazybuilder-desktop-runtime
Desktop/Fabric presentation         → lazybuilder-ui
neutral shared wire contract        → lazybuilder-protocol
third-party plugin lifecycle        → lazybuilder-plugin-management
```

When a new/changed world feature requires a neutral Paper↔Fabric payload, `lazybuilder-protocol` defines that payload first.

## Canonical lifecycle

Persistent product lifecycle is only:

```text
ACTIVE
ARCHIVED
```

Loaded/unloaded/loading/unloading are Paper runtime facts, not durable product states.

Do not recreate:

```text
persisted runtime-state registry
manual Load/Unload product actions
per-world autoLoad metadata
Clone terminology / Clone service
```

Runtime policy:

```text
required use
→ load automatically

empty + idle + no conflicting operation
→ unload automatically
```

Pinned/Recent remain client navigation preferences. User-facing copy uses **Duplicate**, not Clone.

## Import / export / conversion model

These are one World Manager capability family with distinct existing service owners.

Rules:

- native Java 1.21.4 uses the fast path;
- converter implementation remains internal;
- export target formats come only from verified runtime capabilities;
- Map Export Area is transient request context, never persisted world metadata;
- file/conversion work is request-bound and bounded;
- one active conversion lease remains canonical;
- do not add a second converter manager/export service.

Import review ownership is explicit:

```text
completed upload
→ InspectImport claims artifact for bounded review
→ review metadata returned without publishing a world
→ explicit Import revalidates and publishes
   OR DiscardImport releases/deletes the reviewed artifact
```

A completed upload/review artifact must always have one owner; do not create an orphan inbox registry or polling cleanup daemon.

## Failure taxonomy

```text
LIFECYCLE       durable ACTIVE/ARCHIVED rule is wrong
REGISTRY        persisted identity/metadata is stale or inconsistent
RUNTIME_LOAD    Paper load/unload/teleport preparation is wrong
CONCURRENCY     conflicting lease/task overlaps or strands state
FILESYSTEM      path/snapshot/staging/publish/archive/restore/delete safety is wrong
TRANSFER        import/export request lifecycle or bounded file handling is wrong
IMPORT_REVIEW   inspect/review/discard ownership or cleanup is wrong
CONVERSION      capability/acquisition/result/publication/rollback is wrong
PROTOCOL        neutral request/result shape is the first wrong owner
PRESENTATION    canonical world result is correct; UI/client state is wrong
ENVIRONMENT     Paper/filesystem/runtime environment blocks correct behavior
UNKNOWN         evidence cannot separate the above
```

Local labels refine global classification only while World Management remains the first wrong owner. `PROTOCOL` reclassifies to global `PROTOCOL`; `PRESENTATION` to `UI_PRESENTATION`; `ENVIRONMENT` to the narrowest of `INSTALL_ENVIRONMENT` or `PAPER_RUNTIME`; filesystem/rollback failure may additionally require global `RECOVERY`; `UNKNOWN` must name the next separating evidence.

For `PROTOCOL`, stop domain editing until `lazybuilder-protocol` owns the neutral fix. For `PRESENTATION`, hand only canonical result/state to `lazybuilder-ui`.

## Operation contract

World mutations with filesystem consequences follow one semantic transaction owner:

```text
PRECHECK
→ lifecycle / permission / path / player-presence / lease validation

QUIESCE or SNAPSHOT
→ only when a consistent filesystem view is required

STAGE
→ perform heavy file/conversion work off Paper main thread where safe

VALIDATE
→ output structure/integrity/capability checks

PUBLISH
→ one authoritative filesystem publication boundary

COMMIT
→ registry/lifecycle state agrees with publication

or RECOVER
→ preserve/restore previous-valid state and release lease deterministically
```

Retry must not duplicate a committed copy/archive/import/export side effect.

## Safety invariants

- prefer stable Paper/Bukkit APIs; no Multiverse runtime dependency;
- commands, desktop requests, and Fabric requests delegate to the same application owners;
- destructive/file operations validate lifecycle and operation ownership before mutation;
- Bukkit/Paper state mutation never runs asynchronously unless the API contract permits it;
- heavy file/conversion work leaves the main thread after required quiesce/snapshot boundaries;
- consistent-snapshot operations do not silently eject builders; block while builders remain unless product policy explicitly says otherwise;
- fallback/default world is protected from unsafe unload/delete;
- registry/runtime/file/task ownership remains singular;
- one bounded task/lease owner prevents conflicting world/conversion work;
- filesystem publication and registry mutation share one completion boundary;
- interrupted destructive/replacing publication retains enough previous-valid state for deterministic recovery;
- no generic manager hierarchy, second operation framework, background watcher, or polling cleanup daemon without a proven repeated requirement;
- no NMS unless stable APIs demonstrably cannot satisfy a confirmed requirement.

## Canonical procedure

```text
name exact world behavior
→ capture lifecycle + registry + relevant runtime/filesystem evidence
→ classify failure
→ find smallest domain/application owner
→ separate ACTIVE/ARCHIVED from transient runtime/operation facts
→ resolve neutral protocol first when it is the wrong owner
→ touch Paper adapter only for Paper translation
→ touch persistence/filesystem only when behavior requires it
→ preserve one transaction/lease/execution path
→ make smallest complete recoverable change
→ prove the changed policy/filesystem/runtime claim at matching level
→ hand presentation residue to lazybuilder-ui
→ STOP
```

## Proof matrix

Use the canonical proof vocabulary from `development-discipline.md`.

```text
lifecycle / policy / validation rules
→ EXECUTED_SOURCE

registry serialization / path guards / transaction decisions
→ EXECUTED_SOURCE when pure
→ INTEGRATION_FIXTURE when real filesystem/service interaction is required

archive/restore/import/export/conversion staging + publication
→ INTEGRATION_FIXTURE with representative world data

Paper adapter compile/contract
→ EXECUTED_SOURCE

actual load/unload/teleport/player-presence/thread/runtime behavior
→ LIVE_RUNTIME using exact built artifacts and the changed world path

Launcher/Fabric presentation only
→ lazybuilder-ui proof lane
```

A filesystem fixture does not prove Bukkit/Paper lifecycle behavior. A Paper boot that does not exercise the changed path is not `LIVE_RUNTIME` proof for the feature. A Minecraft screenshot can prove presentation, not world-domain mutation or filesystem publication.

## Handoff / exit contract

```text
new/changed neutral Paper↔Fabric payload
→ lazybuilder-protocol
handoff: domain requirement only (intent, authoritative constraints, required result semantics)
Protocol freezes neutral types/version/defaults/bounds; World Management then consumes that frozen contract

canonical world result/state
→ lazybuilder-ui
handoff: canonical world id + ACTIVE/ARCHIVED + capabilities + presentation metadata + stable operation result/error
UI must not infer lifecycle from folders, registry internals, transient Paper load state, or converter implementation

desktop Paper process/provisioning issue
→ lazybuilder-desktop-runtime
handoff: canonical workspace/server identity + exact process/provisioning symptom
Desktop Runtime owns process/provisioning; it must not reinterpret world lifecycle/filesystem semantics
```

Finish when:

- durable lifecycle remains only ACTIVE/ARCHIVED;
- registry/filesystem/runtime/task ownership remains singular;
- destructive/import/conversion work has bounded recoverability;
- matching proof is complete at the available context ceiling;
- next owner can proceed from the typed handoff without reopening World Management truth;
- remaining live-server/presentation residue is named precisely.

Do not preserve stale continuity claims against current source/proof, and do not continue into UI, generic framework, or unrelated world cleanup after the domain contract is satisfied.