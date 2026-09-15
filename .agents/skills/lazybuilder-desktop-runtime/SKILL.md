---
name: lazybuilder-desktop-runtime
description: Own LazyBuilder Launcher engineering and desktop runtime semantics: Tauri/Rust application architecture, workspace lifecycle, process ownership/recovery, long-running operations, settings persistence/migrations, self-update/distribution semantics, Windows app identity, diagnostics, provisioning, managed Java/Paper/core, startup safety, and desktop loopback control. Use for launcher framework/architecture decisions, not visual presentation, Paper world rules, shared Paper/Fabric protocol, or third-party plugin lifecycle.
---

# LazyBuilder Desktop Runtime / Launcher Engineering

Own the **desktop application and runtime architecture** behind LazyBuilder. This is the primary Skill for Launcher framework decisions. Follow `docs/04-system/development-discipline.md` for minimum-flow decisions and `docs/04-system/skill-routing.md` for cross-owner handoffs.

The Launcher is a Tauri desktop application, not a web page wrapped in an executable. Treat Rust/Tauri as the trusted application core and Svelte as the presentation client.

## Owns

```text
LAUNCHER APPLICATION ARCHITECTURE
Tauri/Rust core boundaries and command orchestration
frontend/backend IPC contract ownership on the desktop side
application lifecycle/startup/shutdown/restart behavior
long-running operation/job semantics
settings/config persistence and schema migration
launcher self-update semantics and update channels
Windows bundle/installer/app identity semantics
diagnostics/support-data collection semantics
release/recovery/rollback behavior

SERVER DESKTOP RUNTIME
workspace create/open/adopt/activate/close/relocate behavior
server library registry semantics
server duplicate/remove/delete/backup/restore runtime behavior
Paper process start/stop/restart/detached recovery
one process marker / one recovery path
managed Java provisioning
Paper provisioning and manual Paper updates
bundled core compatibility/synchronization
server runtime/resource configuration
startup safety
local desktop HTTP/control bootstrap
runtime persistence and rollback semantics
```

## Does Not Own

```text
visual hierarchy/layout/input/focus/toast/modal presentation → lazybuilder-ui
Paper world lifecycle/import/export                      → lazybuilder-world-management
third-party Paper plugin lifecycle                       → lazybuilder-plugin-management
shared Paper/Fabric wire contracts                       → lazybuilder-protocol
```

`lazybuilder-ui` may decide how an operation is presented. This Skill decides what the operation **is**, which states exist, who owns them, whether it can retry/cancel, how it persists, and how it recovers.

Desktop loopback HTTP remains this Skill's boundary even when it talks to World Manager. `shared/protocol` is not the owner of desktop HTTP transport.

## Architecture Model

Use this default layering unless source proves a smaller existing owner already exists:

```text
Svelte presentation
    ↓ typed invoke/event bridge
Tauri command boundary
    ↓
application service / operation orchestration
    ↓
domain owner (workspace, process, update, settings, backup, diagnostics)
    ↓
platform/provider adapter (filesystem, Java, Paper, updater, NSIS/Windows)
```

Rules:

- keep business/runtime authority in Rust, not duplicated in Svelte;
- commands validate input and delegate; they are not second domain owners;
- providers stay separate only when they have a real external/platform responsibility;
- one concern gets one state authority, one persistence path, and one recovery path;
- prefer explicit state machines over scattered booleans for lifecycle or long-running work;
- do not add a second queue, cache, registry, updater, settings store, or recovery subsystem when an existing owner can be extended.

## Reference Routing

Read only the reference needed for the task:

| Task | Reference |
|---|---|
| Tauri/Rust/Svelte boundaries, command/service/state architecture, settings | `references/launcher-architecture.md` |
| background operations, progress, cancellation, retry, transaction/recovery | `references/operations-and-recovery.md` |
| Windows identity, NSIS, updater, signing, release artifacts, CI gates | `references/windows-distribution-and-update.md` |
| professional launcher quality audit / feature acceptance checklist | `references/launcher-quality-gates.md` |
| compare a design against mature launcher source patterns / choose reusable execution-readiness-update patterns | `references/real-launcher-patterns.md` |
| provenance of external skills/repos and what was or was not adopted | `references/research-basis.md` |

Do not preload every reference for a small runtime change.

## Production Launcher Baseline

Before inventing a new launcher subsystem, compare the problem against the production-source synthesis in `references/real-launcher-patterns.md`.

The default reusable patterns are:

```text
long-running execution
→ one operation model
→ explicit lifecycle
→ queryable current snapshot
→ typed progress events
→ safe cancel/retry
→ restart reconciliation

server readiness
→ one Rust health/readiness authority
→ reused by every launcher surface

application startup
→ one startup coordinator
→ migration/recovery/reconciliation before UI-ready

launcher self-update
→ one updater state machine
→ one updater lock
→ signed/verified artifact
→ restart-aware install

observability
→ bounded rotating logs
→ stable error codes/details
→ bounded operation history
→ support export without secrets
```

