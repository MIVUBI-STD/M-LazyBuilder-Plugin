# Launcher Architecture Reference

Use for changes to Tauri/Rust/Svelte boundaries, command orchestration, application services, persistent settings, or launcher-wide state ownership.

## Default Process Model

LazyBuilder is a Tauri desktop application:

```text
Windows process
└─ Rust/Tauri core
   ├─ window/app lifecycle
   ├─ trusted filesystem/process access
   ├─ persistent application state
   ├─ server/workspace orchestration
   └─ typed command/event boundary
       ↓
     Svelte/WebView presentation
```

The frontend is not allowed to become a second runtime authority simply because it is easier to implement there.

## Layer Responsibilities

### Svelte presentation

Owns:

```text
rendering
interaction state
selection
pending visual state
forms
navigation
accessibility/focus
presentation formatting
```

May cache read models for rendering, but durable truth must be refreshable from Rust.

### Tauri command boundary

Owns:

```text
input decoding/validation
permission/capability boundary
mapping domain errors to stable command errors
delegating to one application/domain owner
```

Avoid large commands containing persistence, filesystem mutation, process logic, and presentation-specific decisions in one function.

### Application service / orchestrator

Use when one user action legitimately coordinates multiple providers/domains, for example:

```text
self update
server duplicate
backup restore
server repair
provisioning
support package export
```

The orchestrator owns sequencing, not the internal truth of every provider.

### Domain owner

Examples:

```text
workspace_registry
server_manager
runtime_updates
resource_settings
future launcher_settings
future operation_registry
future backup service
future launcher updater service
```

Each durable concern should have one owner.

### Provider/platform adapter

Examples:

```text
java_runtime
paper_provider
filesystem helper
Windows installer/updater adapter
GitHub/static release metadata provider
```

Separate a provider only when it talks to an external/platform mechanism with materially different failure semantics.

## IPC Contract Rules

- prefer typed request/result DTOs;
- use camelCase serialization consistently across Rust/TypeScript;
- stable machine-readable error codes belong at the command boundary;
- user-facing sentences belong to UI presentation unless the text is itself a durable protocol requirement;
- do not expose arbitrary filesystem primitives when a bounded domain command can do the work;
- minimize Tauri permissions/capabilities to what the main window actually requires;
- never place secrets or signing keys in frontend code.

## State Machine Rule

Use an explicit enum/state machine when state controls legal actions or recovery.

Good candidates:

```text
server lifecycle
launcher self-update
backup/restore
repair
provisioning
long-running file copy
runtime migration
```

Avoid combinations such as:

```text
isLoading
isDone
hasFailed
isCancelled
isRetrying
```

when those booleans can express impossible states.

Prefer:

```text
Queued
Preparing
Running
Committing
Succeeded
Failed
Cancelled
NeedsRecovery
```

Domain-specific states may be smaller. Do not force a universal state machine if the operation does not need it.

## Persistent Settings

One settings system should eventually own Launcher preferences such as:

```text
last-opened behavior
update channel/policy
default server location
confirmation preferences
launcher behavior
diagnostic preferences
```

Rules:

1. persist a schema version;
2. centralize defaults in the authority layer;
3. frontend receives effective settings rather than maintaining duplicate defaults;
4. migrations are deterministic and tested;
5. migration writes use temporary/staged files and preserve a previous-valid state when risk is meaningful;
6. unknown/newer schema must fail safely rather than silently resetting user data.

## App Startup Sequence

Keep startup deterministic:

```text
configure process environment
→ initialize app-data directories
→ load/migrate launcher settings
→ recover interrupted launcher operations
→ reconcile workspace/server process state
→ initialize bounded providers
→ create/serve UI
```

Do not perform irreversible cleanup before the metadata needed for recovery is loaded.

## Shutdown / Close

Window close, application exit, and server stop are different actions.

Never assume closing the WebView means it is safe to terminate a running server. The runtime owner decides whether the app:

```text
blocks close
asks for confirmation
keeps process detached/recoverable
or performs an explicit stop
```

UI presents that policy; it does not invent it.

## Error Model

Use layered errors:

```text
provider/internal error
→ domain error/context
→ stable command error code + safe technical details
→ UI user-facing explanation
```

Examples of stable categories:

```text
WORKSPACE_UNAVAILABLE
SERVER_BUSY
INSUFFICIENT_STORAGE
UPDATE_VERIFICATION_FAILED
RECOVERY_REQUIRED
PERMISSION_DENIED
```

Do not leak tokens, credentials, raw auth headers, or unnecessary personal paths into user-visible error messages or support bundles.

## Dependency Rule

Adding a library/plugin/framework abstraction requires a concrete reason. Prefer the current stack and standard library when the existing architecture can express the feature cleanly.

Do not introduce:

```text
a frontend state framework for one global flag
a database for a small versioned settings document
a generic job framework before operation semantics are known
a second IPC wrapper over the existing canonical Tauri bridge
a second updater beside Tauri updater semantics
```

## Architecture Review Checklist

Before implementation:

```text
[ ] one durable owner identified
[ ] frontend is not source of durable truth
[ ] command is bounded and typed
[ ] operation state model is explicit when needed
[ ] persistence schema/default/migration path identified
[ ] failure/retry/restart behavior defined
[ ] no second registry/cache/queue/store added accidentally
[ ] security/capability scope is minimal
[ ] proof strategy identifies unit/static/package/live boundaries
```
