# Production Launcher Source Patterns

This reference distills architecture patterns from real open-source launchers. It is a design reference only. Do not copy source, assets, product-specific APIs, or licensing-sensitive implementation. LazyBuilder source/docs remain authoritative.

## Candidate set

The selected repositories cover different strengths rather than acting as one template:

```text
PrismLauncher/PrismLauncher
→ mature task/progress model, settings, single-instance process behavior, logging lifecycle

gorilla-devs/GDLauncher-Carbon
→ explicit updater state machine, update locking, release channels, main/UI bridge, progress/error propagation

HydroRoll-Team/DropOut
→ Tauri 2 + Rust launcher core, backend-authoritative readiness, resumable/cancellable downloads, progress events, large-library probing

ATLauncher/ATLauncher
→ regression-test discipline around filesystem and launcher classes
```

Use the smallest relevant pattern. Never rebuild LazyBuilder to match another launcher wholesale.

---

## 1. PrismLauncher — task and application lifecycle patterns

Useful source areas:

```text
launcher/tasks/Task.h
launcher/Application.cpp
launcher/settings/*
launcher/InstanceList.*
launcher/updater/*
```

### Pattern: one reusable task contract

PrismLauncher models long-running work as a Task with explicit lifecycle state instead of feature-specific loading flags.

Conceptually useful shape:

```text
Inactive
→ Running
→ Succeeded | Failed | AbortedByUser
```

Task data includes:

```text
stable task identity
status
technical details
current / total progress
warnings
abort capability
multi-step progress
failure reason
```

LazyBuilder adoption:

```text
OperationId
OperationKind
OperationState
phase
status
details
current / total
warnings/error
canCancel
createdAt / completedAt
```

Do not mirror Qt signals/classes. Adopt the semantic contract in Rust and expose typed snapshots/events to Svelte.

### Pattern: explicit step progress

A complex operation may report multiple logical steps rather than one fake percentage.

LazyBuilder should prefer:

```text
Duplicate Server
  validate         Succeeded
  estimate         Succeeded
  copy             Running  8.2 / 14.0 GB
  verify           Waiting
  publish          Waiting
```

instead of inventing a single percentage when totals are not meaningful.

### Pattern: application lifecycle is centralized

Prism's application bootstrap owns single-instance behavior, logging initialization/rotation, paths, settings, instance loading, account/runtime initialization, and update setup.

LazyBuilder adoption:

```text
one startup coordinator
→ runtime temp/environment
→ logging
→ settings migration/load
→ workspace deletion/recovery reconciliation
→ process reconciliation
→ operation reconciliation
→ updater readiness
→ UI ready
```

Avoid putting independent startup side effects in unrelated Tauri commands/components.

### Pattern: rotating launcher logs

Keep a bounded number of previous launcher logs instead of one unbounded file or only ephemeral console output.

LazyBuilder target:

```text
launcher.log
launcher.1.log
launcher.2.log
launcher.3.log
```

Exact retention can differ, but it must be bounded and support-oriented.

---

## 2. GDLauncher Carbon — updater and cross-process state patterns

Useful source areas:

```text
apps/desktop/packages/main/autoUpdater.ts
apps/desktop/packages/preload/autoupdate.ts
apps/desktop/packages/mainWindow/src/utils/updater.tsx
```

### Pattern: updater is a state machine

GDLauncher Carbon exposes explicit updater state rather than treating update as a button click:

```text
idle
checking
downloading
downloaded
no-update
error
```

with:

```text
update metadata
progress
structured error
```

LazyBuilder adoption:

```text
Idle
Checking
Available
Downloading
ReadyToInstall
Installing
RestartRequired
NoUpdate
Failed
```

The Rust updater owner stores the state. Svelte subscribes/renders it.

### Pattern: operation lock prevents concurrent checks

GDLauncher uses one updater lock so a second update request returns a busy state rather than creating competing updater flows.

LazyBuilder rule:

```text
one launcher-update operation at a time
```

A second caller consumes the existing operation snapshot instead of creating a second updater.

### Pattern: release channel is explicit state

GDLauncher maps stable/beta/alpha into update behavior and downgrade policy.

LazyBuilder initially keeps this narrower:

```text
Stable
Preview
```

Channel changes belong to persistent Launcher settings and update policy, not UI-local state.

### Pattern: state is queryable and event-driven

GDLauncher provides both:

```text
get current updater state
subscribe to updater state changes
```

LazyBuilder should use the same general contract for all long-running launcher operations:

```text
operation_snapshot(id)
operation_list()
operation event stream
```

Events are acceleration, not the only truth. A reopened/reloaded UI can always request the current snapshot.

### Pattern: testable update feed boundary

GDLauncher can redirect its update feed in E2E so automated tests do not depend on production update infrastructure.

LazyBuilder adoption:

```text
production updater endpoint/signature config
+ test-only/local mock update endpoint under explicit test build/config
```

Never silently allow arbitrary update feed override in a production build.

---

## 3. DropOut — Tauri/Rust launcher patterns closest to LazyBuilder

Useful source areas:

```text
src-tauri/src/core/downloader.rs
src-tauri/src/main.rs
packages/ui/src/lib/launch-readiness.ts
packages/ui/src/client.ts
packages/ui/src/pages/instances/*
packages/docs/content/en/development/*
```

