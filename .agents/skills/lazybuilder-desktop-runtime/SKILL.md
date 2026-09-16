---
name: lazybuilder-desktop-runtime
description: Own LazyBuilder Launcher engineering and desktop runtime semantics: Tauri/Rust architecture, workspace lifecycle, process ownership/recovery, long-running operations, settings persistence/migrations, self-update/distribution semantics, Windows app identity, diagnostics, provisioning, managed Java/Paper/core, startup safety, and desktop loopback control. Use for launcher/runtime semantics, not visual presentation, Paper world rules, shared Paper/Fabric protocol, or third-party plugin lifecycle.
---

# LazyBuilder Desktop Runtime

Own Launcher application/runtime semantics. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

The Launcher is a Tauri desktop application. Rust/Tauri is the trusted runtime core; Svelte is presentation.

## Entry gate

Use this Skill only when the decision changes desktop/runtime truth:

```text
workspace/server lifecycle
process start/stop/restart/recovery
managed Java/Paper/core provisioning
long-running operation semantics
persistent settings/migrations
backup/restore/runtime filesystem semantics
readiness/health/resource policy
launcher update/distribution/Windows identity
diagnostics/support state
desktop loopback HTTP/control
```

Do not enter merely because a Launcher screen is involved. Presentation stays with `lazybuilder-ui`.

Before mutation, identify the first evidence that separates runtime/domain failure from UI-only stale presentation.

## Owns

```text
Tauri/Rust command/service/state architecture
workspace create/open/adopt/activate/close/relocate semantics
server library registry semantics
Paper process lifecycle and detached recovery
managed Java/Paper/core provisioning and synchronization
one readiness/health authority
one long-running operation model
persistent settings/config + migrations
backup/restore/update/recovery semantics
launcher self-update and Windows app identity
diagnostics/support-data semantics
desktop loopback control/auth/session boundary
```

## Does not own

```text
visual hierarchy/layout/input/focus/toast/modal → lazybuilder-ui
Paper world lifecycle/import/export             → lazybuilder-world-management
third-party Paper plugin lifecycle              → lazybuilder-plugin-management
shared Paper/Fabric wire contracts              → lazybuilder-protocol
```

Desktop loopback HTTP remains here even when it calls World Manager. `shared/protocol` owns only neutral Paper/Fabric contracts.

## Context / reference routing

Read only what can change the decision:

| Need | Reference |
|---|---|
| Tauri/Rust/Svelte boundaries, state, settings | `references/launcher-architecture.md` |
| operations, progress, cancellation, retry, recovery | `references/operations-and-recovery.md` |
| Windows identity, NSIS, updater, release semantics | `references/windows-distribution-and-update.md` |
| launcher acceptance/quality audit | `references/launcher-quality-gates.md` |
| mature launcher architecture patterns | `references/real-launcher-patterns.md` |
| provenance of external research | `references/research-basis.md` |

Do not preload all references. External launcher projects are pattern references only; never copy branding/assets/substantial source.

## Failure taxonomy

Classify before editing:

```text
OWNERSHIP        state/business truth lives in the wrong layer
WORKSPACE        workspace identity/path/library lifecycle is wrong
PROCESS          PID/process-start/restart/detached reconciliation is wrong
READINESS        surfaces disagree on whether runtime is usable
OPERATION        long-running work duplicates, strands, cannot reconcile, or reports wrong state
PERSISTENCE      settings/config/schema/default/migration state is wrong
FILESYSTEM       path validation/staging/publication/backup/restore safety is wrong
PROVISIONING     managed Java/Paper/core acquisition or synchronization is wrong
UPDATE           launcher updater/channel/identity/restart semantics are wrong
LOOPBACK         desktop HTTP auth/session/control boundary is wrong
DIAGNOSTICS      support state/logging/error identity is insufficient or unsafe
PRESENTATION     canonical runtime result is correct; UI is stale/misleading
ENVIRONMENT      Windows/filesystem/process/security environment blocks correct source
UNKNOWN          evidence cannot yet separate the above
```

Local labels refine the global failure class from `development-discipline.md`; they do not replace it. `PRESENTATION` reclassifies to `UI_PRESENTATION`; `ENVIRONMENT` reclassifies to the narrowest supported runtime/environment global class; `OWNERSHIP` reclassifies through `ROUTING`; `UNKNOWN` must name the next separating evidence.

For `PRESENTATION`, hand off to `lazybuilder-ui`. For `UNKNOWN`, gather the smallest separating evidence; do not add fallback state.

## Architecture model

Use the smallest existing owner that fits:

```text
Svelte presentation
    ↓ typed invoke/event bridge
Tauri command boundary
    ↓
application service / operation orchestration
    ↓
semantic owner (workspace/process/update/settings/backup/readiness)
    ↓
platform/provider adapter (filesystem/Java/Paper/Windows/updater)
```

