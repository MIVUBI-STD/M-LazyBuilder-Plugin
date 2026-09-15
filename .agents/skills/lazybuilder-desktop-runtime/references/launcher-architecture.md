# Launcher Architecture Reference

Use for changes to Tauri/Rust/Svelte boundaries, command orchestration, persistent settings, Launcher operation state, or launcher-wide authority.

## Current process model

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

Svelte is presentation. Rust/Tauri owns trusted runtime, filesystem, process, persistence, and durable application semantics.

## Current authority examples

Current source already contains concrete owners including:

```text
launcher_settings.rs  → versioned Launcher settings
operations.rs         → bounded Launcher operation snapshots/history/cancellation
workspace/server owners
provisioning.rs       → managed provisioning
java_runtime.rs       → managed Java boundary
paper_provider.rs     → Paper provider boundary
diagnostics.rs        → Launcher diagnostics/log context
client_integration.rs → selected Modrinth/LazyBuilder client integration
core_modules.rs       → bundled LazyBuilder core synchronization
```

Do not create a `future_*` replacement for an authority that already exists. Extend or consolidate the current owner first.

## Layer responsibilities

### Svelte presentation

Owns rendering, interaction-local state, forms, navigation, focus/accessibility, pending presentation, and formatting.

It may cache read models for rendering, but durable truth must be refreshable from Rust.

### Tauri command boundary

Owns input decoding/validation, bounded command exposure, mapping domain errors to stable command results, and delegation to one semantic owner.

Commands should not become large second implementations of persistence, filesystem mutation, process control, or business rules.

### Application/domain owner

Use one owner per durable concern. Existing examples include Launcher settings, operations, workspace/process/runtime owners, provisioning, diagnostics, and client integration.

Introduce a new domain owner only when a distinct durable responsibility is proven and cannot fit an existing authority cleanly.

### Provider/platform adapter

Use for a real external/platform boundary such as managed Java, Paper release/provider integration, Windows installer/update integration, filesystem/platform primitives, or network release metadata.

Separate an adapter only when the external mechanism has materially different failure/recovery behavior.

## IPC contract rules

- prefer typed request/result DTOs;
- use camelCase serialization consistently across Rust/TypeScript;
- stable machine-readable error codes belong at the command/runtime boundary;
- user-facing wording belongs to UI unless it is itself part of a durable contract;
- expose bounded domain commands rather than arbitrary filesystem/process primitives;
- minimize Tauri capabilities to the required window/runtime surface;
- secrets/signing keys never enter frontend code.

## State-machine rule

Use an explicit finite state model when state controls legal actions, concurrency, restart, or recovery.

For the canonical Launcher operation layer, current durable execution states are:

```text
Queued
Running
Succeeded
Failed
Cancelling
Cancelled
RecoveryRequired
```

Detailed work progression belongs in `phase`/status/progress rather than expanding the global enum for every feature-specific step.

A domain-specific state machine may differ when the domain itself has distinct durable states. Do not force every concern into the generic operation enum.

Avoid scattered booleans that permit impossible combinations.

## Persistent Launcher settings

`launcher_settings.rs` is the current settings authority. Current settings include schema version, last-server behavior, close confirmation, automatic update checking, and update channel.

Rules:

1. one settings document/authority;
2. schema version is explicit;
3. defaults live in Rust authority, not duplicated in Svelte;
4. migrations are deterministic and tested;
5. risky writes preserve previous-valid state where appropriate;
6. unknown/newer schema fails safely rather than silently resetting user data;
7. add a setting only for a real user decision, not an internal implementation flag.

## Startup ordering

Keep startup deterministic and recovery-first:

```text
configure process environment
→ initialize application-owned directories/logging
→ load/migrate Launcher settings
→ reconcile persistent/recoverable runtime state
→ reconcile workspace/server process state
→ initialize bounded providers
→ expose/query current authoritative state
→ UI ready
```

Do not perform irreversible cleanup before metadata needed for recovery has been loaded.

## Shutdown / close

Window close, Launcher exit, and Paper server stop are different actions.

Never assume closing the WebView implies permission to terminate the server. Runtime policy decides whether to block, confirm, detach/recover, or explicitly stop; UI only presents the result.

## Error model

```text
provider/internal error
→ domain context
→ stable command/runtime error code + safe details
→ UI explanation/recovery
```

Never leak credentials, auth headers, private signing material, or unnecessary personal paths into UI/support output.

## Dependency rule

Prefer the current stack and standard library where they already express the feature cleanly.

Do not introduce by default:

```text
frontend state framework for a small shared flag
database for the versioned settings document
second operation registry/queue
second IPC facade beside the canonical bridge
second updater/runtime authority
```

## Review checklist

Before implementation:

```text
[ ] current semantic owner identified
[ ] frontend is not durable authority
[ ] command/request boundary is typed and bounded
[ ] current operation/settings/readiness owner reused where applicable
[ ] state machine only added where state constrains behavior
[ ] failure/retry/restart semantics defined
[ ] persistence/recovery path identified when durable state changes
[ ] no duplicate registry/cache/queue/store introduced
[ ] security/capability scope remains minimal
[ ] proof plan distinguishes source/package/native runtime boundaries
```
