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

## Product work that is still intentionally separate

The foundation does not mean these product surfaces are implemented yet:

- Activity Center UI
- Launcher self-update UI/activation flow
- full server Backup/Restore product flow
- Health/Repair UI and repair executor
- complete central Settings UI
- missing-location/relocate UX
- support-package export

Those features should now be built on the F0-F5 authorities rather than introducing parallel state systems.
