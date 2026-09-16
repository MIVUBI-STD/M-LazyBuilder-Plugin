# Launcher Production Ownership

This document defines the authority boundaries for the LazyBuilder desktop Launcher. It is an architecture contract for future Launcher work: refactors may move presentation code, but must not create a second owner for durable state, recovery, processes, updates, or long-running operations.

## Core rule

The runtime flow is:

```text
Svelte presentation
  -> typed runtime bridge
  -> Tauri command boundary
  -> Rust orchestration/service
  -> one domain authority
  -> platform/provider adapter
```

Frontend state may cache what is currently displayed. It must not become the durable authority for server identity, operation state, recovery, process ownership, settings, backups, compatibility, or update state.

## Authority matrix

| Concern | Canonical owner | Durable state / evidence | Recovery owner | Do not add |
| --- | --- | --- | --- | --- |
| Launcher instance ownership | `engine/app_instance.rs` | `launcher-instance.json` | `app_instance` + Startup | second PID/lock implementation |
| App-data schema compatibility | `engine/app_data_migrations.rs` | `app-data.json` | migration coordinator | subsystem-specific global schema gate |
| Long-running Launcher operations | `engine/operations.rs` | `operations.json` | OperationRegistry startup reconciliation | frontend operation queue/history |
| Server library / workspace identity | `engine/workspace_registry.rs` | registry + workspace `workspace.json` | workspace registry lifecycle recovery | second workspace database/index |
| Create Server transaction | `engine/workspace_creation.rs` | `pending-creations.json` | `recover_pending_creations` | direct final-folder creation path |
| Adopt Existing transaction | `engine/adoption.rs` | `pending-adoptions.json` | `recover_pending_adoptions` | in-memory-only move rollback |
| Duplicate Server transaction | `engine/workspace_registry.rs` | `pending-duplicates.json` | `recover_pending_duplicates` | second duplicate registry |
| Full server backup content/integrity | `engine/server_backups.rs` | restore point + `backup.json` | backup owner | alternate backup format without migration |
| Interrupted backup staging index | `engine/backup_recovery.rs` | `pending-backups.json` | indexed recovery; one-time legacy sweep | per-start full-library recovery scan after migration |
| Full server restore transaction | `engine/server_restore.rs` | `pending-restores.json` | `recover_pending_restores` | file-by-file overwrite restore |
| Paper process ownership | `engine/server_manager` + `server_process_guard.rs` | workspace process marker | process reconciliation | process-name-only ownership checks |
| Start/Restart serialization | `engine/server_start_lock.rs` | runtime lease/lock | canonical Start preparation | separate UI start lock |
| Server health | `engine/server_health.rs` | derived snapshot | Health/Repair flow | frontend-derived health truth |
| Repair plan/execution | `engine/server_repair.rs` | derived from one health snapshot | repair operation | “repair everything” side path |
| Storage pressure | `engine/storage_health.rs` | derived disk snapshot | Health/readiness | automatic backup deletion policy |
| Launcher settings | `engine/launcher_settings.rs` | `settings.json` | schema migration in same owner | frontend preference store |
| Diagnostics log | `engine/diagnostics.rs` | bounded rotated Launcher logs | diagnostics owner | unbounded log history |
| Support-bundle redaction | `engine/privacy_redaction.rs` | export-time policy + tests | same policy | page/command-specific redactors |
| Support bundle packaging | `engine/support_bundle.rs` | local ZIP only | staged publish | automatic upload |
| Plugin user-file ingress | `engine/plugin_ingress.rs` | LazyBuilder-owned immutable staging snapshot | startup stale-ingress cleanup | semantic parsing from mutable user path |
| Plugin semantics/install | `engine/plugin_manager.rs` | server plugin files | plugin manager transaction/rollback | plugin rules in ingress/UI |
| World task lifetime | World Manager backend | `world_task_list` / task snapshots | World Manager | frontend task timeout/terminal state |
| World task observation | `pages/Worlds.svelte` presentation only | none | reattach from backend task list | frontend durable task registry |
| Runtime compatibility target | repository `toolchain.json` | repository source | compatibility verifier | second compatibility/version file |
| Self-update release authority | Launcher release workflow + Tauri signed updater contract | signed artifacts + update-channel metadata | updater runtime once U2 is enabled | ad-hoc EXE download/update path |
| Close-window safety | `src/app/closeGuard.ts` presentation policy over backend snapshots | none | Operation journal/domain recovery after forced close | independent close handlers |
| Modal keyboard/focus lifecycle | `src/app/modalAccessibility.ts` | none | presentation lifecycle | per-dialog focus-trap implementations |

## Recovery order

Startup recovery deliberately runs before normal product use. The current high-level order is:

```text
single-instance lease
-> app-data schema gate
-> operation journal reconciliation
-> launcher settings
-> workspace registry
-> Create recovery
-> Adoption recovery
-> Duplicate recovery
-> Restore recovery
-> indexed Backup staging recovery
-> one-time legacy Backup sweep when migration requires it
-> Paper process reconciliation
-> optional reopen of last valid server
```

Do not reorder recovery stages without checking dependencies. In particular, workspace registry identity must be available before workspace-scoped recovery, and domain recovery remains separate from OperationRegistry history reconciliation.

## Transaction rule

Filesystem mutations that can leave ambiguous user state must follow this pattern where applicable:

```text
validate
-> persist durable intent
-> stage mutation
-> validate staged result
-> atomic commit/publish
-> verify committed identity
-> clear durable intent
```

After the commit boundary, failures must not be reported as ordinary pre-commit failures when user data may already have moved. Use recovery-required semantics and preserve enough state for startup reconciliation.

## Long-running work rule

- `OperationRegistry` is the single Launcher operation authority.
- Semantic phase/state transitions are durable.
- High-frequency telemetry may be sampled to avoid write amplification.
- Backend/domain state determines completion. Presentation timeouts must not invent terminal failure for work that can legitimately continue.
- Cancellation is exposed only when the underlying domain has a safe cancellation boundary.

## Data-preservation rule

Installer lifecycle and user/server data lifecycle are separate. Uninstall/reinstall must not implicitly delete:

- server workspaces;
- `.lazybuilder-backups` restore points;
- `%LOCALAPPDATA%/LazyBuilder` state;
- recovery intents required to reconcile interrupted operations.

Explicit destructive actions remain domain-owned and require their existing validation/confirmation paths.

## Privacy rule

Anything exported for support must pass through `privacy_redaction::RedactionPolicy`. The policy handles raw, slash-normalized, case-insensitive, and JSON-escaped Windows paths. Do not implement support-artifact redaction separately in pages, commands, or `support_bundle.rs`.

## Proof vocabulary

Use these terms precisely:

- **source-implemented** — code is present in the repository.
- **source-audited** — relevant ownership/recovery contracts have been inspected against current source.
- **CI-proven** — the intended Launcher verification workflow succeeded for the exact source commit.
- **packaged-Windows-proven** — the packaged Windows artifact passed its target-machine/package checks.
- **Local-PC-proven** — the user-approved LocalTest flow passed on the intended local machine.

A stronger proof level must not be inferred from a weaker one.
