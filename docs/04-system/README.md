# System / Ownership

Canonical owner for LazyBuilder module boundaries, source ownership, and maintainability rules. Specialist/jobdesk routing lives in `skill-routing.md`; minimum-flow execution discipline lives in `development-discipline.md`; canonical developer/deployment workflow lives in `development-operations.md`.

## Target runtime

```text
LazyBuilder Desktop (Tauri + Svelte + Rust)
        │ authenticated loopback control
        ▼
Paper Modules
  ├── World Manager
  └── Utilities Manager
        ▲
        │ bounded first-party Minecraft protocols
Required Fabric Client
  ├── Map Manager
  ├── Utility Manager
  └── Performance Manager
```

Minecraft client/server application traffic reuses the existing play connection. The desktop loopback bridge is local-only and separate from the in-game protocol plane.

The fullscreen map is first-party LazyBuilder UI. Xaero may be used only as an interaction-quality reference, not as a runtime dependency or semantic owner.

Performance Manager is part of the current required V1 client suite because current source, Launcher packaging, Client Setup, verification, and distribution all build and bundle it. Its responsibility remains bounded to client performance policy/diagnostics; it must not become a scheduler or workload owner for Map Manager, Utility Manager, Paper, or Launcher runtime.

## Ownership rules

```text
Desktop UI          → presentation/application state
Desktop Runtime     → workspace/process/provisioning/runtime orchestration
Plugin Manager      → third-party Paper plugin lifecycle
Map Manager         → world/map/transfer client presentation + World protocol client
Utility Manager     → passive non-building client convenience
Performance Manager → bounded client performance policy/diagnostics
Shared Protocol     → typed bounded Paper/Fabric contracts
World Application  → world lifecycle/use-case policy
World Registry     → durable LazyBuilder world metadata
Paper Adapters      → Bukkit/Paper runtime translation + transport adapters
File Transfer       → bounded import/export byte sessions
Conversion Adapter → external converter runtime/process details only
```

Commands, desktop UI, Fabric UI, and transports delegate to canonical semantic owners; they do not duplicate business rules.

## Current source shape

```text
apps/launcher/
├── src/                 Svelte desktop presentation
└── src-tauri/src/       Rust desktop runtime/orchestration

plugins/
├── world-manager/       Paper World Manager
└── utilities-manager/   Paper Utilities Manager

mods/
├── map-manager/         required Fabric Map Manager
├── utility-manager/     required Fabric Utility Manager
└── performance-manager/ required bounded Fabric performance component

shared/protocol/
└── neutral Paper/Fabric wire contracts

tooling/windows-toolchain/
└── repository-owned Windows build/bootstrap/distribution tooling

scripts/
└── repository/runtime verification utilities
```

This layout is current source authority. Do not recreate retired `EngineData`, `Frontend`, `RustApp`, `modules`, or `client` directory models merely because older reports or historical commits mention them.

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
- one developer operation starts from the canonical root command surface defined in `development-operations.md`;
- compatibility fallbacks exist only to protect supported user data and must not become parallel authorities;
- keep external implementation details behind adapter boundaries;
- no idle polling, converter daemon, watcher, scheduler, or background worker without a concrete active responsibility;
- expensive file/conversion work remains bounded and off Paper's primary thread;
- destructive/file publication paths remain recoverable/fail-safe;
- internal maintenance is automatic unless it represents a real user decision;
- Performance Manager must remain bounded to its named performance responsibility and may not quietly absorb other managers' execution ownership.

## Documentation contract

```text
docs/01-product/          product scope and user flow
docs/02-world-management/ world behavior and feature contracts
docs/03-client-ui/        first-party Fabric presentation/map contracts
docs/04-system/           architecture, minimum-flow discipline, ownership, networking, routing
docs/05-operations/       current proof/handoff only
```

Documentation records current durable behavior, not chronological work history. Git history owns superseded designs and retired terminology.

Supporting evidence documents under `docs/04-system/`—including files primarily named or scoped as audits, locks, implementation status, handoffs, or migration evidence—are **opt-in context only**. They may explain why a canonical rule exists, but they must not be preloaded for ordinary implementation or override this README, current source, or the canonical owner documents. Load them only when rationale/proof can materially change the decision.

Do not add another architecture lock/report when an existing canonical document can be corrected directly. New durable behavior belongs in its canonical owner; historical evidence belongs in Git history unless an active verification/handoff need requires a current evidence document.

## Dependency policy

- Paper/Bukkit APIs are preferred stable server boundaries.
- No NMS without explicit evidence and a version-bound owner.
- No Multiverse runtime dependency in the target World Manager.
- External build tools remain external to the shipped application unless the product explicitly owns provisioning them.
- Performance optimization remains inside the bounded Performance Manager responsibility; do not add another performance engine, shared scheduler, or cross-manager optimization authority without measured evidence and a distinct repeated responsibility.
- Converter implementations remain behind one World Manager adapter and run only when requested.
- Optional deployment tunnels/VPNs do not alter LazyBuilder application protocols.
- Modrinth remains owner of the Minecraft profile, Fabric loader, third-party mods/modpack, and game launching.

## Proof boundary

```text
REMOTE_GITHUB → source/static/CI/build/package claims
LOCAL_CODE    → local checkout/toolchain/filesystem claims
LIVE_SERVER   → actual Paper/Fabric/Minecraft/gameplay behavior
```

Use the cheapest proof capable of falsifying the changed claim. Do not infer live runtime success from CI.
