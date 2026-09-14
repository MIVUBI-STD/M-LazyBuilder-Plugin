# LazyBuilder client modules

LazyBuilder client-side functionality is organized as Manager-owned Fabric mods.

Current implemented client mod:

```text
LazyBuilder Map Manager
└── client/map-manager/
```

Map Manager owns the existing in-game world/map surfaces, navigation, transfer UI, world settings UI, and Fabric-to-Paper World-Manager transport. It does not own server-side World-Manager lifecycle, registry, filesystem, conversion, backup, import/export, or deletion logic; those remain canonical server-side responsibilities.

Planned client managers are Utility Manager and Performance Manager. Each Manager must remain one Fabric mod and one JAR; subfeatures must not become separate mods.

Current source boundaries:

```text
client/
├── map-manager/          implemented
├── utility-manager/      planned
└── performance-manager/  planned
```
