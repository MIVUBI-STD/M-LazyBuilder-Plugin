# Launcher Quality Gates

Use this checklist before calling a non-trivial Launcher feature professional or release-ready. It is intentionally product-quality focused while leaving visual design decisions to `lazybuilder-ui`.

## Core Product Model

LazyBuilder is a **local Minecraft server manager**. Do not imitate large launchers by accumulating unrelated game/store/social features. Match their engineering quality in lifecycle, update, recovery, observability, accessibility, and distribution.

## P0 Product Capabilities

A mature Launcher should eventually have one coherent implementation for:

```text
self update
operation/activity tracking
server backup + restore
server health + repair
```

These capabilities should reuse common authorities rather than each inventing its own job/history/recovery system.

## Operation Quality

Any long-running task should expose enough semantic state for UI to render:

```text
kind
target
phase
progress when measurable
started time
success result
stable failure code/details
retry availability
cancel availability only when safe
```

The Launcher must remain usable while background work runs unless a true global lock is required.

## Server Library Semantics

The server library should support, at minimum, durable semantics for:

```text
create
adopt/open
activate/close
duplicate
remove from library
permanent delete
relocate missing server
health/attention state
backup/restore
```

UI search/sort/filter is a `lazybuilder-ui` concern, but this Skill must provide stable metadata/state to support it without frontend filesystem scanning.

## Missing-Location Recovery

When a registered path disappears:

```text
preserve registry entry
mark unavailable
allow explicit relocate
validate selected replacement against workspace identity/manifest
update registry atomically
```

Never silently attach a different folder because its display name matches.

## Health / Repair

Health is structured state, not generic exception text.

Suggested checks include only canonical facts the Launcher owns:

```text
workspace manifest/identity
workspace directory accessibility
managed Java availability
Paper runtime presence/compatibility
required core runtime files
process marker vs actual process reconciliation
settings/config parseability
pending recovery intents
storage availability for requested operations
```

Repair must state or return the planned recoverable actions and never become a generic reset button.

## Backup / Restore

Professional restore semantics:

```text
verify server offline/process-free
validate backup metadata/integrity
create pre-restore safety snapshot when feasible
stage restore
validate
publish atomically/safely
reconcile registry/runtime state
retain recovery metadata until success
```

Backup retention policy should be explicit when introduced. Never delete old backups silently without a documented retention rule.

## Settings Quality

A central settings authority should eventually cover only meaningful Launcher decisions, for example:

```text
update policy/channel
default server location
last-opened behavior
close behavior while server runs
diagnostic/logging preferences
backup defaults/retention when implemented
```

Avoid exposing internal implementation flags as user settings.

## Notification / Error Semantics

Backend should classify outcomes so UI can choose presentation consistently.

Examples:

```text
success / informational
attention required
recoverable failure
blocking failure
destructive confirmation required
```

Stable machine-readable error codes must be separate from user-facing wording.

## Diagnostics / Supportability

Launcher should be able to produce a sanitized support snapshot containing only useful application/runtime context:

```text
Launcher version + commit/build
Windows/architecture info
WebView2/runtime availability when relevant
Java/Paper versions
workspace manifest metadata
operation state/error history (bounded)
latest bounded Launcher/server startup logs
health-check results
```

Exclude:

```text
auth tokens
passwords/private keys
Microsoft credentials
world contents
large arbitrary user files
unnecessary personal paths when they can be redacted
```

## First-Run and Empty-State Semantics

UI owns copy/layout, but Launcher semantics must support a clean first-run path without hidden developer prerequisites:

```text
install Launcher
→ open
→ create or adopt server
→ Launcher provisions required managed runtime
→ explicit EULA acceptance
→ ready
```

A normal user should not have to run Maven, Gradle, Cargo, Node, Python, or repository bootstrap scripts.

## Accessibility / Desktop Behavior Handoff

`lazybuilder-ui` owns keyboard/focus/scaling presentation. This Skill must not make those impossible through architecture.

Examples:

- commands must not assume mouse-only flow;
- long work must not freeze the WebView/UI thread;
- modal-required semantics should be rare and explicit;
- window close/restart behavior must expose predictable state to UI;
- background operation events must be resumable/refetchable after UI reload rather than existing only as ephemeral events.

## Performance Expectations

Professional desktop behavior means:

```text
no filesystem recursion on UI thread
no blocking process waits on UI thread
bounded log/history memory
large copies run in blocking/background execution
progress emission throttled enough to avoid rendering storms
startup avoids unnecessary network calls before local state is usable
```

Optimize after measurement, but never knowingly put large synchronous I/O behind a Tauri command that blocks the desktop runtime inappropriately.

## Release Readiness Matrix

### Static/CI proof

```text
[ ] typed frontend build
[ ] Rust check/tests
[ ] persistence/migration tests
[ ] path/process safety tests
[ ] operation state/retry/recovery tests
[ ] canonical installer package builds
[ ] installer smoke test
```

### Packaged Windows proof

```text
[ ] clean install
[ ] upgrade install
[ ] shortcut/Start Menu/taskbar identity
[ ] first-run flow
[ ] app restart with no server
[ ] app restart with running/detached server
[ ] long operation progress
[ ] interruption/recovery scenarios
[ ] duplicate/remove/delete safeguards
[ ] backup/restore when implemented
[ ] self-update when implemented
[ ] uninstall preserves server data
[ ] support/diagnostic export is sanitized
```

## Anti-Patterns

Reject these unless there is a documented reason:

```text
business rules encoded only in Svelte
multiple settings stores
feature-specific queues for every operation
UI spinner with no durable operation authority
retry that blindly repeats destructive effects
cancel button with no safe cancellation boundary
silent reset when config/schema parsing fails
self-update implemented as downloading/running arbitrary executable without verification
server backup implemented as "Duplicate" with renamed semantics
repair implemented as delete-and-recreate
installer changes not verified in packaged Windows behavior
```
