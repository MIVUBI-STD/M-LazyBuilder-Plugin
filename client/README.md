# LazyBuilder client modules

LazyBuilder client-side functionality is organized as Manager-owned Fabric mods.

Current implemented client managers:

```text
LazyBuilder Client Suite
├── Map Manager
├── Utility Manager
└── Performance Manager
```

Map Manager owns the existing in-game world/map surfaces, navigation, transfer UI, world settings UI, and Fabric-to-Paper World-Manager transport. It does not own server-side World-Manager lifecycle, registry, filesystem, conversion, backup, import/export, or deletion logic; those remain canonical server-side responsibilities.

Utility Manager owns passive non-building client convenience only. Its active scope is locked separately and must not absorb building or performance ownership.

Performance Manager is currently at the C3 baseline: one independent Fabric mod that passively detects optional performance/graphics capabilities. It does not yet apply FPS policy, profiles, graphics changes, or renderer integration.

Each Manager remains one Fabric mod and one JAR; subfeatures must not become separate mods.

Current source boundaries:

```text
client/
├── map-manager/          implemented
├── utility-manager/      implemented / scope locked
└── performance-manager/  scaffolded / capability detection baseline
```
