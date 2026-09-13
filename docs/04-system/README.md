# System / Ownership

Canonical owner for LazyBuilder module boundaries, source ownership, and maintainability rules.

For execution specialist/jobdesk selection, use [`skill-routing.md`](skill-routing.md). This document defines durable architecture; Skills define procedure.

## Target Runtime

```text
LazyBuilder Desktop (Tauri/Svelte)
        │ local-only authenticated control
        ▼
LazyBuilder Paper Modules
  ├── World-Manager
  └── Utilities-Manager
        ▲
        │ bounded first-party Minecraft protocol
LazyBuilder Fabric Client
```

Client/server application traffic reuses the existing Minecraft play connection. LazyBuilder does not require a separate relay, VPN service, HTTP gateway, WebSocket server, or second public listening port. The authenticated desktop loopback control bridge remains local-only and is not the client/server data plane. The canonical networking contract is [`networking.md`](networking.md).

Xaero World Map is an external client integration for map preview/location interaction, not a world-state or network authority.

## Ownership Rules

```text
Desktop UI         → presentation/input only
Desktop Runtime    → workspace, process, provisioning, resource/runtime orchestration
Plugin Manager     → third-party Paper plugin lifecycle only
Fabric Client UI   → in-game presentation/input only
Shared Protocol    → typed bounded Paper/Fabric requests/results
World Application → lifecycle/use-case policy
World Registry    → LazyBuilder-owned world metadata
Paper Adapter     → Bukkit/Paper calls and runtime translation
File Transfer     → import/export/clone filesystem safety
Conversion Adapter→ external converter process/runtime details only
```

Commands, UI, and transport adapters delegate to the same semantic owners. Do not create separate command logic and UI logic for the same operation.

The external converter is an implementation detail inside World Manager. Import, export, multi-version conversion, runtime updates, and file management remain one product domain and must not grow into parallel product surfaces.

## Source Shape

Keep implementation shallow until real complexity requires more structure. Prefer responsibility-based modules rather than generic manager hierarchies.

Current major ownership shape:

```text
Desktop
├── workspace/runtime/process/providers
├── plugin-manager
└── world-manager client bridge

Paper World Manager
├── application/     world use cases and policy
├── registry/        persistent LazyBuilder world identity/metadata
├── paper/           Paper/Bukkit runtime boundary
├── files/           safe world-file operations
├── conversion/      isolated conversion adapter/runtime lifecycle
├── task/            bounded desktop task contract/runner
└── transfer/        bounded file-transfer session contract

Shared
└── protocol/        neutral Paper/Fabric request/result contracts

Fabric Client
└── presentation + isolated Xaero integration
```

A package/module exists only when its responsibility exists in source. Do not create empty layers for symmetry.

Avoid manager proliferation such as `WorldController`, `WorldCoordinator`, `WorldRuntimeManager`, and `WorldLifecycleManager` unless distinct responsibilities are demonstrated.

## Maintainability Contract

Every durable feature should have one canonical semantic owner and one primary runtime path.

```text
requirement / policy → canonical docs
business behavior    → application/domain source
runtime orchestration→ exact runtime owner
Paper behavior       → Paper adapter
external integration → one dedicated adapter
proof                 → nearest targeted test / runtime check
history               → Git history
```

Rules:

- prefer deletion or consolidation over compatibility layers when no supported consumer requires them;
- compatibility storage fallbacks are allowed only to protect existing user data and must not become parallel active authorities;
- do not duplicate state between UI, registry, runtime, Paper, and converter;
- one config concern has one config owner;
- one managed process has one process/recovery owner;
- keep public contracts small and typed;
- keep external implementation details out of domain/application code;
- do not introduce a shared abstraction until at least one real responsibility requires it;
- background work is opt-in and operation-bound: no idle polling, idle converter worker, unnecessary filesystem watchers, or repeated scans;
- expensive file/conversion work must be bounded and isolated from Paper's main thread;
- world mutation and filesystem publication must be recoverable enough that failure does not silently destroy the previous valid state;
- internal maintenance is automatic unless it represents a real user decision;
- runtime directories must have an active semantic owner; do not create placeholder folders for metadata-only states;
- source comments explain non-obvious constraints, not obvious syntax;
- names describe product concepts rather than leaking third-party implementation terminology.

## Documentation Contract

Documentation records current durable behavior, ownership, and constraints. It is not a chronological work log.

```text
docs/01-product/          product scope and flow
docs/02-world-management/ world behavior and feature contracts
docs/03-client-ui/        Fabric/Xaero presentation contracts
docs/04-system/           architecture, ownership, networking, specialist routing
docs/05-operations/       current continuation/proof only
```

When implementation changes a durable contract, update the canonical document in the same coherent change. Do not copy the same rule into multiple documents; link to the canonical owner instead. Git history owns retired decisions and old implementation detail.

## Change Shape

A normal implementation change should be reviewable as one coherent outcome:

```text
requirement
→ exact specialist / first wrong owner
→ minimum complete source change
→ targeted proof
→ canonical-doc update only when durable behavior changed
→ STOP
```

Do not mix unrelated cleanup, speculative architecture, or future features into the same change.

## Context Loading

Use domain-local context only:

```text
jobdesk/routing     → skill-routing.md
world behavior      → docs/02-world-management + world source
Fabric UI/Xaero     → docs/03-client-ui + client source
network ownership   → networking.md + exact protocol/adapter source
desktop runtime     → skill-routing.md + exact desktop runtime source
architecture        → this document + exact affected owner
current proof       → docs/05-operations only when continuation matters
```

Do not load all docs or all Skills as ceremony.

## Dependency Policy

- Paper/Bukkit APIs are preferred stable boundaries.
- No NMS without explicit evidence and a version-bound owner.
- No Multiverse runtime dependency in the target World Manager.
- Xaero dependency/integration must be isolated behind one client-side boundary so world-management server logic remains independent.
- The conversion engine must remain behind one World Manager adapter and run on demand rather than becoming a permanent server runtime.
- LazyBuilder application networking must remain first-party and independent of optional deployment tunnels/relays; see [`networking.md`](networking.md).
- Third-party version changes must be absorbed at their adapter boundary whenever the product contract has not changed.

## Proof Boundary

```text
REMOTE_GITHUB → source/static/CI claims only
LOCAL_CODE    → compile/unit/integration/build claims
LIVE_SERVER   → actual Paper lifecycle, world mutation, client interaction, and gameplay/runtime claims
```

Use the cheapest proof capable of falsifying the changed claim. A green unrelated check is not acceptance evidence.
