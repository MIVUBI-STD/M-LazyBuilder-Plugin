# System / Ownership

Canonical owner for LazyBuilder module boundaries, source ownership, and maintainability rules. Specialist/jobdesk routing lives in `skill-routing.md`; minimum-flow execution discipline lives in `development-discipline.md`.

## Target runtime

```text
LazyBuilder Desktop (Tauri/Svelte/Rust)
        │ authenticated loopback control
        ▼
Paper Modules
  ├── World-Manager
  └── Utilities-Manager
        ▲
        │ bounded first-party Minecraft protocols
Fabric Client Managers
  ├── Map Manager
  ├── Utility Manager
  └── Performance Manager
```

Minecraft client/server application traffic reuses the existing play connection. The desktop loopback bridge is local-only and separate from the in-game protocol plane.

The fullscreen map is first-party LazyBuilder UI. Xaero may be used only as an interaction-quality reference, not as a runtime dependency or semantic owner.

## Ownership rules

```text
Desktop UI          → presentation/application state
Desktop Runtime     → workspace/process/provisioning/runtime orchestration
Plugin-Manager      → third-party Paper plugin lifecycle
Map Manager         → world/map/transfer client presentation + World protocol client
Utility Manager     → passive non-building client convenience
Performance Manager → performance/resource observation + background-FPS policy
Shared Protocol     → typed bounded Paper/Fabric contracts
World Application  → world lifecycle/use-case policy
World Registry     → durable LazyBuilder world metadata
Paper Adapters      → Bukkit/Paper runtime translation + transport adapters
File Transfer       → bounded import/export byte sessions
Conversion Adapter → external converter runtime/process details only
```

Commands, desktop UI, Fabric UI, and transports delegate to canonical semantic owners; they do not duplicate business rules.

## Source shape

```text
EngineData/Frontend/RustApp/
→ canonical desktop source

shared/protocol/
→ neutral Paper/Fabric wire contracts

modules/world-manager/
→ Paper World-Manager

modules/utilities-manager/
→ Paper Utilities-Manager

client/map-manager/
→ Fabric Map Manager

client/utility-manager/
→ Fabric Utility Manager

client/performance-manager/
→ Fabric Performance Manager
```

A package/module exists only when its responsibility exists in source. Do not create empty layers for symmetry.

## World runtime model

Persistent world lifecycle is only:

```text
ACTIVE
ARCHIVED
```

Loaded/unloaded/loading/unloading are runtime facts owned by Paper, not durable product states.

```text
required use
→ automatic load

empty + idle + no conflicting operation
→ automatic unload
```

Do not reintroduce manual Load/Unload, per-world autoLoad, or Clone terminology. User-facing copy uses `Duplicate`.

## Networking boundaries

```text
lazybuilder:world     → World Control V5
lazybuilder:map       → Map Action V2
lazybuilder:transfer  → bounded file bytes only
```

Desktop ↔ Paper uses a separate authenticated loopback protocol currently at version 2.

Transport/provider layers never become authority for worlds, permissions, lifecycle, conversion, or persistence.

## Maintainability contract

- one semantic owner per responsibility;
- one persisted fact has one authority;
- one process has one lifecycle/recovery owner;
- one config concern has one reader/writer authority;
- one wire contract has one neutral source;
- one user action has one primary execution path;
- compatibility fallbacks exist only to protect supported user data and must not become parallel authorities;
- keep external implementation details behind adapter boundaries;
- no idle polling, converter daemon, watcher, scheduler, or background worker without a concrete active responsibility;
- expensive file/conversion work remains bounded and off Paper's primary thread;
- destructive/file publication paths remain recoverable/fail-safe;
- internal maintenance is automatic unless it represents a real user decision.

## Documentation contract

```text
docs/01-product/          product scope and user flow
docs/02-world-management/ world behavior and feature contracts
docs/03-client-ui/        first-party Fabric presentation/map contracts
docs/04-system/           architecture, ownership, networking, routing
docs/05-operations/       current proof/handoff only
```

Documentation records current durable behavior, not chronological work history. Git history owns superseded designs and retired terminology.

## Dependency policy

- Paper/Bukkit APIs are preferred stable server boundaries.
- No NMS without explicit evidence and a version-bound owner.
- No Multiverse runtime dependency in the target World Manager.
- External build tools remain external.
- Specialist performance engines remain external; Performance Manager coordinates/detects rather than reimplements them.
- Converter implementations remain behind one World Manager adapter and run only when requested.
- Optional deployment tunnels/VPNs do not alter LazyBuilder application protocols.

## Proof boundary

```text
REMOTE_GITHUB → source/static/build/package claims
LOCAL_CODE    → target-PC compile/install/filesystem claims
LIVE_SERVER   → actual Paper/Fabric/Minecraft/gameplay behavior
```

Use the cheapest proof capable of falsifying the changed claim. Do not infer live runtime success from CI.
