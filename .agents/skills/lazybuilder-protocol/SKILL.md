---
name: lazybuilder-protocol
description: Own neutral shared Paper/Fabric contracts under shared/protocol: request/result payloads, identifiers, wire validation/defaults, compatibility/version semantics, and current World/Map/Transfer protocol shapes. Do not use for desktop loopback HTTP, Paper implementation logic, or UI presentation.
---

# LazyBuilder Shared Protocol

Own neutral Paper/Fabric wire semantics only. Follow `docs/04-system/development-discipline.md` and `docs/04-system/networking.md`.

## Entry gate

Use this Skill only when a decision must be shared neutrally between Paper and Fabric:

```text
request/result payload shape
identifier semantics
wire validation/default/optional behavior
compatibility/version semantics
capability advertisement
bounded shared transfer contract
```

Do not enter merely because two modules are involved. Desktop loopback HTTP belongs to `lazybuilder-desktop-runtime`; Paper domain behavior belongs to `lazybuilder-world-management`; presentation belongs to `lazybuilder-ui`.

Before mutation, identify at least one direct producer and one direct consumer plus one concrete failing/mismatched round trip or contract example.

## Owns

```text
shared request/result payloads
shared identifiers
wire validation/default/optional semantics
World Control contract
Map Action contract
shared transfer framing/bounds
Paper/Fabric compatibility/version semantics
neutral source ownership under shared/protocol
```

## Does not own

```text
Paper implementation/domain behavior → lazybuilder-world-management
Desktop/Fabric presentation          → lazybuilder-ui
desktop loopback HTTP/control        → lazybuilder-desktop-runtime
```

## Current canonical contracts

### World Control V5

Current general managed-world contract includes:

```text
ListWorlds + canManage/canTeleport
CreateWorld
TeleportWorld
ArchiveWorld / RestoreWorld
DuplicateWorld
DeleteWorld
world settings
ExportWorld / ImportWorld
GetExportFormats / ExportFormats
InspectImport
DiscardImport
```

`WorldSummary` carries durable/presentation metadata only.

Do not reintroduce:

```text
LoadWorld
UnloadWorld
SetAutoLoad
runtimeState in WorldSummary
autoLoad in WorldSummary
CloneWorld
```

Import inspection is a read-only review step after upload completion. Final Import is a separate explicit mutation and must re-run authoritative validation. `DiscardImport` cleans only the requesting player's currently owned reviewed artifact.

### Map Action V2

```text
TeleportLocation
ExportArea
CurrentWorldRequest
CurrentWorldResult
CurrentWorldCleared
```

Entering an unmanaged world explicitly clears previous managed-world presentation state.

### Transfer

`lazybuilder:transfer` owns file bytes only.

```text
BEGIN
→ bounded session/chunks/window
→ ordered transfer
→ size + SHA-256 validation
→ FINISH / ABORT
```

Do not tunnel files through World/Map payloads and do not create a second HTTP/WebSocket/cloud transfer plane for in-game world transfer.

## Failure taxonomy

```text
SHAPE             producer/consumer disagree on payload structure
VALIDATION        malformed/oversized input rules are wrong
DEFAULT_OPTIONAL  absence/default semantics diverge
IDENTIFIER        world/request/entity identifiers are unstable/ambiguous
VERSIONING        breaking compatibility is hidden or version churn is unnecessary
CAPABILITY        advertised capability differs from verified backend support
ADAPTER_DRIFT     neutral contract is correct; Paper/Fabric adapter is stale
TRANSPORT_LEAK    implementation/transport detail entered neutral contract
DOMAIN_LEAK       Paper/world business rule entered neutral contract
PRESENTATION_LEAK UI-only state entered neutral contract
TRANSFER_BOUND    chunk/window/order/integrity/session rule is unsafe
UNKNOWN           evidence cannot separate the above
```

`ADAPTER_DRIFT` usually means the shared contract should not change.

## Versioning rules

- bump protocol compatibility only for an actual incompatible wire contract;
- additive optional data does not automatically justify a version bump when old/new peers can still interoperate safely;
- removed/renamed required fields or changed semantics require explicit compatibility reasoning;
- compatibility shims exist only for a supported real consumer and must have a retirement condition;
- do not maintain parallel active protocol versions for hypothetical future compatibility.

## Canonical procedure

```text
name exact caller-visible contract
→ identify direct producer + consumer
→ capture one failing/mismatched round trip
→ classify failure
→ prove shared protocol is the first wrong owner
→ reuse existing neutral type/validation
→ change smallest payload/semantic surface
→ change version only when compatibility requires it
→ update direct Paper/Fabric adapters lockstep
→ update focused round-trip/validation tests
→ targeted build/contract proof
→ freeze the neutral contract
→ hand only the typed contract to the next semantic owner
→ STOP
```

## Invariants

- neutral contracts never depend on Paper implementation classes;
- shared types are not duplicated inside World Manager or Fabric;
- payloads stay small, typed, bounded, and transport-neutral;
- Paper remains authorization/domain authority even when capability flags are sent for presentation;
- capability catalogs contain only verified backend-supported values;
- optional/default semantics are defined once and tested at both ends;
- malformed/oversized data fails deterministically at the wire boundary;
- desktop loopback and Minecraft client/server data planes remain separate;
- implementation-language/build mechanics are not protocol changes;
- file transfer ownership handoff is explicit; completed bytes never become an unowned inbox;
- no fallback formats, command-string tunneling, second transport, or generic compatibility framework without a supported requirement;
- protocol fields do not exist solely to simplify one renderer or implementation class.

## Proof matrix

```text
shape / encode-decode / validation / defaults
→ focused shared round-trip/contract test

Paper + Fabric adapter alignment
→ build tests for both direct consumers

capability catalog
→ contract test against authoritative backend-supported values

compatibility migration
→ old/new fixture or explicit supported-version test

real ordering/disconnect/reconnect/plugin-channel behavior
→ LIVE_SERVER + real Fabric client using exact artifacts
```

Compile success does not prove a round trip. Unit round trip does not prove live plugin-channel timing.

## Handoff / exit contract

Protocol handoff is a **frozen neutral contract**, not a partially designed domain feature.

```text
protocol → lazybuilder-world-management
handoff: request/result types + identifiers + version + validation/default/bounds + capability semantics
World Management implements authorization/domain/filesystem behavior without redefining wire meaning

protocol → lazybuilder-ui
only when the returned state is already presentation-ready
handoff: typed neutral result/capability only
UI must not invent server/domain meaning for an underspecified wire field

protocol ↛ lazybuilder-desktop-runtime
Desktop loopback HTTP/Tauri IPC is a separate transport/contract boundary; shared protocol is not a generic DTO library for desktop reuse
```

For a new world capability the normal chain is strictly:

```text
protocol contract complete
→ STOP protocol ownership
→ world-management implements domain behavior
→ world-management returns canonical domain result
→ ui presents it
```

Finish when:

- one neutral shared definition exists;
- direct producer and consumer agree with it;
- version/default/bounds/capability semantics are explicit;
- matching round-trip/build proof is complete for the available context;
- downstream owners can consume the frozen contract without reopening protocol semantics;
- remaining domain/UI/live interoperability residue is named precisely.

Do not create a second protocol namespace, transport, compatibility framework, or speculative payload surface.