Rules:

- Rust owns runtime/filesystem/process/persistent truth;
- Svelte owns presentation state only;
- commands validate and delegate, they do not become second domain owners;
- providers exist only for real external/platform responsibilities;
- one concern gets one state authority, persistence path, and recovery path;
- use explicit finite lifecycle states for non-trivial operations instead of scattered booleans;
- do not add a second queue/cache/registry/updater/settings store/recovery system when an existing owner can extend cleanly.

## Canonical procedure

```text
name exact runtime responsibility
→ capture authoritative state + failing evidence
→ classify failure
→ find first wrong semantic owner
→ load only the matching reference when needed
→ check for duplicated UI/business/runtime ownership
→ define restart/partial-failure/retry/rollback semantics
→ reuse/consolidate one existing execution path
→ make the smallest recoverable mutation
→ prove the narrowest changed layer first
→ hand presentation-only residue to lazybuilder-ui
→ STOP
```

For non-trivial infrastructure, compare against `real-launcher-patterns.md` only after the actual LazyBuilder owner/problem is known.

## Runtime invariants

### State / operations

- one authoritative state per workspace/process/update/operation concern;
- UI reload/window recreation can query current state; events are not the sole truth;
- every long-running operation has one identity and finite lifecycle;
- only one conflicting operation per exclusive resource runs at once;
- retry resumes from authoritative state and must not duplicate committed side effects;
- a committed mutation is not relabeled failed only because a later refresh fails;
- cancellation exists only at safe cancellation boundaries;
- startup reconciliation is part of every persistent lifecycle design.

### Filesystem / destructive work

- validate canonical owned paths before destructive/copy/restore/update mutation;
- user-facing path strings are never deletion authority;
- prefer stage → validate → atomic publish/rename where practical;
- retain enough previous-valid state for deterministic recovery when replacing/destructive;
- ENOSPC, access denial, partial copy, lingering process, interrupted restart/update are first-class cases.

### Readiness / process

- one Paper process lifecycle owner and process identity authority;
- readiness is computed in Rust from authoritative runtime/filesystem/process state;
- all surfaces consume the same readiness result;
- expensive health probing is bounded/progressive;
- resource policy owns RAM/profile ceilings only; CPU scheduling remains JVM/OS managed.

### Persistence / updates

- one settings authority, centralized defaults, versioned migrations where needed;
- launcher self-update is separate from Paper/runtime update;
- update artifacts require integrity/authenticity verification before activation;
- updater state is finite, queryable, single-owner, restart-aware;
- installer/executable/Start Menu/taskbar/updater identity remain consistent;
- private signing/update keys never enter the repository.

### Diagnostics

- logs are bounded/rotated;
- durable technical failures use stable machine-readable identity plus human detail;
- support export includes build/channel/relevant runtime state but excludes secrets/tokens;
- operation history may aid diagnosis but must not become a second domain database.

## Proof matrix

```text
state transitions / validation / migration / serialization
→ focused unit/source-contract test

filesystem transaction / path guards / rollback selection
→ focused filesystem fixture/integration test

Tauri command/service wiring / compile contracts
→ Launcher source/build proof

Windows installer/update/app identity/filesystem locking
→ packaged Windows proof

Paper process start/stop/restart/detached recovery
→ local/live runtime proof using exact artifacts

presentation-only behavior
→ lazybuilder-ui proof lane
```

A green compile does not prove Windows process recovery or installer behavior.

## Handoff / exit contract

Handoff is sequential and carries only the typed result the next owner needs.

```text
canonical runtime/readiness/operation result
→ lazybuilder-ui
handoff: canonical ids + state + capabilities + progress/retry/cancel + stable result/error
UI must not re-scan filesystem/process/runtime to derive the same truth

Paper world-domain behavior
→ lazybuilder-world-management
handoff: authenticated desktop request context + canonical workspace/server identity only
World Management decides lifecycle/filesystem/domain semantics; Desktop transports the result without reinterpreting it

third-party plugin lifecycle
→ lazybuilder-plugin-management
handoff: canonical workspace/server context + requested plugin lifecycle intent
Plugin Management decides identity/dependency/compatibility/restart semantics

neutral Paper/Fabric payload
→ lazybuilder-protocol
handoff only when Paper↔Fabric wire meaning changes
Desktop loopback HTTP/Tauri IPC must never be converted into shared protocol merely for reuse
```

Finish when:

- one runtime/persistence/recovery owner remains;
- failure/restart/retry semantics are explicit;
- destructive work has the required recoverability boundary;
- matching proof is complete at the available context ceiling;
- the next owner can proceed from the handoff result without reopening Desktop Runtime truth;
- remaining UI/live/native residue is named precisely.

Do not continue into visual redesign, generic framework work, release infrastructure, or speculative future-proofing after the runtime contract is satisfied.