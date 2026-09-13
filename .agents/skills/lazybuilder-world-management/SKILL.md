---
name: lazybuilder-world-management
description: Specialist for World Manager implementation and audits: world lifecycle, creation/settings, archive/restore/backup/clone/delete, import/export/conversion, Paper runtime boundaries, and world filesystem safety. Use when World Manager behavior is the primary change.
---

# LazyBuilder World Management

Own World Manager semantics and Paper world behavior. Desktop/runtime orchestration and client presentation remain separate owners.

## Use This Owner For

- create/load/unload/settings and BUILD_READY policy;
- archive/restore/backup/clone/delete;
- import/export/conversion and world-file safety;
- World Manager tasks, registry, persistence, operation leases;
- Paper/Bukkit world runtime adapters.

Route elsewhere when the primary owner is:

```text
desktop server/process/provisioning → lazybuilder-desktop-runtime
desktop Svelte presentation         → lazybuilder-desktop-ui
Fabric/Xaero client presentation    → lazybuilder-client-ui
shared wire contract                → lazybuilder-protocol
third-party plugin lifecycle        → lazybuilder-plugin-management
```

## Canonical Context

1. `docs/02-world-management/README.md`
2. `docs/04-system/skill-routing.md`
3. exact current World Manager source/test owner
4. `docs/04-system/README.md` only when module/boundary decisions are affected
5. `docs/05-operations/` only when current continuation/proof is material

Do not preload unrelated desktop/plugin/client context.

## Implementation Order

```text
contract
→ smallest domain/application owner
→ Paper adapter only when runtime translation is needed
→ persistence/filesystem owner only when required
→ adapter surface
→ targeted proof
```

## Rules

- Native Paper/Bukkit APIs are preferred; no Multiverse runtime dependency.
- Commands, desktop requests, and client requests must reach the same application owners.
- Validate lifecycle/state before destructive filesystem operations.
- Never perform unsafe Bukkit/Paper mutation asynchronously.
- Heavy file/conversion work stays off the Paper main thread after required quiesce/snapshot boundaries.
- No NMS unless stable APIs demonstrably cannot satisfy a confirmed requirement.
- Keep registry, runtime state, file publication, and task ownership singular.
- Do not invent generic manager hierarchies or parallel world-operation frameworks.

## Proof

```text
pure policy/logic        → unit tests
filesystem/conversion    → local fixture/integration proof
Paper-facing boundaries  → compile/static proof where feasible
actual lifecycle/runtime → LIVE_SERVER
```

Stop once the requested world behavior and its relevant proof are complete.