### Pattern: Rust owns side effects

DropOut's product architecture keeps authentication/download/Java/version/instance/launch orchestration in the Rust/Tauri side while the UI invokes commands and renders results.

This matches LazyBuilder's intended architecture and reinforces:

```text
Svelte = presentation/client
Rust = filesystem/process/network/persistent-domain authority
```

### Pattern: backend-authoritative readiness

The UI does not infer readiness from frontend configuration alone. It asks one backend readiness contract.

LazyBuilder adoption:

```text
ServerHealth / ServerReadiness
```

should be the canonical input for:

```text
Server Library status
Overview primary action
Repair recommendation
Start eligibility
tray/shortcut action in the future
```

Do not independently calculate "ready" in each Svelte page.

### Pattern: same readiness across surfaces

DropOut reuses the same readiness check in home, instance library, and tray actions.

LazyBuilder rule:

```text
one semantic health/readiness result
→ many presentation consumers
```

### Pattern: progressive probing for large libraries

DropOut checks visible instances progressively instead of probing hundreds of instances simultaneously.

LazyBuilder should use bounded/background refresh when Server Library grows:

```text
load registry metadata immediately
→ render library
→ health-check visible/priority servers first
→ bounded refresh remaining entries
```

Do not make Server Library startup proportional to every expensive runtime check.

### Pattern: resumable download + progress event + cancellation

DropOut uses resumable large-file downloads, progress events, checksum verification, and explicit cancellation for Java/runtime downloads.

LazyBuilder adoption for managed downloads:

```text
.partial/staging download
resume metadata where provider supports it
current bytes / total bytes
speed/phase when useful
checksum/signature verification
safe cancellation boundary
atomic promotion only after verification
```

This applies to future Launcher self-update and managed runtimes, not arbitrary plugin/mod download ownership.

---

## 4. ATLauncher — regression-test discipline

Useful source:

```text
TESTING.md
src/test/*
```

The useful pattern is intentionally simple:

```text
new/changed class behavior
→ colocated/named regression test
filesystem behavior
→ isolated temporary directory
```

LazyBuilder adoption:

- lifecycle/state-machine logic gets unit tests;
- filesystem mutation tests use isolated temp roots;
- tests prove source remains untouched on failed duplicate/restore/update;
- destructive path validation gets negative tests;
- interrupted/recovery state gets explicit regression cases.

Do not adopt ATLauncher's older Java architecture merely because it is mature.

---

# Unified LazyBuilder synthesis

## A. One operation model

Prism task semantics + GDLauncher query/event pattern + DropOut progress/cancellation become one LazyBuilder operation authority:

```text
Operation
├── id
├── kind
├── scope/resource id
├── state
├── phase
├── status
├── details
├── progress { current, total, unit }
├── steps[]
├── canCancel
├── error
├── warnings[]
├── createdAt
└── completedAt
```

Initial operation states:

```text
Queued
Running
Succeeded
Failed
Cancelling
Cancelled
RecoveryRequired
```

Feature-specific domain state remains with its owner. The operation registry tracks execution, not a duplicate copy of the domain database.

## B. One readiness/health model

DropOut's strongest pattern should be adopted directly in concept:

```text
Rust computes canonical readiness/health
→ Server Library
→ Overview
→ Start/Repair eligibility
→ future notifications/tray
```

Suggested result shape:

```text
status: Ready | NeedsAttention | Unavailable | Busy
checks[]:
  id
  state
  summary
  technicalDetails?
  repairable
primaryAction
```

## C. One startup reconciliation path

Prism application lifecycle + LazyBuilder's existing recovery systems become:

```text
bootstrap environment
→ logging
→ settings migration
→ recover pending filesystem transactions
→ reconcile Paper process marker
→ reconcile interrupted operations
→ initialize updater state
→ load server registry
→ begin bounded health refresh
→ UI ready
```

## D. One updater state machine

Use GDLauncher as state-model inspiration but implement with Tauri updater/security requirements:

```text
Idle
Checking
Available
Downloading
Verifying
ReadyToInstall
Installing
RestartRequired
NoUpdate
Failed
```

One updater operation at a time. Current state must be queryable after UI reload.

## E. Bounded observability

Professional launcher diagnostics should combine:

```text
rotating launcher logs
operation history summary
structured errors with stable codes
support bundle exporter
version/build/channel information
```

Never persist secrets merely for diagnostics.

---

# Patterns deliberately rejected

Do not adopt these simply because another launcher has them:

```text
account/skin/store/social systems
mod marketplace architecture
ads/telemetry architecture
multi-platform abstraction before Windows need exists
system tray residency without a concrete LazyBuilder workflow
feature-specific task managers
frontend-owned durable launcher state
production-configurable arbitrary updater feed
copying another project's source/assets
```

# Selection rule

When implementing a Launcher feature, ask:

```text
Is this execution state?        → unified operation model
Is this server readiness?       → canonical backend health/readiness
Is this application bootstrap?  → startup coordinator/reconciliation
Is this app update?             → one updater state machine
Is this user-facing history?    → bounded diagnostics/operation history
Is it only presentation?        → lazybuilder-ui
```

Prefer these shared patterns before adding a new manager, queue, registry, store, worker, or recovery mechanism.
