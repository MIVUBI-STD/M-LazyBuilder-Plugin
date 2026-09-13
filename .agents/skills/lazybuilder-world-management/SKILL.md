---
name: lazybuilder-world-management
description: Own World Manager semantics and Paper world behavior: lifecycle, creation/settings, archive/restore/backup/clone/delete, import/export/conversion, registry/persistence, Paper runtime boundaries, and world filesystem safety. Do not use for desktop process/runtime or presentation-only work.
---

# LazyBuilder World Management

Own World Manager domain semantics and Paper world behavior. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

## Owns

```text
create/load/unload/settings and BUILD_READY policy
archive/restore/backup/clone/delete
import/export/conversion
world registry/persistence/runtime state
world operation leases/tasks
Paper/Bukkit world adapters
world filesystem safety/publication
```

## Does Not Own

```text
desktop server/process/provisioning → desktop-runtime
desktop Svelte presentation         → desktop-ui
Fabric/Xaero presentation           → client-ui
neutral shared wire contract        → protocol
third-party plugin lifecycle        → plugin-management
```

If a new world feature needs a shared payload, Protocol defines the payload first; World Management consumes it without duplicating the contract.

## Canonical Context

1. `docs/04-system/development-discipline.md`
2. `docs/02-world-management/README.md`
3. `docs/04-system/skill-routing.md`
4. exact World Manager source/test owner
5. system/ops docs only when boundary or continuation is material

Do not preload desktop/plugin/client internals.

## Procedure

```text
name exact world behavior
→ locate smallest application/domain owner
→ validate lifecycle/state boundary
→ touch Paper adapter only for runtime translation
→ touch persistence/filesystem only when behavior requires it
→ targeted proof
→ STOP
```

## World Invariants

- native Paper/Bukkit APIs preferred; no Multiverse runtime dependency;
- commands, desktop requests, and client requests reach the same application owners;
- destructive/file operations validate lifecycle/state first and remain recoverable;
- unsafe Bukkit/Paper mutation never runs asynchronously;
- heavy file/conversion work stays off Paper main thread after required quiesce/snapshot boundaries;
- registry/runtime/file/task ownership remains singular;
- no generic manager hierarchy or parallel world-operation framework without proven repeated responsibility;
- no NMS unless stable APIs demonstrably cannot satisfy a confirmed requirement.

## Proof Boundary

```text
pure policy/logic        → focused unit test
filesystem/conversion    → local fixture/integration proof
Paper-facing boundaries  → compile/static proof where feasible
actual lifecycle/runtime → LIVE_SERVER
```
