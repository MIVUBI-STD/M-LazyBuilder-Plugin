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

Do not preload every reference for a small runtime change.

## Procedure

```text
1. Name the exact launcher/runtime responsibility.
2. Find the existing state/persistence/process authority.
3. Classify the change:
   architecture | operation | persistence | update/distribution | runtime lifecycle | diagnostics.
4. Read the matching reference only.
5. Identify duplicate ownership or web-UI-owned business logic.
6. Reuse/consolidate the existing path.
7. Design failure, retry, restart, and rollback semantics before happy-path code.
8. Make the smallest recoverable mutation.
9. Prove the narrowest layer first; expand proof only when the boundary changes.
10. Hand presentation-only work to lazybuilder-ui and STOP.
```

## Launcher Engineering Invariants

### State and ownership

- Rust is authoritative for filesystem, process, update, backup, diagnostics, and persistent settings semantics.
- Svelte owns presentation state only; it must not invent durable truth that conflicts with Rust.
- every long-running action has one operation identity and a finite state model;
- a committed mutation must not be reported as failed only because a later refresh/network lookup failed;
- startup reconciliation is part of every persistent lifecycle design.

### Filesystem and destructive work

- destructive/copy/restore/update work validates canonical paths and ownership before mutation;
- never trust a user-facing path string as deletion authority;
- use staging + validate + atomic publish/rename where practical;
- preserve or record enough previous-valid state to recover interrupted replacement;
- ENOSPC, access denial, partial copy, process linger, and restart interruption are first-class cases.

### Long-running operations

- progress is semantic (`phase`, `current`, `total`) rather than spinner-only;
- cancellation exists only when the underlying operation has a safe cancellation boundary;
- retry reuses authoritative state instead of blindly repeating side effects;
- operation history/diagnostics may persist results, but must not become a second domain database.

### Settings and migrations

- one settings authority and schema;
- version persisted settings that may evolve;
- migrate forward deterministically and keep a recoverable previous state for risky migrations;
- defaults are centralized and must not diverge between frontend and Rust.

### Updates and distribution

- Launcher self-update is separate from Paper/runtime update;
- update artifacts require authenticity/integrity verification before activation;
- update flow is staged and restart-aware;
- private signing/update keys never enter the repository;
- installer identity, executable identity, Start Menu/taskbar identity, and updater identity stay consistent.

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
What happens on partial failure?
Can retry duplicate side effects?
Can cancel leave inconsistent state?
What path/process identity proves authority?
Does update/restore/delete preserve a recoverable previous state?
What is logged for support without exposing secrets?
Which parts are semantics (this Skill) versus presentation (lazybuilder-ui)?
What exact CI/static/live proof can falsify the implementation?
```

If these cannot be answered, do not start with UI implementation.

## Proof Boundary

Source/static tests can prove ownership, validation, state transitions, migration logic, path guards, serialization contracts, and failure-path structure.

Real Windows installer behavior, taskbar/shortcut identity, WebView2/environment behavior, updater restart/install, Paper process recovery, filesystem permissions/locking, power-loss-like interruption, and restart persistence require appropriate packaged/local proof after source-side gates are clean.
