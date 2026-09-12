# Manage World Lifecycle

This file is the canonical detailed contract for `Clone`, `Archive / Restore`, and `Delete` inside World Manager. Runtime load state remains owned by `WorldRuntimeService`; filesystem paths remain owned by `WorldFileRepository`; operation conflict exclusion remains owned by `WorldOperationCoordinator`.

## Archive / Restore

Archive is metadata lifecycle, not a filesystem move or ZIP operation.

```text
ACTIVE
→ unload safely
→ Auto Load OFF
→ lifecycle ARCHIVED
```

Restore changes `ARCHIVED → ACTIVE` while keeping the world `UNLOADED` with Auto Load OFF. Teleport may load it later through the normal runtime service.

## Clone

Clone is phased so Paper lifecycle work and heavy filesystem work do not share one execution lane.

```text
prepare (Paper/main)
→ validate ACTIVE source and destination identity
→ acquire CLONE lease
→ remember previous source load state
→ unload source safely for a consistent V1 snapshot

file phase (request worker)
→ sanitized CLONE copy into owned workspace
→ omit session.lock, uid.dat, playerdata, advancements, stats
→ publish destination folder
→ register fresh WorldId
→ initialize destination UNLOADED
→ persist registry

finish (Paper/main)
→ restore source load state when it was previously loaded
→ release lease
```

The clone inherits the source `WorldKind` and Default Game Mode preference but receives a fresh `WorldId` and `autoLoad=false`. The destination is never force-loaded or force-teleported.

V1 deliberately unloads the source while its filesystem snapshot is copied. This favors consistency over uninterrupted access. A future snapshot-capable implementation may shorten that unavailable window without changing the application contract.

## Delete

Delete is permanent and requires an exact typed match of the canonical world folder name.

```text
prepare (Paper/main)
→ acquire DELETE lease
→ safely unload target

file phase (request worker)
→ atomically move owned world folder into owned workspace when supported
→ persist registry without target
→ remove in-memory registry/runtime ownership
→ recursively delete staged workspace

finish (Paper/main)
→ if commit failed and world previously loaded, reload restored target
→ release lease
```

The staged move makes pre-commit deletion reversible. If registry persistence fails before commit, the staged directory is moved back to its canonical world folder and registry ownership remains intact. Registry metadata is removed only after filesystem staging succeeds and durable registry removal succeeds.

If final workspace cleanup fails after registry removal commits, the managed world is still deleted; the remaining workspace is an explicit cleanup failure, not a restored world. No background cleanup daemon is started.

## Execution Rules

- Paper load/unload/player evacuation stays on the primary server thread.
- Copy, recursive cleanup, and destructive filesystem work run only when requested and are intended for a request-scoped worker.
- No permanent clone/delete worker, scheduler, filesystem watcher, or polling loop exists.
- All world/workspace paths are resolved from canonical owned roots; user input is never treated as a recursive-delete path.
- One world can hold only one conflicting operation lease at a time.
