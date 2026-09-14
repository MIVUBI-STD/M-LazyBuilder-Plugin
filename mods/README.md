# LazyBuilder Fabric Mods

LazyBuilder client-side functionality is organized as exactly three Manager-owned Fabric mods.

```text
mods/
├── map-manager/          implemented / scope locked
├── utility-manager/      implemented / scope locked
└── performance-manager/  implemented baseline / scope locked
```

## Ownership

- **Map Manager** — world/map workflow, navigation, transfer UI, world settings UI, current managed-world state, Map Export Area, and Fabric-to-Paper World-Manager transport.
- **Utility Manager** — passive non-building client convenience.
- **Performance Manager** — performance/resource observation and the narrow background-FPS fallback that yields to Dynamic FPS.

Each Manager is one Fabric mod and one output JAR. Managers do not import another Manager's implementation packages.

Building/editing remains owned by Vanilla, Axiom, WorldEdit/FAWE, MetaBrushes, and other specialist tools. LazyBuilder must not add a duplicate generic build-tool layer.

Architecture lock: `docs/04-system/client-cross-manager-audit-lock.md`.
Proof handoff: `docs/05-operations/client-implementation-proof-handoff.md`.
