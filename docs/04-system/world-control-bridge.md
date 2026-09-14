# World-Manager Control Transports

## Purpose

`World-Manager` is the only authority for managed world lifecycle, persistence, file operations, conversion, and runtime state.

LazyBuilder has two frontend transports that delegate to the same World-Manager application services:

```text
LazyBuilder Desktop
→ authenticated loopback HTTP/JSON
→ PaperLocalControlServer
→ World-Manager services

Minecraft Fabric Map Manager
→ Paper plugin messaging
→ World / Map / Transfer adapters
→ World-Manager services
```

These are transport adapters, not separate world-management systems.

## Desktop transport

The desktop bridge is loopback-only and authenticated.

```text
host             127.0.0.1 / loopback
protocol version 2
authentication   bearer token
```

Environment variables:

```text
LAZYBUILDER_WORLD_CONTROL_TOKEN
LAZYBUILDER_WORLD_CONTROL_PORT
```

When no token is supplied, the desktop bridge remains disabled. Manual Paper launches therefore do not expose an unauthenticated desktop control surface.

Desktop owns request presentation and local file selection only. World mutation remains in World-Manager services.

The current product model does not expose manual world Load/Unload or per-world autoLoad controls. Desktop world operations use current lifecycle/application semantics such as create, settings, Duplicate, Archive/Restore, backup, Import/Export, and Delete.

## Fabric transport

The Fabric Map Manager uses the existing Minecraft play connection:

```text
lazybuilder:world     → World Control V5
lazybuilder:map       → Map Action V2
lazybuilder:transfer  → bounded file-transfer protocol
```

The adapters own transport-specific concerns such as player identity, permissions, packet framing, session routing, and response delivery. They must not create alternative registry, filesystem, conversion, lifecycle, or authorization authorities.

The map surface and Export Area selection are first-party LazyBuilder UI. Xaero may be a behavioral reference only and is not part of this transport ownership.

## Threading

Paper/Bukkit runtime mutations execute on the Paper primary thread. Heavy file work, hashing, archive work, and conversion execute away from the primary thread through explicit bounded owners.

`PaperMainThreadDispatcher` is the canonical cross-thread boundary where synchronous Paper access is required.

## Heavy-operation coordination

Heavy operations share canonical World-Manager application services and operation coordination rather than implementing separate Desktop and Fabric business paths.

Product operations include:

```text
Duplicate
Delete
Export
Import
Archive / Restore
Backup where supported by the requesting frontend
```

Transport layers may differ in request/response presentation, but they do not own duplicate domain logic.

`WorldHeavyOperationOrchestrator` coordinates phases that genuinely require shared ordering between runtime-sensitive and file-heavy work. Domain semantics remain in the corresponding application services such as `WorldDuplicateService`, `WorldDeleteService`, `WorldExportService`, and `WorldImportService`.

Area export may have map-specific orchestration for transient spatial context, but it still delegates export semantics to the canonical `WorldExportService` path.

## Import review ownership

The Fabric World Control path separates upload, inspection, review, and final Import:

```text
transfer upload completes
→ InspectImport claims the uploaded artifact for review
→ server returns bounded metadata
→ user explicitly imports or discards/leaves the review
```

Final Import revalidates before publication. Abandoned review cleanup is explicit/event-driven and scoped to the requesting player's tracked reviewed artifact. Do not add an orphan-scanning daemon or second inbox registry.

## Shutdown and failure behavior

Transports fail closed:

- no new requests after shutdown begins;
- active transfer sessions clean partial state;
- transport callbacks must not mutate stale runtime state after shutdown;
- application/service cleanup still runs where required to release leases and restore valid state;
- malformed/out-of-order protocol input is rejected and affected request/session state is cleaned.

## Security rules

1. Desktop control stays loopback-only.
2. Every desktop request requires the local bearer token.
3. Fabric permission presentation is never final authorization; Paper rechecks every mutation.
4. Payloads and transfer sizes remain bounded.
5. Arbitrary filesystem paths are not accepted when owned IDs/artifacts can be used.
6. Protocol version mismatch fails closed.
7. File publication occurs only after required validation/checksum checks.
8. Transport adapters never become world/domain authorities.

## Proof boundary

Source/static review can prove contract shape, version alignment, ownership, and fail-closed structure. Actual desktop installation, Paper lifecycle, Fabric interoperability, Import review timing, large transfers, disconnect behavior, and gameplay interaction remain LOCAL_CODE/LIVE_SERVER proof responsibilities.
