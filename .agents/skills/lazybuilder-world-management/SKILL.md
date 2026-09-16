---
name: lazybuilder-world-management
description: Own World Manager semantics and Paper world behavior: ACTIVE/ARCHIVED lifecycle, automatic runtime loading, creation/settings, archive/restore/backup/duplicate/delete, import/export/conversion, registry/persistence, Paper runtime boundaries, and world filesystem safety. Do not use for desktop process/runtime or presentation-only work.
---

# LazyBuilder World Management

Own World Manager domain semantics and Paper world behavior. Global diagnosis/proof rules come from `docs/04-system/development-discipline.md`; durable world behavior comes from `docs/02-world-management/README.md`; cross-owner handoff comes from `docs/04-system/skill-routing.md`.

## Entry gate

Use this Skill only when the decision changes world-domain truth, lifecycle, filesystem publication, conversion, or Paper-facing world behavior.

Do not enter because a Launcher/Fabric surface displays worlds.

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

Does not own desktop server/process/provisioning, presentation, neutral shared wire contracts, or third-party plugin lifecycle.

When a new/changed world capability requires a neutral Paper↔Fabric payload, Protocol defines that payload first.

## Canonical lifecycle

Persistent lifecycle is only:

```text
ACTIVE
ARCHIVED
```

Loaded/unloaded/loading/unloading are transient Paper runtime facts.

Do not recreate persisted runtime-state registry, manual Load/Unload product actions, per-world `autoLoad`, or Clone terminology/service.

Runtime policy:

```text
required use → load automatically
empty + idle + no conflicting operation → unload automatically
```

Pinned/Recent are client preferences. User-facing copy uses **Duplicate**.

## Import / export / conversion

One capability family with distinct existing service owners.

- native Java 1.21.4 uses the fast path;
- converter implementation stays internal;
- export formats come only from verified runtime capabilities;
- Map Export Area is transient request context, never world metadata;
- file/conversion work is request-bound and bounded;
- one active conversion lease remains canonical;
- no second converter manager/export service.

Import review ownership:

```text
completed upload
→ InspectImport claims artifact for bounded review
→ review metadata only; no publication
→ explicit Import revalidates/publishes
   OR DiscardImport releases/deletes reviewed artifact
```

A completed upload/review artifact always has one owner; no orphan inbox or polling cleanup daemon.

## Failure taxonomy

```text
LIFECYCLE      ACTIVE/ARCHIVED rule wrong
REGISTRY       persisted identity/metadata stale/inconsistent
RUNTIME_LOAD   Paper load/unload/teleport preparation wrong
CONCURRENCY    lease/task overlap or stranded state
FILESYSTEM     path/snapshot/stage/publish/archive/restore/delete safety wrong
TRANSFER       import/export request lifecycle/file handling wrong
IMPORT_REVIEW  inspect/review/discard ownership/cleanup wrong
CONVERSION     capability/acquisition/result/publication/rollback wrong
PROTOCOL       neutral contract is first wrong owner
PRESENTATION   canonical world result correct; UI/client state wrong
ENVIRONMENT    Paper/filesystem/runtime environment blocks correct source
UNKNOWN        next separating evidence required
```

Cross-owner labels use the global bridge in `development-discipline.md`.

## Operation contract

Filesystem-affecting world mutations use one semantic transaction owner:

```text
PRECHECK
→ lifecycle / permission / path / player-presence / lease validation

QUIESCE or SNAPSHOT
→ only when consistent filesystem view is required

STAGE
→ heavy file/conversion work off Paper main thread where safe

VALIDATE
→ structure/integrity/capability checks

PUBLISH
→ one authoritative filesystem publication boundary

COMMIT
→ registry/lifecycle agrees with publication

or RECOVER
→ preserve/restore previous-valid state + release lease deterministically
```

Retry cannot duplicate a committed copy/archive/import/export side effect.

## Safety invariants

- prefer stable Paper/Bukkit APIs; no Multiverse runtime dependency;
- commands, desktop requests, and Fabric requests delegate to the same application owners;
- destructive/file operations validate lifecycle + operation ownership first;
- Bukkit/Paper mutation never runs async unless API contract permits it;
- heavy file/conversion work leaves main thread after required quiesce/snapshot;
- consistent-snapshot operations do not silently eject builders;
- fallback/default world is protected from unsafe unload/delete;
- registry/runtime/file/task ownership remains singular;
- one bounded task/lease owner prevents conflicting work;
- filesystem publication and registry mutation share one completion boundary;
- interrupted destructive/replacing publication retains enough previous-valid state for deterministic recovery;
- no generic manager hierarchy, second operation framework, watcher, polling cleanup daemon, or NMS without proven need.

## Canonical procedure

```text
name exact world behavior
→ capture lifecycle + registry + relevant runtime/filesystem evidence
→ classify local subtype
→ confirm World Management remains first wrong owner
→ separate durable lifecycle from transient runtime facts
→ resolve neutral protocol first when needed
→ touch Paper adapter only for Paper translation
→ touch persistence/filesystem only when behavior requires it
→ preserve one transaction/lease/execution path
→ smallest complete recoverable change
→ matching proof
→ typed handoff if ownership changes
→ STOP
```

## Proof matrix

```text
lifecycle / policy / validation rules             → EXECUTED_SOURCE
pure registry/path/transaction decisions          → EXECUTED_SOURCE
real filesystem/service transaction behavior      → INTEGRATION_FIXTURE
archive/restore/import/export/conversion publish   → INTEGRATION_FIXTURE
Paper adapter compile/contract                     → EXECUTED_SOURCE
real load/unload/teleport/player/thread behavior   → LIVE_RUNTIME
presentation-only state                            → lazybuilder-ui
```

A filesystem fixture does not prove Bukkit/Paper lifecycle behavior. Paper boot is not `LIVE_RUNTIME` proof unless the changed world path is exercised. A screenshot proves presentation, not domain mutation/publication.

## Handoff / exit

Use canonical typed handoffs from `skill-routing.md`.

```text
to protocol
→ domain intent + authoritative constraints + required result semantics

to ui
→ canonical world id + ACTIVE/ARCHIVED + capabilities + presentation metadata + stable result/error

to desktop-runtime
→ canonical workspace/server identity + exact process/provisioning symptom
```

UI must not infer lifecycle from folders, registry internals, transient Paper load state, or converter implementation. Desktop Runtime must not reinterpret world lifecycle/filesystem semantics.

Finish when durable lifecycle remains ACTIVE/ARCHIVED, registry/filesystem/runtime/task ownership is singular, destructive/import/conversion work has bounded recoverability, and matching proof covers the changed claim. Stop before UI work, generic framework work, or unrelated world cleanup.