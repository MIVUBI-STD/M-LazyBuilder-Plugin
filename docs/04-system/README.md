# System / Ownership

Canonical owner for LazyBuilder module boundaries and source ownership.

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
```

Commands and UI adapters delegate to the same application owners. Do not create separate command logic and UI logic for the same operation.

## Source Shape

Keep implementation shallow until real complexity requires more structure. A likely initial server shape is:

```text
world/
├── WorldService
├── WorldRegistry
├── WorldRepository
└── paper/
```

Names are not contracts until source implementation starts; responsibility boundaries are.

Avoid manager proliferation such as `WorldController`, `WorldCoordinator`, `WorldRuntimeManager`, and `WorldLifecycleManager` unless distinct responsibilities are proven.

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
- Xaero dependency/integration must be isolated behind a client-side boundary so world-management server logic remains independent.
