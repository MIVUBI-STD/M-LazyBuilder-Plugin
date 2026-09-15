# LazyBuilder Launcher Foundation Audit

Status: source-side architecture audit before new Launcher feature implementation.

Scope: `apps/launcher` only. Plugin/mod/domain internals are out of scope except where the Launcher consumes their existing contracts.

Reference basis:

- `.agents/skills/lazybuilder-desktop-runtime/SKILL.md`
- `.agents/skills/lazybuilder-desktop-runtime/references/real-launcher-patterns.md`
- PrismLauncher task/application lifecycle patterns
- GDLauncher Carbon updater-state patterns
- DropOut Tauri/Rust readiness/download patterns
- ATLauncher regression-test discipline

## Executive finding

LazyBuilder already has several strong product/runtime foundations:

```text
canonical Tauri bridge
structured Rust command errors
workspace registry
process ownership/recovery
transactional workspace delete/duplicate safety
managed Java/Paper runtime
Windows NSIS packaging
launcher diagnostics log
source/CI proof gates
```

The main remaining Launcher architecture gap is that these capabilities are still orchestrated mostly feature-by-feature. Before adding Activity Center, self-update, server backup/restore, health/repair, and central settings, the Launcher needs one internal application framework for:

```text
operations
startup/reconciliation
launcher settings + migrations
health/readiness
error/recovery metadata
diagnostics correlation
update state
```

Do not build those features as independent managers.

---

## 1. Existing strengths to preserve

### 1.1 Canonical frontend bridge

`src/app/bridge/runtimeApi.ts` already centralizes production `invoke()` calls and normalizes Rust command errors into `RuntimeError`.

`runtimeProductFacade.ts` is acceptable because visual-preview mode swaps only the data source while production continues to use the same UI contract. This is a test/presentation adapter, not a second runtime authority.

Decision: preserve one public frontend bridge.

### 1.2 Rust owns persistent/runtime semantics

Workspace, process, filesystem, resource and provisioning rules are already in Rust. Do not move these into Svelte stores while improving the Launcher.

### 1.3 Command error envelope exists

`CommandError { code, message }` and frontend `RuntimeError` are a good base. Extend rather than replace them.

### 1.4 Filesystem mutation safety is materially stronger than typical early-stage launchers

Duplicate/delete use registered workspace identity, process guards, staging, rollback/recovery intent and path validation. Future Backup/Restore/Repair must reuse these principles.

---

## 2. P0 gap — no Launcher-wide operation framework

### Current state

Long-running Launcher actions use different local mechanisms.

Examples in `App.svelte` include independent booleans such as:

```text
creating
adopting
provisioningServer
acceptingEula
updatingPaper
managementBusy
estimateLoading
```

Other pages have their own `busy`, `loaded`, `message`, `error` states.

World Management already has a domain-specific task model, but there is no generic Launcher operation model for desktop-owned work.

### Risk

Adding Backup, Restore, Repair, Self Update and Activity Center now would likely create:

```text
backup progress state
restore progress state
repair progress state
updater progress state
provision progress state
```

with different retry/cancel/error semantics.

### Target

Add one Rust-owned Launcher operation contract:

```text
OperationId
OperationKind
ResourceKey
OperationState
Phase
Status
Details
Progress { current, total, unit }
Steps[]
Warnings[]
Error?
CanCancel
CreatedAt
UpdatedAt
CompletedAt?
```

Suggested finite states:

```text
Queued
Running
Cancelling
Succeeded
Failed
Cancelled
RecoveryRequired
```

Required APIs conceptually:

```text
operation_list()
operation_get(id)
operation_cancel(id)
operation_retry(id)  # only for explicitly retry-safe operations
```

Events are a responsiveness layer only. Snapshot/query remains authority.

### Rule

Do not force every instant command through the operation framework. Use it for work that is long-running, multi-phase, cancellable, restart-relevant, or worth retaining in Activity history.

---

## 3. P0 gap — startup is initialization, not yet a coordinator

### Current state

`app_bootstrap.rs` currently performs roughly:

```text
prepare runtime TEMP/TMP
log launcher start
initialize workspace registry / pending deletion recovery
construct Tauri state
register commands
run app
```

This is valid but insufficient for a mature Launcher once more persistent systems exist.

### Target startup coordinator

The desired sequence is:

```text
runtime environment
→ diagnostics/logging
→ launcher settings load + migration
→ pending filesystem recovery
→ process reconciliation
→ operation reconciliation
→ updater initialization
→ registry/server library readiness
→ bounded health refresh
→ UI ready
```

Each stage must have an explicit result. Non-fatal degraded startup must be distinguishable from healthy startup.

Do not silently log every initialization failure and continue without exposing degraded state to the UI.

---

## 4. P0 gap — no central Launcher settings authority

### Current state

The current Settings page is primarily server RAM/resource configuration plus diagnostic display.

`server_config.rs` owns server-manager runtime settings and already uses safe temporary replacement, but it is not a Launcher settings system. Its persisted JSON also relies on serde defaults rather than an explicit application-settings schema/version migration path.

### Required separation

```text
LauncherSettings
→ application-level behavior

ServerConfig
→ active server runtime behavior
```

Do not merge them into one file.

### Initial LauncherSettings scope

Only add settings when features require them, for example:

```text
schemaVersion
updateChannel
checkLauncherUpdates
rememberLastServer
closeBehavior
serverLibraryDefaultLocation
notification preferences
```

Do not pre-populate dozens of speculative settings.

### Migration requirement

