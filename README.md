# LazyBuilder

LazyBuilder is a modular Minecraft Java 1.21.4 builder-server workspace.

It is not a single world-management plugin. The product is split into independently maintained components:

```text
LazyBuilder
├── Desktop Application
│   ├── Server-Manager
│   └── Plugin-Manager
├── Paper Modules
│   ├── World-Manager
│   └── Utilities-Manager
└── Client
    └── Fabric integration
```

## Repository layout

```text
EngineData/Frontend/RustApp/  Canonical LazyBuilder desktop app (Tauri 2 + Svelte 5 + Rust)
modules/world-manager/        World lifecycle, import/export, archive, settings, transfer
modules/utilities-manager/    Small builder/server convenience features
client/fabric/                Fabric client and in-game World-Manager surfaces
docs/                         Product, architecture, UI, and operational contracts
```

The desktop has one source authority: `EngineData/Frontend/RustApp`. Svelte owns presentation/application state; Rust owns desktop-native process, filesystem, Plugin-Manager, Server-Manager, and World-Manager client behavior.

Each manager owns its own source boundary, tests, configuration, artifact, and version. Unrelated modules must remain independently updateable.

The active development authority is `Local`; `main` remains the stable/release authority. Structural work is validated through CI before live-server testing.
