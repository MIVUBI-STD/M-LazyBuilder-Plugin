# System / Ownership

Canonical owner for LazyBuilder module boundaries, source ownership, and maintainability rules.

## Target Runtime

```text
LazyBuilder Client Mod
        │
        │ bounded protocol
        ▼
LazyBuilder Server Plugin
        │
        ▼
Paper API 1.21.4
```

Xaero World Map is an external client integration for map preview/location interaction, not a world-state authority.

## Ownership Rules

```text
Client UI          → presentation/input only
Protocol           → typed bounded requests/results
World application  → lifecycle/use-case policy
World registry     → LazyBuilder-owned world metadata
Paper adapter      → Bukkit/Paper calls and runtime translation
File transfer      → import/export/clone filesystem safety
Conversion adapter → external converter process/runtime details only
```

Commands and UI adapters delegate to the same application owners. Do not create separate command logic and UI logic for the same operation.

The external converter is an implementation detail inside World Manager. Import, export, multi-version conversion, runtime updates, and file management remain one product domain and must not grow into parallel product surfaces.

## Source Shape

Keep implementation shallow until real complexity requires more structure. Prefer responsibility-based packages rather than generic manager hierarchies.

Expected direction:

```text
world/
├── application/     world use cases and policy
├── registry/        persistent LazyBuilder world identity/metadata
├── paper/           Paper/Bukkit runtime boundary
├── files/           safe world-file operations
├── conversion/      isolated conversion adapter/runtime lifecycle
└── protocol/        typed client/server contracts when client work begins
```

A package exists only when its responsibility exists in source. Do not create empty layers for symmetry.

Avoid manager proliferation such as `WorldController`, `WorldCoordinator`, `WorldRuntimeManager`, and `WorldLifecycleManager` unless distinct responsibilities are demonstrated.

## Maintainability Contract

Every durable feature should have one canonical semantic owner and one primary runtime path.

```text
requirement / policy → canonical docs
business behavior    → application/domain source
Paper behavior       → Paper adapter
external integration → one dedicated adapter
proof                 → nearest targeted test / CI / runtime check
history               → Git history
```

Rules:

- prefer deletion or consolidation over compatibility layers when no supported consumer requires them;
- do not duplicate state between UI, registry, Paper, and converter runtime;
- keep public contracts small and typed;
- keep external implementation details out of domain/application code;
- do not introduce a shared abstraction until at least one real responsibility requires it;
- configuration must have one owner and documented defaults;
- background work is opt-in and operation-bound: no idle polling, idle converter worker, unnecessary filesystem watchers, or repeated scans;
- expensive file/conversion work must be bounded and isolated from Paper's main thread;
- world mutation and filesystem publication must be transactional enough that failure leaves the previously valid state recoverable;
- source comments explain non-obvious constraints, not obvious syntax;
- names describe product concepts (`World Manager`, `Import World`, `Export Area`) rather than leaking third-party engine terminology.

## Documentation Contract

Documentation records current durable behavior, ownership, and constraints. It is not a chronological work log.

```text
docs/01-product/          product scope and flow
docs/02-world-management/ world behavior and feature contracts
docs/03-client-ui/        client/Xaero presentation contracts
docs/04-system/           architecture, ownership, maintainability
docs/05-operations/       current continuation/proof only
```

When implementation changes a durable contract, update the canonical document in the same coherent change. Do not copy the same rule into multiple documents; link to the canonical owner instead. Git history owns retired decisions and old implementation detail.

## Change Shape

A normal implementation change should be reviewable as one coherent outcome:

```text
requirement
→ first wrong owner
→ minimum complete source change
→ targeted test/proof
→ canonical-doc update only when durable behavior changed
→ STOP
```

Do not mix unrelated cleanup, speculative architecture, or future features into the same change.

## Context Loading

Use domain-local context only:

```text
world behavior    → docs/02-world-management + world source
client UI/Xaero   → docs/03-client-ui + client source
architecture      → this document + exact affected source owners
current proof     → docs/05-operations only when continuation matters
```

Do not load all docs or all Skills as ceremony.

## Dependency Policy

- Paper/Bukkit APIs are preferred stable boundaries.
- No NMS without explicit evidence and a version-bound owner.
- No Multiverse runtime dependency in the target World Manager.
- Xaero dependency/integration must be isolated behind one client-side boundary so world-management server logic remains independent.
- The conversion engine must remain behind one World Manager adapter and run on demand rather than becoming a permanent server runtime.
- Third-party version changes must be absorbed at their adapter boundary whenever the product contract has not changed.

## Proof Boundary

```text
REMOTE_GITHUB → source/static/CI claims only
LOCAL_CODE    → compile/unit/integration/build claims
LIVE_SERVER   → actual Paper lifecycle, world mutation, client interaction, and gameplay/runtime claims
```

Use the cheapest proof capable of falsifying the changed claim. A green unrelated check is not acceptance evidence.
