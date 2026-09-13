---
name: lazybuilder-protocol
description: Specialist for client-visible shared Paper/Fabric contracts under shared/protocol: request/result types, wire semantics, compatibility, and bounded transport contracts. Use when the shared protocol itself is the primary change.
---

# LazyBuilder Shared Protocol

Own shared client/server wire semantics only. Do not absorb Paper implementation logic, desktop HTTP control, or Fabric presentation.

## Use This Owner For

- shared request/result payloads and identifiers;
- Paper/Fabric compatibility contract;
- map-action, transfer, world-control wire semantics;
- protocol validation/default/optional behavior;
- shared source ownership under `shared/protocol`.

Route elsewhere when the primary owner is:

```text
Paper world behavior          → lazybuilder-world-management
Fabric/Xaero presentation     → lazybuilder-client-ui
desktop loopback HTTP/runtime → lazybuilder-desktop-runtime
Svelte desktop presentation   → lazybuilder-desktop-ui
```

## Canonical Context

1. `docs/04-system/networking.md`
2. `docs/04-system/world-control-bridge.md` when relevant
3. `docs/04-system/skill-routing.md`
4. exact shared protocol + direct adapters

## Rules

- Shared protocol owns neutral contracts, never Paper implementation classes.
- Keep requests/results small, typed, bounded, and transport-neutral.
- Do not duplicate shared types in World Manager or Fabric.
- Avoid fallback message formats, parallel protocol versions, or command-string tunneling without a real compatibility requirement.
- Change protocol only when the product contract requires it; implementation-language/build mechanics are not protocol changes.
- Keep desktop loopback control distinct from the Minecraft client/server data plane.

## Proof

Static/compile proof can establish shared ownership and contract alignment. Actual client/server interoperability requires local/live proof when behavior depends on Minecraft runtime.