Every durable application-settings schema must have:

```text
schema version
central defaults
forward migration
safe write/replace
recoverable previous-valid state for risky migration
```

Svelte must not own competing defaults.

---

## 5. P0 gap — Server Health / readiness is fragmented

### Current state

The Launcher currently exposes several related concepts independently:

```text
WorkspaceProvisioningStatus
ServerPreflight
ServerSnapshot / ServerHealth
DiagnosticSummary
RuntimeUpdateStatus
```

These are valid domain snapshots, but there is not yet one Launcher-level answer to:

> Can this server be safely opened/started/maintained, and what action should the user take next?

### Target

Add one backend-derived health/readiness aggregation for Launcher surfaces.

Conceptually:

```text
ServerHealthSummary
  serverId
  status: Ready | Busy | NeedsAttention | Unavailable
  checks[]
  primaryAction?
  technicalDetails?
  refreshedAt
```

Example checks:

```text
workspace identity
workspace location
Java runtime
Paper runtime
server configuration
process ownership
pending recovery state
storage availability where relevant
```

Do not reimplement these checks. Aggregate existing authorities.

The same summary should eventually feed:

```text
Server Library status
Overview status
Start eligibility
Repair entry point
Diagnostics
```

For large libraries, refresh progressively with bounded concurrency. Do not probe every server deeply before rendering the library.

---

## 6. P1 gap — error model needs recovery metadata

### Current state

The structured `{ code, message }` command error contract is a good base, but presentation still contains repeated `friendlyError()` logic and feature-specific message handling.

### Target

Keep technical codes stable and extend errors/results only when useful with bounded metadata such as:

```text
code
message
recoverable
suggestedAction?
details?
operationId?
```

Do not make the backend own final prose/layout for every UI surface. The backend owns classification and recovery semantics; UI owns presentation.

Examples:

```text
WORKSPACE_UNAVAILABLE
→ recoverable = true
→ suggestedAction = LocateWorkspace

SERVER_BUSY
→ recoverable = true
→ suggestedAction = StopServer

UPDATE_SIGNATURE_INVALID
→ recoverable = false for that artifact
```

---

## 7. P1 gap — diagnostics are useful but too narrow for mature support

### Current state

`diagnostics.rs` currently writes one active `launcher.log`, rotating to one `launcher.previous.log` at 1 MiB.

This is already better than console-only logging.

### Improvements

Use bounded rolling retention rather than one previous file, for example:

```text
launcher.log
launcher.1.log
launcher.2.log
launcher.3.log
```

Future operation framework should add correlation fields in messages:

```text
operationId
operationKind
serverId
phase
```

Do not require structured JSON logging unless it solves a concrete support need.

Future Diagnostics surface should be able to:

```text
run health diagnostics
open logs directory
copy version/system summary
export sanitized support bundle
```

No automatic upload.

---

## 8. P1 gap — App.svelte is carrying too much orchestration

### Current state

`App.svelte` is approximately 40 KB and currently owns significant Server Library/create/adopt/provision/update/management orchestration in addition to application navigation/presentation.

This does not mean it should be split mechanically by line count.

### Target

As the new foundations land, move semantic orchestration out of `App.svelte` into the canonical Rust/bridge contract. Presentation components may then be split by stable product responsibility, for example:

```text
ServerLibrary
CreateServerFlow
AdoptServerFlow
ServerManagementDialogs
ApplicationShell
```

Do not introduce a frontend business-logic store merely to make App.svelte shorter.

---

## 9. P1 gap — updater framework is absent

Do not implement self-update until operation + settings foundations are defined.

Target update state machine:

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

Properties:

```text
one updater owner
one active update operation
Stable first; Preview only if intentionally enabled
signed/authenticated artifacts
snapshot + events
restart-aware activation
pre-update app-data safety for risky migrations
```

Launcher self-update remains separate from Paper runtime updates.

---

## 10. Architecture order

Recommended order before major product features:

```text
F0  Launcher Operation Framework
F1  Startup Coordinator / reconciliation
F2  Launcher Settings + schema migration
F3  Server Health / Readiness aggregation
F4  Error recovery metadata + common frontend handling
F5  Diagnostics/log correlation + bounded retention
```

Then product features can be implemented on top:

```text
Activity Center       → F0
Self Update           → F0 + F1 + F2 + F4 + F5
Backup / Restore      → F0 + F1 + F4 + F5
Health / Repair       → F1 + F3 + F4 + F5
Central Settings UI   → F2
Missing-location UX   → F3 + F4
```

This avoids implementing five separate infrastructure systems accidentally.

---

## 11. Explicit non-goals for foundation work

Do not use this refactor to add:

```text
new plugin/mod semantics
new world-manager semantics
account management
store/marketplace
news feed
system tray residency
cloud sync
telemetry service
SQLite just because mature launchers use databases
new frontend state framework without evidence
```

The goal is consolidation, not product expansion.

---

## 12. Acceptance gate before Local PC validation

Foundation work is source-ready only when:

```text
one Launcher operation authority exists
no duplicate feature-specific operation manager is introduced
startup reconciliation is explicit and testable
Launcher settings are distinct from ServerConfig and versioned
Server Health is backend-authoritative and aggregates existing checks
error classification can carry recovery intent
logs remain bounded and diagnostics can correlate operations
frontend uses the canonical bridge and does not own durable truth
Rust tests cover state/failure/restart-relevant transitions
existing installer/package proof remains green
```

Only after source-side foundation and the desired product features are remotely verified should Local PC acceptance resume.
