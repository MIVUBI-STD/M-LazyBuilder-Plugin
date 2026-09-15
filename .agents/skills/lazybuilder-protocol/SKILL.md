---
name: lazybuilder-protocol
description: Own neutral shared Paper/Fabric contracts under shared/protocol: request/result payloads, identifiers, wire validation/defaults, compatibility semantics, and current World/Map/Transfer protocol shapes. Do not use for desktop loopback HTTP, Paper implementation logic, or UI presentation.
---

# LazyBuilder Shared Protocol

Own neutral Paper/Fabric wire semantics only. Follow `docs/04-system/development-discipline.md`, `docs/04-system/networking.md`, and `CONTEXT.md`.

## Entry gate

Use this Skill only when the changed decision must be shared neutrally between Paper and Fabric.

```text
request/result payload shape
identifier semantics
wire validation/default/optional behavior
compatibility/version semantics
bounded shared transfer contract
```

Do not enter merely because two modules are involved. Desktop loopback HTTP is `lazybuilder-desktop-runtime`; Paper behavior is `lazybuilder-world-management`; rendering/interaction is `lazybuilder-ui`.

Before mutation, identify at least one direct producer and consumer of the contract and the first evidence that shows the current shared shape is wrong.

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

## Failure patterns

Classify the shared-contract failure before editing:

```text
SHAPE             producer/consumer disagree on request/result structure
VALIDATION        invalid or oversized input is accepted/rejected incorrectly
DEFAULT_OPTIONAL  absence/default semantics diverge between ends
IDENTIFIER        entity/world/request identifiers are unstable or ambiguous
VERSIONING        breaking compatibility is hidden or unnecessary version churn occurs
CAPABILITY        advertised capability differs from verified backend support
ADAPTER_DRIFT     shared type is correct but Paper/Fabric adapter is stale
TRANSPORT_LEAK    transport/implementation detail has entered neutral contract
DOMAIN_LEAK       Paper/world business rule has entered shared contract
PRESENTATION_LEAK UI-only state has entered shared contract
TRANSFER_BOUND    shared transfer size/chunk/order/integrity rule is unsafe
UNKNOWN           evidence cannot yet separate the above
```

Fix the neutral contract only when it is the first wrong owner. `ADAPTER_DRIFT` usually means the shared contract needs no change.

## Procedure

```text
name exact caller-visible contract
→ identify direct producer + consumer
→ capture one failing/mismatched round trip or contract example
→ classify failure
→ prove it is a shared wire concern
→ reuse existing neutral type/validation
→ change smallest payload/semantic surface
→ bump compatibility/version only when actually required
→ update direct Paper/Fabric adapters lockstep
→ update focused round-trip/validation tests
→ targeted compile/contract proof
→ hand domain or presentation residue to its owner
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
- desktop loopback control and Minecraft client/server data plane remain separate boundaries;
- optional/default semantics are defined once in the shared contract and tested at both ends;
- malformed/oversized data must fail deterministically at the wire boundary rather than becoming domain/UI state;
- a compatibility shim exists only for a supported real consumer and has an explicit retirement condition;
- protocol fields do not exist solely to simplify one renderer or one implementation class.

## Proof matrix

```text
shape / encode-decode / validation / defaults
→ focused shared round-trip or contract test

Paper and Fabric adapter alignment
→ compile/build tests for both direct consumers

capability catalog
→ contract test against the authoritative backend-supported values

compatibility/version migration
→ old/new fixture or explicit supported-version contract test

real client/server interoperability, ordering, disconnect/reconnect timing
→ LIVE_SERVER + real Fabric client using exact artifacts
```

Compile success does not prove a round trip. A round-trip unit test does not prove live plugin-channel timing or reconnect behavior.

## Handoff / exit contract

Sequential ownership only:

```text
neutral payload/validation becomes correct
→ lazybuilder-world-management implements Paper/domain behavior
→ lazybuilder-ui presents returned state when needed
```

Desktop HTTP remains outside this chain and routes to `lazybuilder-desktop-runtime`.

Finish when:

- one neutral shared definition exists;
- producer and consumer adapters agree with it;
- version/default/bounds semantics are explicit;
- matching round-trip/build proof is complete for the current context;
- remaining domain/UI/live interoperability residue is named precisely.

Do not create a second protocol namespace, generic compatibility framework, or transport solely for future possibilities.
