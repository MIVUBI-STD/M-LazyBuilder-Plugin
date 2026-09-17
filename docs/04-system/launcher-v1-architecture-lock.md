# Launcher V1 Architecture Lock

Status: **locked for product hardening and verification**.

This document records the Launcher architecture that should remain stable while V1 is finished. It is intentionally narrower than a general desktop-platform design.

## Product goal

LazyBuilder Launcher is a dependable local control surface for build servers. It should make common server work understandable, recoverable, and safe without taking ownership away from the systems that already own Minecraft, worlds, or third-party content.

The V1 quality bar is closer to a mature desktop launcher: predictable state, visible long-running work, clear recovery steps, durable local metadata, and isolated server instances.

## Layer boundary

```text
Svelte product surfaces
        ↓
Typed runtime facade
        ↓
Tauri command adapters
        ↓
Rust domain/runtime owners
```

Rules:

- `App.svelte` is a shell: navigation, current workspace composition, loading state, and desktop close guard.
- Product flows live in focused surfaces such as `ServersLibrary`, `ActiveServer`, `Activity`, `Client`, `Worlds`, `Plugins`, and Settings.
- `runtimeApi.ts` is composition-only. Command ownership belongs to bounded APIs such as `appApi`, `workspaceApi`, `serverApi`, `pluginApi`, `clientApi`, and `worldApi`.
- Svelte must not become a second source of truth for runtime or filesystem state.

## Ownership

Launcher owns:

- desktop lifecycle and single-instance behavior;
- server/workspace library metadata;
- managed Java and Paper runtime;
- Launcher-owned core modules;
- local backup/restore and repair flows;
- Launcher plugin file management;
- Modrinth client integration boundary;
- diagnostics, support bundles, settings, and operation history.

Launcher does **not** own:

- Minecraft account/session management;
- general Modrinth profile/modpack management;
- arbitrary third-party plugin configuration;
- world lifecycle semantics, transfer semantics, conversion semantics, or world filesystem rules.

`plugins/world-manager` remains the authority for world-management semantics. The Launcher world module is a loopback control bridge only.

## Long-running work

`OperationRegistry` is the durable Launcher operation journal and exclusivity guard.

It is **not** a scheduler, queue platform, DAG/workflow engine, or background daemon.

Required behavior:

- long-running Launcher work records a stable `kind`, `phase`, `status`, and optional progress;
- Activity is the detailed task surface;
- navigation exposes a compact active/recovery indicator so work is visible without opening Activity;
- operation conflicts return `OPEN_ACTIVITY` recovery guidance;
- operation history remains bounded.

Do not add generic scheduling infrastructure unless a concrete product requirement cannot be solved with the current registry.

## Recovery errors

Backend errors use stable machine-readable `RecoveryAction` codes.

Frontend owns human-readable labels and eventual navigation behavior.

Do not add new prose recovery strings through `CommandError::recoverable(...)` for user-facing flows. New or migrated code should use `recoverable_action(...)`.

Typical actions include:

- `OPEN_ACTIVITY`
- `RETRY_OPERATION`
- `STOP_SERVER`
- `START_SERVER`
- `WAIT_FOR_SERVER_START`
- `WAIT_FOR_SERVER_STOP`
- `REPAIR_SERVER`
- `LOCATE_WORKSPACE`
- `RECONNECT_CLIENT_PROFILE`
- `REVIEW_BACKUPS`
- `REVIEW_PLUGINS`

Machine codes are protocol. Button text is presentation.

## Readiness and runtime state

Server readiness and live runtime condition are separate concepts.

- readiness answers whether the registered server is complete/safe enough for a Launcher operation;
- runtime condition describes the live process state/health information.

Do not collapse them back into one overloaded `health` concept. Compatibility aliases may remain until wire/API migration is safe.

## Persistence

Launcher metadata persistence uses the canonical atomic JSON and safe-path policy.

Do not introduce another ad-hoc JSON atomic-write implementation.

Persistence code must continue to reject unsafe symlink/reparse-point metadata paths and preserve interrupted-write recovery behavior.

## Server isolation

Each registered server/workspace is an independent unit.

Mutation operations must:

- target an explicit workspace resource;
- reject conflicting active operations;
- reject unsafe mutation while the server process is active;
- preserve workspace identity during reconnect, duplicate, delete, backup, and restore;
- avoid modifying unrelated server instances.

## Repair boundary

Automatic repair may change only Launcher-owned state, including managed Java, Paper runtime, Launcher configuration, and bundled LazyBuilder components.

Do not automatically repair:

- arbitrary worlds;
- arbitrary third-party plugin data/configuration;
- workspace identity mismatches;
- Minecraft EULA acceptance;
- unrelated user files.

Those remain manual/blocking actions.

## Update boundary

The current settings contract is fail-closed:

- update channel: `stable` only;
- automatic in-app update checks: disabled until the signed updater runtime is enabled.

Do not expose preview/update preferences in UI or frontend types before the signed updater path exists and is verified end-to-end.

Paper runtime updates are separate from desktop self-update and remain transactional with rollback protection.

## Non-goals for V1

Do not add without a concrete requirement:

- IoC/DI container;
- database replacing the current bounded local metadata stores;
- generic scheduler/task queue;
- cloud sync;
- account platform;
- marketplace/store;
- telemetry platform;
- background daemon;
- generic frontend state framework;
- Launcher-owned world management semantics.

## Change rule

Before adding a new abstraction, answer all three:

1. Which existing user flow cannot be implemented safely with the current boundary?
2. Which current owner is insufficient, and why?
3. Can the requirement be solved by extending an existing bounded API/domain instead?

If the third answer is yes, extend the existing owner rather than creating another layer.

## Verification gate

Architecture work is considered frozen before final verification. The final verification sequence is:

1. frontend type/Svelte checks and production build;
2. Rust check/tests;
3. Launcher contract/hardening scripts;
4. package/local smoke checks;
5. CI/workflow verification last.

Do not use failing verification as a reason to introduce new architecture unless the failure demonstrates a real ownership or lifecycle defect.
