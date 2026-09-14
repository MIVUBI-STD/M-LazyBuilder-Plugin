---
name: lazybuilder-world-management
description: Own World Manager semantics and Paper world behavior: ACTIVE/ARCHIVED lifecycle, automatic runtime loading, creation/settings, archive/restore/backup/duplicate/delete, import/export/conversion, registry/persistence, Paper runtime boundaries, and world filesystem safety. Do not use for desktop process/runtime or presentation-only work.
---

# LazyBuilder World Management

Own World Manager domain semantics and Paper world behavior. Follow `docs/04-system/development-discipline.md`, `docs/02-world-management/README.md`, and `CONTEXT.md`.

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
- no NMS unless stable APIs demonstrably cannot satisfy a confirmed requirement.

## Procedure

```text
name exact world behavior
→ locate smallest application/domain owner
→ confirm ACTIVE/ARCHIVED vs transient operation/runtime fact
→ touch Paper adapter only for runtime translation
→ touch persistence/filesystem only when behavior requires it
→ preserve one execution path
→ targeted proof
→ STOP
```

## Proof boundary

```text
pure policy/logic        → focused unit test
filesystem/conversion    → local fixture/integration proof
Paper-facing boundaries  → compile/static proof where feasible
actual lifecycle/runtime → LIVE_SERVER
```

Current `Local` World Manager implementation is ahead of fresh proof; do not claim compile/live validation until the final validation pass has run.
