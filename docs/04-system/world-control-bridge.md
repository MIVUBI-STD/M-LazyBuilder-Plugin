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

The canonical application services define operation phases. Transport adapters only dispatch those phases onto the correct execution context.

## Heavy-operation coordination

Desktop heavy operations use `WorldTaskRunner` for bounded asynchronous execution and task observation.

Fabric plugin-message operations currently preserve the existing request/response contract, but still delegate the actual operation phases to the same services and `WorldOperationCoordinator`. They must not duplicate service logic.

Long-term consolidation should move common heavy-operation orchestration into a shared application-level coordinator without breaking the Fabric protocol or desktop task contract.

## Bootstrap ownership

`WorldManagerPlugin` is the canonical Paper entry point and lifecycle owner.

The historical `LazyBuilderPlugin` type is now compatibility-only and contains no bootstrap state or lifecycle behavior. Remove it after remaining adapter constructor types have been generalized away from the historical class name.

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
10. CI/source proof is not a substitute for live Paper/Fabric/Desktop runtime validation.
