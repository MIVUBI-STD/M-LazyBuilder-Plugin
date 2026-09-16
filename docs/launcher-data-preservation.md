# LazyBuilder Launcher Data Preservation Contract

## Purpose

Launcher installation state and user/server data are separate lifecycles. Installing, updating, repairing, uninstalling, or reinstalling the LazyBuilder desktop application must not implicitly delete user-managed Minecraft server data.

## Canonical data classes

### Application binaries

Owned by the installer and safe for installer replacement/removal.

- LazyBuilder executable and bundled resources under the installer-managed application directory.
- WebView/bootstrap installer artifacts owned by the package manager.

### Launcher application data

Persisted outside the installer directory under `%LOCALAPPDATA%\LazyBuilder` (with `%APPDATA%` fallback where existing source owners explicitly support it).

This includes, among other bounded launcher state:

- launcher settings;
- server library registry;
- operation journal;
- startup/recovery intents;
- managed Java runtime/cache;
- launcher diagnostics/logs;
- temporary plugin ingress cache.

Uninstall must not silently delete this tree. Reinstall with the same application-data schema must be able to reopen it through the application-data migration gate.

### Server workspaces

Server workspaces are user data and can live anywhere selected by the user. They are never installer-owned.

Uninstall must not delete, move, rewrite, or unregister them.

### Server restore points

Full server restore points live in sibling `.lazybuilder-backups/<workspace-id>` storage. They are user recovery data and are not installer-owned.

Uninstall must not delete or prune them.

## Reinstall semantics

On reinstall:

1. the single-instance authority is acquired;
2. the application-data schema gate runs before mutable subsystem state is opened;
3. operation and filesystem recovery reconciliation runs;
4. the existing server library is reused;
5. saved server paths are validated before activation;
6. missing server locations remain recoverable through Locate instead of being discarded.

Reinstall must not auto-start Paper.

## Explicit deletion boundary

The only operation that may permanently delete a registered server workspace is the explicit Launcher **Delete server** flow, which requires server identity validation and typed-name confirmation.

Backup deletion is also explicit and scoped to one validated restore point.

Neither installer uninstall nor application-data migration is a server deletion authority.

## Reset / factory-reset policy

LazyBuilder currently has no implicit factory-reset action. If a future reset feature is introduced, it must be a separate explicit product flow with a preview of affected paths and must not be implemented as an installer uninstall hook.

## Proof boundary

This document and repository verification establish the **source contract** only. Actual Windows Add/Remove Programs behavior remains `packaged-Windows-proven` only after installer/uninstaller validation on a Windows package produced by the release pipeline.
