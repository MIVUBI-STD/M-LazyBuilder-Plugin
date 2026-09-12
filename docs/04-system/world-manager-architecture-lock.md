# World-Manager Architecture Lock

## Status

World-Manager is **structurally stable at the source/CI proof level**.

This lock means new work should extend the existing ownership boundaries rather than introduce parallel managers, duplicate filesystem authorities, duplicate task systems, or alternate transport-specific implementations of the same world operation.

It does **not** mean live Paper/Desktop/Fabric validation is complete. Runtime proof remains a separate LOCAL_CODE / LIVE_SERVER stage.

## Canonical ownership

```text
WorldManagerPlugin
    ↓
WorldManager
    ↓
application services / registry / files / conversion
```

`WorldManagerPlugin extends JavaPlugin` is the only Paper bootstrap owner for the module.

There is no legacy `LazyBuilderPlugin` compatibility bootstrap.

## Client transports

Two supported frontends reach the same World-Manager authority:

```text
Desktop Tauri/Svelte
    ↓ authenticated loopback HTTP/JSON
PaperLocalControlServer

Fabric Minecraft client
    ↓ plugin messaging
PaperWorldControlPayloadAdapter
PaperTransferPayloadAdapter
PaperMapActionPayloadAdapter
```

Transport code owns only transport concerns: authentication/permissions, framing, request parsing, response delivery, file picker/upload plumbing, and client-specific state.

Transport code must not own registry persistence, world-file publication, lifecycle rules, conversion rules, or duplicate implementations of world operations.

## Heavy operations

General heavy operations shared by Desktop and Fabric use one orchestration boundary:

```text
transport
    ↓
WorldHeavyOperationOrchestrator
    ↓
WorldCloneService
WorldDeleteService
WorldExportService
WorldImportService
```

The orchestrator owns phase sequencing and Paper thread boundaries only. Domain logic remains in the application services.

Desktop wraps heavy operations with the bounded `WorldTaskRunner` for queueing, progress snapshots, and task observation.

`WorldTaskRunner` is fixed at bounded concurrency and bounded queue capacity; do not replace it with an unbounded executor.

Backup remains desktop-only and directly delegates to `WorldBackupService` until another supported frontend creates a real need for shared backup orchestration.

Xaero/map area export retains its specialized adapter flow because area export has a distinct contract and source-resume timing. It still delegates export domain logic to `WorldExportService`.

## Paper thread boundary

`PaperMainThreadDispatcher` is the canonical cross-thread Paper call boundary.

It owns:

- inline execution when already on the Paper primary thread;
- scheduled main-thread execution otherwise;
- timeout handling;
- queued-future cancellation on timeout;
- interrupted-thread restoration;
- unwrapping and preserving the original service exception.

Do not reintroduce direct `Future#get(timeout)` logic in transport adapters or heavy-operation orchestration.

## Destructive-operation safety

Permanent delete requires:

- exact folder-name confirmation;
- server-side fallback/default-world protection;
- exclusive world-operation lease;
- reversible staging before registry commit;
- registry persistence before final physical cleanup;
- recovery when failure happens before commit.

UI confirmation is additive safety only; server-side validation remains mandatory.

## Import/export ownership

Import file transport and import domain execution remain separate concerns:

```text
Desktop file picker
→ authenticated streaming upload
→ TransferSessionService / imports inbox
→ async IMPORT task
→ WorldImportService
```

Export and backup artifacts are produced by World-Manager-owned stores. Desktop must never become a second server-filesystem implementation.

Conversion remains an on-demand World-Manager implementation detail behind the conversion adapter/runtime boundary.

## Shutdown contract

Shutdown is fail-closed:

1. stop accepting new Desktop HTTP requests;
2. stop accepting new Fabric heavy-operation requests;
3. close the bounded WorldTaskRunner;
4. stop Fabric/map/transfer transports;
5. stop WorldManager last.

An already-running canonical operation must be allowed to execute its service `finish`/cleanup path where possible. Stale transport responses are suppressed after transport shutdown begins.

## Proof at lock point

The lock was established after CI verified all three current build surfaces from `Local`:

```text
Paper modules/tests  ✅
Fabric client build  ✅
Tauri/Svelte/Rust    ✅
```

Source/CI proof covers compilation, unit tests, typecheck, frontend build, and Rust check. It does not prove live Paper world mutation, Windows packaged-app behavior, Fabric gameplay interaction, real large-world transfer throughput, or real Chunker conversion.

## Change rule after lock

A future change may modify this architecture only when a concrete requirement cannot be met cleanly inside the existing ownership boundaries.

Preferred order:

```text
extend existing service
→ extend shared orchestrator only when phase sequencing is shared
→ extend one transport adapter
→ add focused test
→ update canonical contract
```

Do not add another manager, executor, filesystem authority, registry, conversion runtime, or world-operation path for convenience.
