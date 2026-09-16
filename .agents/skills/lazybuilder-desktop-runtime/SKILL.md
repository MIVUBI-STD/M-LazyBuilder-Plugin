---
name: lazybuilder-desktop-runtime
description: Own LazyBuilder Launcher engineering and desktop runtime semantics: Tauri/Rust architecture, workspace lifecycle, process ownership/recovery, long-running operations, settings persistence/migrations, self-update/distribution semantics, Windows app identity, diagnostics, provisioning, managed Java/Paper/core, startup safety, and desktop loopback control. Use for launcher/runtime semantics, not visual presentation, Paper world rules, shared Paper/Fabric protocol, or third-party plugin lifecycle.
---

# LazyBuilder Desktop Runtime

Own Launcher application/runtime semantics. Global diagnosis/proof rules come from `docs/04-system/development-discipline.md`; cross-owner selection/handoff comes from `docs/04-system/skill-routing.md`.

This Skill is consumer-neutral: ChatGPT and Codex follow the same semantic procedure. Adapt only execution steps to tools actually available; never assume shell/local workspace/live server access or claim proof that was not observed.

Rust/Tauri is trusted runtime authority; Svelte is presentation.

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

Do not enter merely because a Launcher screen is involved.

## Owns

```text
Tauri/Rust command/service/state architecture
workspace create/open/adopt/activate/close/relocate semantics
server library registry
Paper process lifecycle + detached recovery
managed Java/Paper/core provisioning/synchronization
one readiness/health authority
one long-running operation model
persistent settings/config + migrations
backup/restore/update/recovery semantics
launcher self-update + Windows app identity
diagnostics/support-data semantics
desktop loopback auth/session/control boundary
```

Does not own UI presentation, Paper world semantics, third-party plugin lifecycle, or neutral Paper↔Fabric contracts.

## Reference routing

Load only when material:

```text
Tauri/Rust/Svelte boundaries, state, settings → references/launcher-architecture.md
operations/retry/cancel/recovery             → references/operations-and-recovery.md
Windows/NSIS/update/release                  → references/windows-distribution-and-update.md
launcher acceptance audit                    → references/launcher-quality-gates.md
mature launcher patterns                     → references/real-launcher-patterns.md
external research provenance                 → references/research-basis.md
```

## Failure taxonomy

```text
OWNERSHIP    state/business truth lives in wrong layer
WORKSPACE    workspace identity/path/library lifecycle wrong
PROCESS      PID/start/restart/detached reconciliation wrong
READINESS    canonical runtime usability result wrong/inconsistent
OPERATION    long-running work duplicates/strands/reconciles wrongly
PERSISTENCE  settings/config/schema/default/migration wrong
FILESYSTEM   path/staging/publication/backup/restore safety wrong
PROVISIONING managed Java/Paper/core acquisition/sync wrong
UPDATE       launcher update/channel/identity/restart semantics wrong
LOOPBACK     desktop HTTP auth/session/control boundary wrong
DIAGNOSTICS  support/log/error evidence insufficient or unsafe
PRESENTATION canonical runtime result correct; UI wrong
ENVIRONMENT  Windows/filesystem/process environment blocks correct source
UNKNOWN      next separating evidence required
```

Cross-owner labels use the global bridge in `development-discipline.md`; do not patch `PRESENTATION`, `OWNERSHIP`, or environment-only failures inside Desktop Runtime when another owner is proven wrong.

## Architecture model

```text
Svelte presentation
→ typed invoke/event bridge
→ Tauri command boundary
→ application service/orchestration
→ semantic owner
→ platform/provider adapter
```

Rules:

- Rust owns runtime/filesystem/process/persistent truth;
- commands validate/delegate, not duplicate domain logic;
- providers exist only for real external/platform responsibilities;
- one concern gets one state authority, persistence path, and recovery path;
- non-trivial lifecycle uses explicit finite state rather than conflicting booleans;
- do not add a second queue/cache/registry/updater/settings/recovery system when an existing owner can extend cleanly.

## Canonical procedure

```text
name exact runtime responsibility
→ capture authoritative state + failing evidence
→ classify local subtype
→ confirm Desktop Runtime remains first wrong owner
→ load only matching reference when needed
→ define restart/partial-failure/retry/rollback semantics
→ reuse/consolidate one execution path
→ smallest recoverable mutation
→ matching proof available in the current context
→ typed handoff only if ownership changes
→ STOP
```

If the current consumer cannot execute the required local/native/live proof, finish all lower-context work and name that exact residue instead of fabricating completion.

## Runtime invariants

### State / operations

- one authoritative state per workspace/process/update/operation concern;
- UI reload can query current state; events are not sole truth;
- every long-running operation has one identity + finite lifecycle;
- only one conflicting operation per exclusive resource;
- retry uses authoritative state and cannot duplicate committed side effects;
- committed mutation is not relabeled failed because a later refresh fails;
- cancellation exists only at safe boundaries;
- persistent lifecycle includes startup reconciliation.

### Filesystem / destructive work

- validate canonical owned paths before mutation;
- user-facing paths are never deletion authority;
- prefer stage → validate → atomic publish/rename where practical;
- retain enough previous-valid state for deterministic recovery when destructive/replacing;
- ENOSPC, access denial, partial copy, lingering process, interrupted restart/update are first-class cases.

### Readiness / process

- one Paper process identity/lifecycle authority;
- one Rust readiness result consumed by all surfaces;
- expensive health probing is bounded/progressive;
- resource policy owns RAM/profile ceilings; CPU scheduling remains JVM/OS managed.

### Persistence / updates

- one settings authority + centralized defaults + versioned migrations where needed;
- launcher update is separate from Paper/runtime update;
- update artifacts require integrity/authenticity verification before activation;
- updater state is finite/queryable/single-owner/restart-aware;
- installer/executable/Start Menu/taskbar/update identity remains consistent;
- private signing/update keys never enter repository source.

### Diagnostics

- logs/history are bounded;
- technical failures expose stable machine-readable identity + human detail;
- support export excludes secrets/tokens;
- diagnostics never become a second domain database.

## Proof matrix

```text
state transitions / validation / migration / serialization → EXECUTED_SOURCE
filesystem transaction / path guards / rollback          → INTEGRATION_FIXTURE
Tauri command/service wiring / compile contracts          → EXECUTED_SOURCE
installer/package identity/basic install-start contract   → PACKAGE_SMOKE
Windows shell/DPI/dialog/update-install behavior           → NATIVE_ACCEPTANCE
Paper process lifecycle/recovery                           → LIVE_RUNTIME
presentation-only behavior                                → lazybuilder-ui
```

`PACKAGE_SMOKE` is not `NATIVE_ACCEPTANCE`; Paper boot is not feature proof unless the changed lifecycle path is exercised.

## Handoff / exit

Use canonical typed handoffs from `skill-routing.md`. Desktop-specific outputs commonly include:

```text
to ui
→ canonical ids + runtime/readiness/operation state + capabilities + stable result/error

to world-management
→ authenticated desktop request context + canonical workspace/server identity

to plugin-management
→ canonical workspace/server context + requested third-party plugin lifecycle intent
```

Desktop loopback/Tauri IPC never becomes shared Paper↔Fabric protocol merely for reuse.

Finish when runtime/persistence/recovery ownership is singular, failure/retry/restart semantics are explicit, destructive work is recoverable where required, and matching proof covers the changed claim at the available context ceiling. Stop before visual redesign, generic framework work, or unrelated future-proofing.