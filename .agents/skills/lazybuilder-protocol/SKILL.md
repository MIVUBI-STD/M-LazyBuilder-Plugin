---
name: lazybuilder-protocol
description: Own neutral shared Paper/Fabric contracts under shared/protocol: request/result payloads, identifiers, wire validation/defaults, compatibility/version semantics, and current World/Map/Transfer protocol shapes. Do not use for desktop loopback HTTP, Paper implementation logic, or UI presentation.
---

# LazyBuilder Shared Protocol

Own neutral Paper↔Fabric wire semantics only. Global diagnosis/proof rules come from `docs/04-system/development-discipline.md`; networking/product boundaries come from `docs/04-system/networking.md`; cross-owner handoff comes from `docs/04-system/skill-routing.md`.

This Skill is consumer-neutral: ChatGPT and Codex use the same wire-contract reasoning. Adapt only execution mechanics to available tools; if a live round trip cannot be exercised, preserve that `LIVE_RUNTIME` residue explicitly rather than inferring it from source/build evidence.

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

Do not enter merely because two modules are involved. Desktop loopback HTTP is Desktop Runtime; Paper domain behavior is World Management; presentation is UI.

Before mutation identify one direct producer, one direct consumer, and one concrete failing/mismatched contract example.

## Owns

```text
shared request/result payloads
shared identifiers
wire validation/default/optional semantics
World Control contract
Map Action contract
shared transfer framing/bounds
Paper/Fabric compatibility/version semantics
neutral source under shared/protocol
```

## Context loading

### Default load

```text
this SKILL.md
→ exact shared/protocol type or validator involved
→ one direct producer
→ one direct consumer
→ one concrete mismatched/failing round-trip example
```

For a localized wire defect, do not preload World/UI/Desktop docs.

### Required if

```text
current canonical World/Map/Transfer contract scope or networking boundary is uncertain
→ docs/04-system/networking.md

version compatibility semantics are material
→ exact supported producer/consumer versions + focused compatibility evidence

capability advertisement is disputed
→ authoritative backend capability source + direct consumer

wire defect may actually be domain/presentation/desktop transport leakage
→ skill-routing.md to resolve owner before changing shared types
```

### Do not load if

- do not load world-management docs merely because the payload concerns worlds;
- do not load UI docs merely because Fabric renders the result;
- do not load Desktop Runtime docs for Paper↔Fabric contracts unless the issue is actually desktop loopback/Tauri IPC leakage;
- do not scan unrelated protocol namespaces when one producer/consumer pair can decide the contract;
- do not load historical versions unless a currently supported peer requires compatibility reasoning.

### Escalate when

Escalate only when exact shared types + producer + consumer cannot determine whether the first wrong owner is Protocol, adapter drift, domain leakage, presentation leakage, or transport leakage. Gather the next separating round-trip evidence before loading more context.

## Canonical contracts

### World Control V5

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

`WorldSummary` carries durable/presentation metadata only. Do not reintroduce `LoadWorld`, `UnloadWorld`, `SetAutoLoad`, runtime-state product metadata, `autoLoad`, or Clone terminology.

Import inspection is read-only review after upload completion; final Import is a separate explicit mutation and revalidates. `DiscardImport` cleans only the requesting player's owned reviewed artifact.

### Map Action V2

```text
TeleportLocation
ExportArea
CurrentWorldRequest
CurrentWorldResult
CurrentWorldCleared
```

Entering an unmanaged world clears prior managed-world presentation state.

### Transfer

`lazybuilder:transfer` owns file bytes only:

```text
BEGIN
→ bounded session/chunks/window
→ ordered transfer
→ size + SHA-256 validation
→ FINISH / ABORT
```

Do not tunnel files through World/Map payloads or create a second in-game transfer plane.

## Failure taxonomy

```text
SHAPE             producer/consumer payload structure differs
VALIDATION        malformed/oversized input rule wrong
DEFAULT_OPTIONAL  absence/default semantics diverge
IDENTIFIER        identifiers unstable/ambiguous
VERSIONING        compatibility change hidden or version churn unnecessary
CAPABILITY        advertised capability differs from backend support
ADAPTER_DRIFT     neutral contract correct; direct adapter stale
TRANSPORT_LEAK    non-neutral transport detail entered shared contract
DOMAIN_LEAK       Paper/world business rule entered shared contract
PRESENTATION_LEAK UI-only state entered shared contract
TRANSFER_BOUND    chunk/window/order/integrity/session rule unsafe
UNKNOWN           next separating round-trip evidence required
```

`ADAPTER_DRIFT` never justifies changing correct shared types. Cross-owner labels route through the global bridge in `development-discipline.md`.

## Versioning rules

- bump compatibility only for an actually incompatible wire contract;
- additive optional data does not require a bump when old/new peers interoperate safely;
- removed/renamed required fields or changed semantics require explicit compatibility reasoning;
- compatibility shims exist only for a supported real consumer and have a retirement condition;
- do not keep parallel protocol versions for hypothetical future compatibility.

## Canonical procedure

```text
name exact caller-visible contract
→ identify producer + consumer
→ capture failing/mismatched round trip
→ classify local subtype
→ confirm Protocol remains first wrong owner
→ reuse existing neutral type/validation
→ smallest payload/semantic change
→ change version only if compatibility requires it
→ update direct adapters lockstep
→ focused contract proof available in the current context
→ typed handoff if ownership changes
→ STOP
```

## Invariants

- shared contracts never depend on Paper implementation classes;
- shared types are not duplicated in World Manager/Fabric;
- payloads stay small, typed, bounded, transport-neutral;
- Paper remains authorization/domain authority;
- capability catalogs advertise verified backend-supported values only;
- optional/default semantics are defined once and tested at both ends;
- malformed/oversized data fails deterministically at the wire boundary;
- desktop loopback and Minecraft client/server data planes stay separate;
- file-transfer ownership handoff is explicit; completed bytes never become an unowned inbox;
- no fallback formats, command-string tunneling, second transport, or generic compatibility framework without a supported requirement;
- protocol fields do not exist solely to simplify one renderer/implementation class.

## Proof matrix

```text
shape / encode-decode / validation / defaults → EXECUTED_SOURCE
Paper + Fabric adapter compile/alignment       → EXECUTED_SOURCE
capability catalog deterministic contract      → EXECUTED_SOURCE
multi-owner deterministic integration          → INTEGRATION_FIXTURE
supported old-new compatibility boundary       → EXECUTED_SOURCE or INTEGRATION_FIXTURE
real plugin-channel ordering/reconnect/interoperability → LIVE_RUNTIME
```

Compile success does not prove a round trip. Unit round trip does not prove plugin-channel timing. `VISUAL_RENDERED` does not prove protocol interoperability.

## Handoff / exit

Use canonical typed handoffs from `skill-routing.md`.

```text
to world-management
→ exact neutral types + identifiers + defaults/bounds + compatibility/version semantics

to ui
→ canonical payload/result/capability meaning when semantics are already correct

adapter drift
→ hand to stale adapter owner without modifying shared contract
```

Desktop HTTP remains outside this chain.

Finish when one neutral shared definition exists, producer/consumer agree, version/default/bounds/capability semantics are explicit, and matching proof covers the changed contract at the available context ceiling. Stop before second protocol namespaces, transports, generic compatibility frameworks, or speculative payloads.