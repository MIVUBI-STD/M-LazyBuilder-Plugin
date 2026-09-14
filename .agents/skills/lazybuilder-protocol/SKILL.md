---
name: lazybuilder-protocol
description: Own neutral shared Paper/Fabric contracts under shared/protocol: request/result payloads, identifiers, wire validation/defaults, compatibility semantics, and current World/Map/Transfer protocol shapes. Do not use for desktop loopback HTTP, Paper implementation logic, or UI presentation.
---

# LazyBuilder Shared Protocol

Own neutral Paper/Fabric wire semantics only. Follow `docs/04-system/development-discipline.md`, `docs/04-system/networking.md`, and `CONTEXT.md`.

## Owns

```text
shared request/result payloads
shared identifiers
wire validation/default/optional semantics
map-action contracts
world-control contracts
shared transfer contracts
Paper/Fabric compatibility semantics
source ownership under shared/protocol
```

## Does Not Own

```text
Paper implementation behavior → lazybuilder-world-management
Desktop or Fabric UI          → lazybuilder-ui
desktop loopback HTTP/control → lazybuilder-desktop-runtime
```

A `WorldControl*` type located in `shared/protocol` is owned here only for neutral shared wire semantics. Desktop HTTP request routing/authentication/session behavior remains Desktop Runtime.

## Current canonical contracts

### World Control V3

Product-facing world control intentionally excludes stale runtime-state machinery.

Do not reintroduce:

```text
LoadWorld
UnloadWorld
SetAutoLoad
runtimeState in WorldSummary
autoLoad in WorldSummary
CloneWorld
```

Current concepts include:

```text
ListWorlds + canManage/canTeleport
CreateWorld
TeleportWorld
ArchiveWorld / RestoreWorld
DuplicateWorld
DeleteWorld
settings requests
ExportWorld / ImportWorld
GetExportFormats / ExportFormats
```

`WorldSummary` carries durable/presentation world metadata only.

### Map Action V2

Owns spatial map intents plus authoritative current-world presentation state:

```text
TeleportLocation
ExportArea
CurrentWorldRequest
CurrentWorldResult
CurrentWorldCleared
```

Paper may push current-world changes when the player's actual world changes. An unmanaged world must explicitly clear previous managed-world state.

### Transfer

File bytes stay on `lazybuilder:transfer`; do not tunnel files through World or Map payloads and do not introduce an extra HTTP/WebSocket/cloud path for in-game transfer.

## Procedure

```text
name exact caller-visible contract
→ prove it is a shared wire concern
→ reuse existing neutral type/validation
→ change smallest payload/semantic surface
→ update direct Paper/Fabric adapters lockstep
→ update round-trip tests
→ targeted compile/contract proof
→ STOP
```

## Protocol invariants

- neutral contracts never depend on Paper implementation classes;
- shared types are not duplicated in World Manager or Fabric;
- requests/results stay small, typed, bounded, and transport-neutral;
- version bumps are intentional when compatibility is broken;
- no fallback formats, parallel active protocol paths, or command-string tunneling without a supported compatibility requirement;
- server remains authorization/domain authority even when capability flags are sent for presentation;
- capability catalogs contain only verified backend-supported values;
- implementation-language/build mechanics are not protocol changes;
- desktop loopback control and Minecraft client/server data plane remain separate boundaries.

## Proof boundary

Static/compile proof can establish shared ownership and adapter alignment. Real Paper/Fabric interoperability requires local/live client-server proof. Current `Local` protocol changes have not yet received that final fresh validation pass.
