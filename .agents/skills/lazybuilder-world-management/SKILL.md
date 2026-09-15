---
name: lazybuilder-world-management
description: Own World Manager semantics and Paper world behavior: ACTIVE/ARCHIVED lifecycle, automatic runtime loading, creation/settings, archive/restore/backup/duplicate/delete, import/export/conversion, registry/persistence, Paper runtime boundaries, and world filesystem safety. Do not use for desktop process/runtime or presentation-only work.
---

# LazyBuilder World Management

Own World Manager domain semantics and Paper world behavior. Follow `docs/04-system/development-discipline.md`, `docs/02-world-management/README.md`, and `CONTEXT.md`.

## Entry gate

Use this Skill when the decision changes world-domain truth, world lifecycle, world filesystem publication, conversion, or Paper-facing world behavior.

Do not enter merely because a Launcher/Fabric surface shows worlds. Presentation stays with `lazybuilder-ui`; desktop server/process ownership stays with `lazybuilder-desktop-runtime`.

Before mutation, identify the smallest evidence that separates durable lifecycle from transient runtime/operation state.

## Owns

```text
create/settings and BUILD_READY policy
ACTIVE / ARCHIVED lifecycle
automatic runtime load / idle unload coordination
teleport runtime preparation
archive/restore/backup/duplicate/delete
import/export/conversion
world registry/persistence
world operation leases/tasks
Paper/Bukkit world adapters
world filesystem safety/publication
```

## Does Not Own

```text
desktop server/process/provisioning → lazybuilder-desktop-runtime
Desktop or Fabric presentation      → lazybuilder-ui
neutral shared wire contract        → lazybuilder-protocol
third-party plugin lifecycle        → lazybuilder-plugin-management
```

If a new world feature needs a shared payload, `lazybuilder-protocol` defines that payload first. World Management consumes it without duplicating the contract.

## Canonical model

Persistent product lifecycle is only:

```text
ACTIVE
ARCHIVED
```

Loaded/unloaded/loading/unloading are Paper runtime facts, not durable lifecycle values. Do not recreate `WorldRuntimeStateRegistry`, persisted runtime state, manual Load/Unload UI contracts, or per-world `autoLoad` metadata.

Runtime policy:

```text
required use
→ load automatically

empty + idle + no conflicting operation
→ unload automatically
```

Pinned/Recent are client navigation preferences and do not belong here.

User-facing copy uses **Duplicate**, not Clone. Do not reintroduce `WorldCloneService`, Clone protocol actions, or Clone UI terminology.

## Import / Export

Import, whole-world Export, Map Export Area, conversion, and format capability discovery are one World Manager capability family with distinct existing service owners.

Rules:

- native Java 1.21.4 keeps the fast path;
- converter/Chunker implementation stays internal;
- additional target formats come only from a verified current runtime catalog;
- Map selected area is transient request context, never world metadata or a saved preset;
- file/conversion work is request-bound;
- one active conversion lease remains canonical;
- do not invent a second converter manager or export service.

## Failure patterns

Classify before editing:

```text
LIFECYCLE       durable ACTIVE/ARCHIVED rule is wrong
REGISTRY        persisted world identity/metadata is stale or inconsistent
RUNTIME_LOAD    load/unload/teleport preparation fails at Paper boundary
CONCURRENCY     conflicting lease/task can overlap or strand state
FILESYSTEM      path, snapshot, staging, publish, archive, restore, or delete safety fails
TRANSFER        import/export request lifecycle or bounded file handling fails
CONVERSION      converter capability/result/publication fails
PROTOCOL        shared request/result shape is the first wrong owner
PRESENTATION    canonical world result is correct; UI/client state is wrong
ENVIRONMENT     Paper/filesystem/runtime environment blocks otherwise-correct behavior
UNKNOWN         evidence cannot yet separate the above
```

For `PROTOCOL`, stop domain editing until the neutral contract is fixed by `lazybuilder-protocol`. For `PRESENTATION`, hand only canonical world state/result to `lazybuilder-ui`.

## Safety invariants

- native Paper/Bukkit APIs preferred; no Multiverse runtime dependency;
- commands, desktop requests, and client requests reach the same application owners;
- destructive/file operations validate lifecycle and operation ownership first;
- unsafe Bukkit/Paper mutation never runs asynchronously;
- heavy file/conversion work stays off Paper main thread after required quiesce/snapshot boundaries;
- operations requiring a consistent filesystem snapshot must not silently eject builders; block while builders remain inside unless a separately approved product rule says otherwise;
- fallback/default world remains protected from unsafe unload/delete behavior;
- registry/runtime/file/task ownership remains singular;
- bounded task execution and conversion leases are retained where they prevent main-thread work or concurrency conflicts;
- no generic manager hierarchy or parallel operation framework without proven repeated responsibility;
- no NMS unless stable APIs demonstrably cannot satisfy a confirmed requirement;
- committed filesystem publication and registry mutation must agree on one authoritative completion boundary;
- retry must not duplicate a completed copy/archive/import/export side effect;
- interrupted replacement/publication keeps enough previous-valid state for deterministic recovery when the operation is destructive or replacing.

## Procedure

```text
name exact world behavior
→ capture current lifecycle + registry + relevant operation/runtime evidence
→ classify failure
→ locate smallest application/domain owner
→ confirm ACTIVE/ARCHIVED vs transient operation/runtime fact
→ resolve neutral protocol first when it is the wrong owner
→ touch Paper adapter only for runtime translation
→ touch persistence/filesystem only when behavior requires it
→ preserve one execution path and one lease/transaction owner
→ make smallest complete change
→ prove policy/filesystem/runtime claim at the matching level
→ hand presentation-only residue to lazybuilder-ui
→ STOP
```

## Proof matrix

```text
pure lifecycle/policy/validation
→ focused unit test

registry serialization / path guards / transaction decisions
→ focused source or filesystem fixture

archive/restore/import/export/conversion publication
→ local integration fixture with representative world data

Paper adapter compile/contract
→ build/CI proof

actual load/unload/teleport/player-presence/thread/runtime behavior
→ LIVE_SERVER using the exact built artifact

Fabric/Launcher presentation only
→ lazybuilder-ui proof lane
```

A filesystem fixture does not prove Bukkit/Paper lifecycle behavior. A live Paper boot without exercising the changed world path does not prove the feature.

## Handoff / exit contract

Sequential ownership only:

```text
new/changed neutral Paper↔Fabric payload
→ lazybuilder-protocol
→ world-management consumes it

canonical world result/state
→ lazybuilder-ui presents it

desktop Paper process/provisioning problem
→ lazybuilder-desktop-runtime
```

Finish when:

- durable lifecycle remains only ACTIVE/ARCHIVED;
- registry/filesystem/runtime responsibilities remain singular;
- destructive and conversion work is recoverable/bounded where required;
- matching proof is complete at the available context ceiling;
- remaining live-server or presentation residue is stated precisely.

Current `Local` source remains implementation truth; do not preserve stale continuity claims against fresh CI/live evidence.
