# LazyBuilder World Management Skill

Use for implementation or audit work whose semantic owner is World Manager.

## Canonical context

1. `docs/02-world-management/README.md`
2. `docs/04-system/README.md` only when module/boundary decisions are affected
3. exact current world-management source/test owners
4. `docs/05-operations/README.md` only for current continuation/proof

Do not preload unrelated plugin-stack or client UI context.

## Implementation order

```text
contract
→ smallest domain/application owner
→ Paper adapter
→ persistence only if required by the contract
→ adapter surface (command/protocol/UI request)
→ targeted proof
```

## Rules

- Native Paper/Bukkit path is preferred; Multiverse is not the target runtime dependency.
- New worlds must apply the approved `BUILD_READY` profile through one canonical policy owner.
- Commands and client requests must delegate to the same world use cases.
- Validate world state before filesystem/destructive operations.
- Never perform unsafe Bukkit/Paper state mutation asynchronously.
- File-heavy operations should keep main-thread work minimal while respecting save/unload lifecycle requirements.
- No NMS unless stable APIs demonstrably cannot satisfy a confirmed requirement.
- No generic manager hierarchy; add only responsibilities required by current behavior.

## Proof

```text
pure policy/logic        → unit tests
Paper-facing boundaries  → compile/integration/static proof where feasible
world lifecycle runtime  → LIVE_SERVER
filesystem export/import → local/runtime fixture + LIVE_SERVER final proof when world state is involved
```

Stop once the requested world behavior and its relevant proof are complete.
