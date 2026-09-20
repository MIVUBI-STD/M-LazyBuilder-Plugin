# LazyBuilder Fabric Mods

LazyBuilder client-side functionality is organized as independent Fabric modules with explicit ownership.

```text
mods/
├── map-manager/          world / map / transfer workflow
├── utility-manager/      passive non-building convenience
├── performance-manager/  performance/resource policy
└── builder-utilities/    Axiom-first building extension layer
```

## Ownership

- **Map Manager** — world/map workflow, navigation, transfer UI, world settings UI, current managed-world state, Map Export Area, and Fabric-to-Paper World-Manager transport.
- **Utility Manager** — passive non-building client convenience.
- **Performance Manager** — performance/resource observation and the narrow background-FPS fallback that yields to Dynamic FPS.
- **Builder Utilities** — Axiom-first extension point for building capabilities that are missing from Axiom. Axiom remains the primary editor and UX. FAWE and ezEdits are reference/donor implementations only; Builder Utilities must not introduce them as permanent runtime authorities.

The former Terraform prototype is retired from active source; its historical implementation remains available through Git history.

Each Manager/feature module is one Fabric mod and one output JAR. Modules do not import another LazyBuilder Manager's implementation packages.

## Builder architecture

The previous rule that LazyBuilder must not own a building/editing layer is superseded for the Builder Utilities scope by the explicit product requirement to extend Axiom while preserving Axiom as the primary editor.

```text
Axiom original editor / UX
        ↓
LazyBuilder Builder Utilities
        ↓
Axiom public client API first
        ↓
LazyBuilder-native missing capabilities
```

Rules:

- preserve original Axiom behavior unless a specific missing capability requires an extension;
- use Axiom's public client API before considering internal hooks or mixins;
- do not copy or repackage Axiom code into LazyBuilder;
- do not make FAWE or ezEdits permanent runtime dependencies merely to reuse their behavior;
- migrate only capabilities that materially improve Axiom, implemented under LazyBuilder ownership;
- keep world/server authority and shared wire semantics in their existing canonical owners.

Architecture boundary: `docs/04-system/client-cross-manager-audit-lock.md`.
Proof handoff: `docs/05-operations/client-implementation-proof-handoff.md`.
