# Desktop ↔ World-Manager Control Bridge

## Purpose

LazyBuilder Desktop presents world state but does not own world lifecycle logic. `World-Manager` remains the only authority for managed worlds.

```text
LazyBuilder.exe
    ↓ authenticated loopback request
World-Manager
    ↓
Paper / world-system
```

## Transport

The first desktop bridge uses HTTP/JSON bound only to the loopback interface.

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

## Initial API surface

```text
GET /v1/status
GET /v1/worlds
```

`/v1/worlds` returns only World-Manager-owned metadata and runtime state:

- stable world id
- folder name
- display name
- Flat/Void kind
- lifecycle state
- runtime load state
- auto-load state
- default game mode

The desktop does not scan or mutate `world-system/` to build this list.

## Ownership rule

Do not add desktop implementations for:

- create/load/unload
- archive/restore
- clone/delete
- world settings
- backup
- import/export
- conversion

Future endpoints must call the existing World-Manager application services. HTTP handlers stay thin transport adapters.

## Security and maintenance rules

1. Never bind the control server to `0.0.0.0`.
2. Every request requires the local bearer token.
3. Never accept arbitrary filesystem paths from the desktop when a world id/artifact id can be used.
4. Keep protocol responses small and structured.
5. Increment protocol version only for breaking contract changes.
6. Keep long world operations asynchronous when they are added; do not block the HTTP listener on conversion/copy work.
7. CI/source proof is not a substitute for live Paper runtime validation.
