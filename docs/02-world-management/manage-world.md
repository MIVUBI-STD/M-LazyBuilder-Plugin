# Manage World Lifecycle

Detailed World Manager contract for **Duplicate**, **Archive / Restore**, and **Delete**. The canonical product model and safety rules are in [World Management](README.md). Paper runtime loading is owned by `WorldRuntimeService`; paths and staging by `WorldFileRepository`; operation conflicts by `WorldOperationCoordinator`.

## Product goal and scope (WM-01)

World Manager is a builder-facing workspace for safely organizing, entering, preserving, duplicating and transferring managed Minecraft worlds. It is **not** a generic server administration panel, world editor, gameplay engine or replacement for Axiom/WorldEdit.

- Builder decisions are displayed as actions on worlds; backend load/unload, folder identity, operation leases and conversion worker details remain internal.
- Persistent lifecycle is only `ACTIVE` and `ARCHIVED`. Paper-loaded state is transient, never persisted as `autoLoad`.
- Imported worlds keep their validated gameplay/settings where supported. The BUILD_READY profile applies to newly created worlds or explicit Reset Builder Defaults only.
- Destructive/file operations must fail closed on active players, unsafe paths, ownership ambiguity, or conflicting world operations; do not silently evacuate players.
- Source/Paper/Fabric/Desktop and runtime acceptance remain separate proof layers. Source implementation alone is not a release gate.

## Archive / Restore

Archive changes durable lifecycle metadata; it does not move the world into a ZIP/archive folder. Builders inside a world block archive. The active fallback/default world cannot be archived.

```text
ACTIVE
→ acquire ARCHIVE operation lease
→ verify no players are inside; unload safely when loaded
→ persist ARCHIVED
→ omit archived worlds from normal daily list

ARCHIVED
→ acquire RESTORE operation lease
→ persist ACTIVE
→ remain eligible for automatic loading on later Teleport
```

When registry persistence fails after archive's runtime unload, reload an originally loaded world where safe. There is no user-facing `Auto Load`, `Load` or `Unload` world operation in the normal builder workflow.

## Duplicate

The user-facing and backend operation is **Duplicate**, not Clone. A duplicate has a fresh `WorldId`, retains the source `WorldKind` and durable default-game-mode preference, and does not inherit user-scoped Pinned/Recent navigation preferences.

```text
prepare (Paper main thread)
→ require ACTIVE source with no builders inside
→ reserve destination folder and acquire DUPLICATE lease
→ remember source loaded state; unload for consistent snapshot

file phase (request-scoped worker)
→ stage sanitized DUPLICATE copy of source plus managed dimensions
→ exclude session.lock, uid.dat, playerdata, advancements, stats
→ publish independent destination folder and fresh WorldId
→ persist registry with ACTIVE lifecycle

finish (Paper main thread)
→ restore previous loaded source when needed
→ release source operation lease and destination reservation
```

The destination is not automatically loaded or teleported into; its loaded/unloaded condition is Paper runtime truth, not persistent world metadata. If publication/registry commit fails, filesystem and metadata rollback follow the existing transaction/restart-recovery path.

## Delete

Delete requires an exact typed match of the **visible world display name** (not internal folder name), together with immutable `WorldId` authorization. It rejects fallback/default world deletion and a world occupied by builders.

```text
prepare (Paper main thread)
→ validate confirmation and protection/player guards
→ acquire DELETE lease
→ remember loaded state and unload safely

file phase (request worker)
→ stage owned world family in a reversible delete workspace
→ remove durable registry entry using WorldRegistryTransactions
→ delete staged data only after durable removal commits

finish (Paper main thread)
→ if uncommitted and originally loaded, attempt source runtime restore
→ release operation lease
```

A pre-commit failure should restore the staged world and previous registry state. A post-commit cleanup error **does not resurrect** the deleted world; cleanup debt is retained for retry/restart reconciliation. World-folder names are internal identifiers, not user-visible confirmation tokens.

## Execution and proof rules

- Paper/Bukkit world and player operations run on the main thread; heavy copy/extraction/recursive cleanup run only as scoped worker operations.
- Operations affecting one world are mutually exclusive through the existing canonical lease owner. Do not add a second world registry, watcher or scheduler.
- Source may establish the above contracts, but real snapshot consistency, world families, interrupted delete/duplicate, player presence and restart reconciliation still require matching executable/integration/runtime evidence.
