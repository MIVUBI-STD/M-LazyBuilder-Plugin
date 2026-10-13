# Launcher Foundation Implementation Status

This file records implementation/proof status for the architecture defined in `launcher-foundation-audit.md`. It does not replace that architecture document.

## Scope

Launcher only (`apps/launcher`). Plugin/mod domain behavior remains out of scope.

## Foundation status

| Phase | Source status | Notes |
|---|---|---|
| F0 Operation Framework | Implemented | Rust-owned operation registry, exclusive resource guard, snapshot/query contract, Duplicate Server pilot, correlation ID support. |
| F1 Startup Coordinator | Implemented | Runtime environment, Launcher settings initialization, workspace recovery, process-marker reconciliation, explicit degraded startup report. |
| F2 Launcher Settings Authority | Implemented | Separate from `ServerConfig`; schema version, defaults, migration, atomic replacement, typed Tauri bridge. |
| F3 Server Health / Readiness | Implemented | Backend-authoritative health snapshot covering workspace identity/location, config, Java, Paper, core modules, EULA, and validated process ownership. |
| F4 Error Recovery Model | Implemented foundation | Command errors carry technical code, message, details, recoverable flag, suggested action, and correlation ID. Product-specific recovery actions can be added without changing the envelope. |
| F5 Diagnostics Correlation | Implemented | Bounded launcher log retention, serialized rotation/writes, command/operation correlation IDs. |

## Lifecycle hardening included in the foundation pass

- Duplicate destination cannot be inside its source workspace.
- Duplicate staging is cleaned on copy/manifest preparation failures.
- Published duplicate is cleaned if canonicalization, manifest finalization, or registry publication fails.
- Duplicate rejects symbolic links and Windows reparse points/junction-style traversal.
- Pending deletion intent uses atomic replacement with a previous-valid backup.
- Delete preserves recovery intent if registry update and filesystem rollback both fail.
- Startup reconciles stale managed Paper process markers without terminating a validated live process.

## Proof policy

Intermediate CI runs are not phase gates. Development proceeds through source implementation and audit first.

One final Launcher verification run is the remote proof gate for the complete F0-F5 source set:

```text
frontend install/typecheck/build
Tauri icon generation
Rust cargo check
Rust cargo test
Launcher packaging contract
```

A green final CI means **CI-proven**, not **Local-PC-proven**.

Local PC acceptance remains a separate later gate and must not begin until the source audit and final Launcher CI are clean and explicit approval is given.

## Product surfaces beyond the foundation (source review: 2026-10-13)

The F0-F5 foundation is not a statement that later product surfaces are absent. The current `Local` source already contains the following implementations; presence in source is **not** live Windows or end-to-end acceptance proof.

| Product surface | Source observation | Remaining boundary |
|---|---|---|
| Activity Center | `src/pages/Activity.svelte` presents active/recent tracked work using the existing Rust operation owner. | Verify navigation, cancellation and interrupted-operation recovery in the installed app. |
| Server Backup / Restore | `src/pages/BackupPanel.svelte` and `src-tauri/src/engine/server_backups.rs` implement listing, estimates, backup, restore and retention-related UI feedback. | Prove real large-workspace backup/restore and failure recovery; do not infer scheduled backups. |
| Health / Repair | `src/pages/HealthPanel.svelte` and `src-tauri/src/engine/server_repair.rs` implement readiness checks, repair planning and supported repair execution. | Verify repair boundaries on target Windows, including blocked/manual cases. |
| Launcher Settings | `src/pages/LauncherSettings.svelte` exposes implemented settings and support controls. | This is not evidence of a general-purpose settings platform or an active signed updater. |
| Missing-location / Reconnect | `src/pages/ServersLibrary.svelte` includes locating and reconnecting a moved registered server. | Verify moved/missing directory and identity safety with actual workspaces. |
| Support-package export | `src/pages/LauncherSettings.svelte` calls the existing support-bundle implementation in `src-tauri/src/engine/support_bundle.rs`. | Check archive contents, redaction and permissions under a real installation; no automatic upload. |
| Launcher self-update | Signed in-app updater activation remains intentionally disabled; Settings states that updates are manual. | Do not advertise automatic update checks or enable unsigned update paths. |

These product surfaces must extend the F0-F5 authorities, not introduce parallel operation, readiness, filesystem or recovery owners.

This is a dated **source-alignment note**, not a mutable completion tracker. The actual proof and release gate are owned by [Current Verification](../05-operations/current-verification.md) and the exact `Local` revision.
