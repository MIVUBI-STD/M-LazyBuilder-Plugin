---
name: lazybuilder-protocol
description: Own neutral shared Paper/Fabric contracts under shared/protocol: request/result payloads, identifiers, wire validation/defaults, and compatibility semantics. Do not use for desktop loopback HTTP, Paper implementation logic, or UI presentation.
---

# LazyBuilder Shared Protocol

Own neutral Paper/Fabric wire semantics only. Follow `docs/04-system/development-discipline.md` and `docs/04-system/skill-routing.md`.

## Owns

```text
shared request/result payloads
shared identifiers
wire validation/default/optional semantics
map-action contracts
shared transfer contracts
Paper/Fabric compatibility semantics
source ownership under shared/protocol
```

## Does Not Own

```text
Paper implementation behavior      → world-management
Fabric/Xaero presentation          → client-ui
desktop loopback HTTP/control      → desktop-runtime
Svelte/Tauri presentation          → desktop-ui
```

A `WorldControl*` type located in `shared/protocol` is owned here only for its neutral shared wire semantics. Desktop HTTP request routing/authentication/session behavior remains Desktop Runtime.

## Canonical Context

1. `docs/04-system/development-discipline.md`
2. `docs/04-system/networking.md`
3. `docs/04-system/skill-routing.md`
4. exact `shared/protocol` source + direct Paper/Fabric adapters

## Procedure

```text
name exact caller-visible contract
→ prove it is a shared wire concern
→ reuse existing neutral type/validation
→ change smallest payload/semantic surface
→ update direct adapters only as required
→ targeted compile/contract proof
→ STOP
```

## Protocol Invariants

- neutral contracts never depend on Paper implementation classes;
- shared types are not duplicated in World Manager or Fabric;
- requests/results stay small, typed, bounded, and transport-neutral;
- no fallback formats, parallel protocol versions, or command-string tunneling without a supported compatibility requirement;
- implementation-language/build mechanics are not protocol changes;
- desktop loopback control and Minecraft client/server data plane remain separate boundaries.

## Proof Boundary

Static/compile proof can establish shared ownership and adapter alignment. Real Paper/Fabric interoperability requires the appropriate local/live client-server proof.