Do not copy PrismLauncher, GDLauncher Carbon, DropOut, ATLauncher, or any external launcher source/assets. Adopt only architecture patterns that fit LazyBuilder's semantic owners and license boundaries.

## Procedure

```text
1. Name the exact launcher/runtime responsibility.
2. Find the existing state/persistence/process authority.
3. Classify the change:
   architecture | operation | persistence | update/distribution | runtime lifecycle | diagnostics.
4. Read the matching reference only.
5. For non-trivial launcher infrastructure, compare against real-launcher-patterns.md.
6. Identify duplicate ownership or web-UI-owned business logic.
7. Reuse/consolidate the existing path.
8. Design failure, retry, restart, and rollback semantics before happy-path code.
9. Make the smallest recoverable mutation.
10. Prove the narrowest layer first; expand proof only when the boundary changes.
11. Hand presentation-only work to lazybuilder-ui and STOP.
```

## Launcher Engineering Invariants

### State and ownership

- Rust is authoritative for filesystem, process, update, backup, diagnostics, readiness/health, and persistent settings semantics.
- Svelte owns presentation state only; it must not invent durable truth that conflicts with Rust.
- every long-running action has one operation identity and a finite state model;
- a committed mutation must not be reported as failed only because a later refresh/network lookup failed;
- startup reconciliation is part of every persistent lifecycle design;
- a UI reload/window recreation must be able to query current operation/update/readiness state instead of depending only on missed events.

### Filesystem and destructive work

- destructive/copy/restore/update work validates canonical paths and ownership before mutation;
- never trust a user-facing path string as deletion authority;
- use staging + validate + atomic publish/rename where practical;
- preserve or record enough previous-valid state to recover interrupted replacement;
- ENOSPC, access denial, partial copy, process linger, and restart interruption are first-class cases.

### Long-running operations

- progress is semantic (`phase`, `current`, `total`) rather than spinner-only;
- complex work may expose explicit step progress rather than fake one-number percentages;
- cancellation exists only when the underlying operation has a safe cancellation boundary;
- retry reuses authoritative state instead of blindly repeating side effects;
- operation history/diagnostics may persist results, but must not become a second domain database;
- only one conflicting operation for the same exclusive resource may run at once; a second caller consumes busy/current state instead of silently starting a duplicate operation.

### Readiness and health

- readiness is computed in Rust from authoritative runtime/filesystem/process state;
- Server Library, Overview, Start eligibility, Repair recommendation, and future tray/shortcut surfaces consume the same readiness result;
- large libraries use bounded/progressive health probing so expensive checks do not block initial library rendering;
- presentation may summarize health but must not independently reimplement the rules.

### Settings and migrations

- one settings authority and schema;
- version persisted settings that may evolve;
- migrate forward deterministically and keep a recoverable previous state for risky migrations;
- defaults are centralized and must not diverge between frontend and Rust.

### Updates and distribution

- Launcher self-update is separate from Paper/runtime update;
- update artifacts require authenticity/integrity verification before activation;
- update flow is staged and restart-aware;
- updater state is explicit, finite, queryable, and single-owner;
- only one launcher-update operation may run at a time;
- private signing/update keys never enter the repository;
- installer identity, executable identity, Start Menu/taskbar identity, and updater identity stay consistent;
- test update feeds/endpoints are permitted only through explicit test/E2E boundaries, never arbitrary production override.

### Observability

- launcher logs are bounded/rotated rather than unbounded or console-only;
- durable technical failures use stable machine-readable codes plus human summaries/details;
- support diagnostics include build/version/channel and relevant operation/runtime state while excluding credentials/tokens/secrets;
- event streams improve responsiveness but are not the only source of truth.

### Runtime

- one Paper process lifecycle owner;
- one active process identity/marker authority;
- one server-config reader/writer authority;
- one provisioning path;
- one recovery path;
- CPU scheduling remains JVM/OS managed; resource settings own RAM profile/ceiling only;
- bundled core maintenance stays internal; only user-relevant runtime decisions surface as user choices.

## Quality Gate Before Implementation

For a non-trivial Launcher feature, answer:

```text
Who owns durable state?
What is the state machine?
What survives restart?
Can a reopened UI query current state without replaying old events?
What happens on partial failure?
Can retry duplicate side effects?
Can cancel leave inconsistent state?
What path/process identity proves authority?
Is there already a reusable operation/readiness/updater owner?
Does update/restore/delete preserve a recoverable previous state?
What is logged for support without exposing secrets?
Which parts are semantics (this Skill) versus presentation (lazybuilder-ui)?
What exact CI/static/live proof can falsify the implementation?
```

If these cannot be answered, do not start with UI implementation.

## Proof Boundary

Source/static tests can prove ownership, validation, state transitions, migration logic, path guards, serialization contracts, concurrency/duplicate-operation guards, and failure-path structure.

Real Windows installer behavior, taskbar/shortcut identity, WebView2/environment behavior, updater restart/install, Paper process recovery, filesystem permissions/locking, power-loss-like interruption, and restart persistence require appropriate packaged/local proof after source-side gates are clean.
