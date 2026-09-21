# LazyBuilder System Coordination Plane

## Purpose

LazyBuilder remains modular by semantic ownership, but a mature multi-runtime product also needs one coherent read model of the whole system.

The coordination plane is deliberately **not** a new business/domain authority. It does not own worlds, server lifecycle, plugins, client setup, renderer behavior, builder editing, or persisted domain configuration.

Its responsibilities are limited to:

~~~text
observe existing authorities
→ compose a read-only system snapshot
→ project capability/readiness
→ expose one stable Launcher query surface
~~~

## Architecture

~~~text
Existing authorities
├── Workspace Registry
├── Server Runtime Registry
├── Server Health
├── Operation Registry
├── Paper World Manager
└── Fabric managers
        │
        │ typed/read-only observations
        ▼
System coordination plane
├── SystemSnapshot
├── capability projection
├── readiness projection
└── later: typed invalidation / cross-runtime activity projection
        │
        ▼
Launcher UI / diagnostics / workflow routing
~~~

The first implementation lives in:

~~~text
apps/launcher/src-tauri/src/engine/system/
├── snapshot.rs
├── capabilities.rs
├── context.rs
└── activity.rs

apps/launcher/src-tauri/src/commands/system.rs
apps/launcher/src-tauri/src/commands/system_activity.rs
~~~

System coordination is split by cohesive projection responsibility. The services store no duplicate durable business state and run no background worker.

## SystemSnapshot

The snapshot currently composes:

- active workspace and recent workspace library;
- server readiness;
- live/detached server runtime summaries;
- active Launcher operations;
- Paper World Manager task activity when the active runtime bridge is available;
- projected capabilities;
- projection warnings.

This is an on-demand projection. Callers must not persist it as a second authority.

## Capability rules

Capabilities answer whether a user-facing action is usable **now** and why.

Examples:

~~~text
workspace.manage
server.inspect
server.start
server.stop
world.manage
client.sync
diagnostics.export
~~~

A capability is computed from existing facts. It does not grant permission to bypass the owning subsystem's own validation. Domain commands must continue to validate their invariants at execution time.

~~~text
projection says "available"
        ↓
user invokes command
        ↓
domain owner validates again
        ↓
operation executes or fails safely
~~~

The projection is UX/orchestration guidance, not a security boundary.

## Readiness rules

System readiness is intentionally coarse:

~~~text
NO_WORKSPACE
NEEDS_ATTENTION
READY
BUSY
DEGRADED
~~~

It is not a replacement for detailed server health, operation state, or component diagnostics.

BUSY represents a transition or conflicting active operation. DEGRADED means the projection could not safely inspect part of the system; it must never silently assume readiness.

## Ownership invariants

1. No subsystem moves its durable state into the system coordination package.
2. System projection services may read owners but must not reimplement their domain rules.
3. No global mutable event bus is introduced.
4. No idle poller or watcher is introduced merely to keep the snapshot fresh.
5. UI may use capability/readiness projection for presentation, but execution commands revalidate.
6. Cross-runtime capability claims must be added only when there is an authoritative observation source; protocol versions alone are not feature flags.
7. Unknown state fails closed into unavailable/degraded rather than optimistic readiness.
8. Projection computation should stay cheap enough for explicit refresh or event-triggered refresh.

## Next integration stages

1. Launcher SystemSnapshot and local capability projection — implemented.
2. Launcher shell consumes the snapshot for workspace/runtime composition instead of rebuilding those joins in Svelte — implemented.
3. Paper task snapshots are projected into one Activity view with Launcher operations while Paper keeps task ownership — implemented.
4. World Manager advertises explicit bridge capabilities; `world.manage` readiness is gated by that handshake rather than version guessing — implemented for the Desktop ↔ Paper boundary.
5. Extend capability discovery to Fabric managers using their existing versioned boundaries — next.
6. Typed invalidation signals refresh only affected projections — next.
7. Runtime proof verifies degraded/recovery transitions on a real Windows/Minecraft environment — acceptance layer.

Frontend activity observation also has one shared feed (`app/operations/activityFeed.ts`) so the Activity page and navigation badge do not create duplicate polling loops.

Do not jump directly to a generic event bus, distributed state store, or universal scheduler.
