# Modular maintenance rules

LazyBuilder is maintained as independently evolving components.

## Module ownership

- `modules/world-manager` owns world lifecycle, import/export, archive/restore, settings, conversion, transfer, and map/world control.
- `modules/utilities-manager` owns builder convenience features only.
- `client/fabric` owns in-game Fabric presentation and transport clients.
- `EngineData/Frontend/RustApp` is the canonical desktop app and owns Server-Manager and Plugin-Manager desktop responsibilities plus presentation/control of World-Manager through its explicit bridge.

## Change rules

1. A feature is changed in its owning module only.
2. Modules must not import another module's implementation packages.
3. Cross-module contracts must stay narrow and versioned.
4. Each Paper module owns its own plugin metadata, configuration, tests, artifact name, and version.
5. A module update must not require unrelated modules to change unless a shared contract actually changes.
6. Feature packages inside Utilities-Manager have independent lifecycle registration and configuration keys.
7. Background workers are forbidden unless the owning feature has a demonstrated continuous-work requirement.
8. Paper APIs are preferred over NMS and custom runtime layers.
9. CI must compile/test Paper modules, compile the Fabric client, and check the Tauri/Svelte/Rust desktop before a structural migration is accepted.
10. Legacy duplicate source trees must be removed once the canonical owner passes CI; compatibility storage fallbacks may remain only where they protect existing user data.
11. Runtime folders are created only when an active owner uses them. Do not create placeholder storage for lifecycle states that are metadata-only.

## Release model

Components may evolve independently, for example:

```text
World-Manager       0.4.x
Utilities-Manager   0.2.x
Fabric Client       0.3.x
LazyBuilder Desktop 0.1.x
```

The suite version is a product/release label, not a requirement that every component share the same implementation version.
