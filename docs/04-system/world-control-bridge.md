# World-Manager Control Transports

## Purpose

`World-Manager` is the only authority for managed world lifecycle, persistence, file operations, conversion, and runtime state.

LazyBuilder currently has two client transports that serve different frontends:

```text
LazyBuilder Desktop (Tauri/Svelte)
    ↓ authenticated loopback HTTP/JSON
PaperLocalControlServer
    ↓
World-Manager application services

Minecraft Fabric client
    ↓ Paper plugin messaging
PaperWorldControlPayloadAdapter / PaperTransferPayloadAdapter / PaperMapActionPayloadAdapter
    ↓
World-Manager application services
```

The transports are not separate world-management systems. They must stay thin and delegate to the same canonical World-Manager services.

## Desktop transport

The desktop bridge uses HTTP/JSON bound only to the loopback interface.

- host: `127.0.0.1` / loopback only
- default port: `17842`
- protocol version: `1`
- authentication: bearer token
- token is generated and persisted by LazyBuilder Desktop under `tools/lazybuilder/world-control.json`
- token and port are passed to the Paper child process through environment variables
- token is not printed in normal logs

Environment variables:

```text
LAZYBUILDER_WORLD_CONTROL_TOKEN
LAZYBUILDER_WORLD_CONTROL_PORT
```

When no token is supplied, the desktop bridge remains disabled. Manual Paper launches therefore do not expose an unauthenticated HTTP control surface.

### Desktop API

```text
GET   /v1/status
GET   /v1/worlds
POST  /v1/worlds
POST  /v1/worlds/{id}/load
POST  /v1/worlds/{id}/unload
GET   /v1/worlds/{id}/settings
PATCH /v1/worlds/{id}/settings

GET   /v1/tasks
GET   /v1/tasks/{taskId}
POST  /v1/tasks/archive
POST  /v1/tasks/restore
POST  /v1/tasks/clone
POST  /v1/tasks/backup
POST  /v1/tasks/export
POST  /v1/tasks/import
POST  /v1/tasks/delete

POST  /v1/imports/upload
```

Heavy operations return a task snapshot immediately and execute through the bounded `WorldTaskRunner`.

### Desktop ownership rules

Desktop must never implement world operations independently. It may select files and submit structured requests, but all world mutation remains in World-Manager services.

Desktop must not directly implement:

- create/load/unload
- archive/restore
- clone/delete
- world settings
- backup
- import/export
- conversion
- world registry persistence

## Fabric client transport

The Fabric client integration remains a supported in-game frontend. Its Paper plugin-message adapters exist because Minecraft clients cannot use the local desktop HTTP token/loopback contract as their normal control path.

Current channels:

```text
lazybuilder:world     general in-game world controls
lazybuilder:transfer  bounded import/export file transfer
lazybuilder:map       Xaero/map-related world actions
```

These adapters may own transport-specific concerns such as permissions, packet framing, player identity, and response delivery. They must not create alternative registry, filesystem, conversion, or lifecycle implementations.

## Threading

Paper/Bukkit runtime mutations must run on the Paper primary thread.

Heavy file work, ZIP packaging, conversion, and hashing must run off the Paper primary thread.

The canonical application services define operation phases. Transport adapters only dispatch work through the shared orchestration boundary and handle transport-specific request/response delivery.

## Heavy-operation coordination

`WorldHeavyOperationOrchestrator` is the shared phased orchestration boundary for heavy operations used by both Desktop HTTP and the Fabric world-control transport.

It centralizes:

- Clone `prepare → file phase → finish`
- Delete `prepare → staged delete → finish`
- Export `prepare → snapshot → source resume → package/convert → finish`
- Import `prepare → validate/convert/publish → finish`
- Paper main-thread dispatch for lifecycle-sensitive phases
- finish/error combination semantics

The orchestrator does **not** own domain logic or storage implementations. Those remain inside `WorldCloneService`, `WorldDeleteService`, `WorldExportService`, and `WorldImportService`.

Desktop wraps the shared orchestrator with `WorldTaskRunner`, which provides bounded worker concurrency, bounded queueing, progress snapshots, and task observation.

Fabric keeps its existing plugin-message request/response contract and per-player in-flight guard, while delegating the same heavy operation flow to the shared orchestrator.

`Backup` remains desktop-only at this stage and directly uses `WorldBackupService`; there is no duplicate Fabric backup path to consolidate.

`PaperMapActionPayloadAdapter` retains its area-export-specific orchestration because it has a distinct Xaero/map contract and intentionally restores player access immediately after area snapshot capture. It still delegates all export domain work to `WorldExportService`.

## Bootstrap ownership

`WorldManagerPlugin extends JavaPlugin` is the single canonical Paper entry point and lifecycle owner.

The historical `LazyBuilderPlugin` compatibility base has been removed. Paper adapters and `WorldManager` depend only on `JavaPlugin` or explicit World-Manager services.

## Security and maintenance rules

1. Never bind the desktop control server to `0.0.0.0`.
2. Every desktop request requires the local bearer token.
3. Never accept arbitrary server filesystem paths when a world id or artifact id can be used.
4. Keep protocol responses bounded and structured.
5. Increment desktop protocol version only for breaking contract changes.
6. Keep long operations asynchronous.
7. Execute Bukkit/Paper runtime mutations on the server primary thread.
8. Keep all world business logic inside World-Manager services, never inside UI or transport layers.
9. Desktop HTTP and Fabric plugin messaging are client transports, not separate authorities.
10. Keep shared heavy-operation phase sequencing in `WorldHeavyOperationOrchestrator`; transports own only transport concerns.
11. CI/source proof is not a substitute for live Paper/Fabric/Desktop runtime validation.
