# Desktop ↔ World-Manager Control Bridge

## Purpose

LazyBuilder Desktop presents and requests world operations but does not own world lifecycle logic. `World-Manager` remains the only authority for managed worlds.

```text
LazyBuilder.exe
    ↓ authenticated loopback request
World-Manager
    ↓
Paper / world-system
```

## Transport

The desktop bridge uses HTTP/JSON bound only to the loopback interface.

- host: `127.0.0.1` / loopback only
- default port: `17842`
- protocol version: `1`
- authentication: bearer token
- token is generated and persisted by LazyBuilder Desktop under `tools/lazybuilder/world-control.json`
- the token and port are passed to the Paper child process through environment variables
- the token is not printed in normal logs

Environment variables:

```text
LAZYBUILDER_WORLD_CONTROL_TOKEN
LAZYBUILDER_WORLD_CONTROL_PORT
```

When no token is supplied, the World-Manager local bridge remains disabled. This keeps manual Paper launches from exposing an unauthenticated control surface.

## API surface — Stage 1 and Stage 2

```text
GET   /v1/status
GET   /v1/worlds
POST  /v1/worlds
POST  /v1/worlds/{id}/load
POST  /v1/worlds/{id}/unload
GET   /v1/worlds/{id}/settings
PATCH /v1/worlds/{id}/settings
```

The protocol version remains `1` because Stage 2 is an additive extension of the existing contract rather than a breaking change. Increment the protocol version only when an existing request/response contract becomes incompatible.

### World inventory

`GET /v1/worlds` returns World-Manager-owned metadata and runtime state:

- stable world id
- folder name
- display name
- Flat/Void/imported kind
- lifecycle state
- runtime load state
- auto-load state
- default game mode

The desktop does not scan or mutate `world-system/` to build this list.

### Create

`POST /v1/worlds` accepts a bounded create request for `FLAT` or `VOID`. The handler delegates to the existing `WorldCreationService`, which owns BUILD_READY application, registry publication, persistence, runtime state initialization, and rollback.

Desktop must never create world folders directly.

### Load / unload

`POST /v1/worlds/{id}/load` and `/unload` delegate to `WorldRuntimeService`. Requests use the stable world id rather than a filesystem path.

### Basic settings

`GET /v1/worlds/{id}/settings` exposes the normal settings snapshot used by the desktop. `PATCH /v1/worlds/{id}/settings` supports the bounded normal settings surface:

- auto load
- default game mode
- time of day
- weather
- natural mob spawning
- daylight cycle
- weather cycle

Settings operations delegate to `WorldSettingsService`; the desktop does not write Paper files or world metadata directly.

All Paper runtime mutations initiated by the HTTP listener must be dispatched to the Paper main thread before calling Bukkit/Paper-backed services.

## Ownership rule

The desktop may request operations, but must not implement them independently. HTTP handlers are transport adapters over existing World-Manager application services.

Do not add desktop filesystem implementations for:

- create/load/unload
- archive/restore
- clone/delete
- world settings
- backup
- import/export
- conversion

## Next control slice

Do not expose clone, backup, import, export, archive, restore, or delete as synchronous desktop HTTP calls. These operations require an asynchronous task contract first, with task id, state, progress/message, success/failure result, and bounded cancellation semantics where supported.

## Security and maintenance rules

1. Never bind the control server to `0.0.0.0`.
2. Every request requires the local bearer token.
3. Never accept arbitrary filesystem paths from the desktop when a world id/artifact id can be used.
4. Keep protocol responses small and structured.
5. Increment protocol version only for breaking contract changes.
6. Keep long world operations asynchronous; do not block the HTTP listener on conversion/copy work.
7. Execute Bukkit/Paper runtime mutations on the server main thread.
8. CI/source proof is not a substitute for live Paper runtime validation.
