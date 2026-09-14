# LazyBuilder

LazyBuilder is a modular Minecraft Java 1.21.4 builder-server workspace.

It is not a single world-management plugin. The product is split into independently maintained components:

```text
LazyBuilder
├── Desktop Application
│   ├── Server-Manager
│   └── Plugin-Manager
├── Shared Contracts
│   └── Protocol
├── Paper Modules
│   ├── World-Manager
│   └── Utilities-Manager
└── Client Managers
    ├── Map Manager          (implemented)
    ├── Utility Manager      (planned)
    └── Performance Manager  (planned)
```

## Repository layout

```text
EngineData/Frontend/RustApp/  Canonical LazyBuilder desktop app (Tauri 2 + Svelte 5 + Rust)
shared/protocol/               Neutral Paper/Fabric wire contracts and shared value types
modules/world-manager/         World lifecycle, import/export, archive, settings, transfer
modules/utilities-manager/     Small builder/server convenience features
client/map-manager/            LazyBuilder Map Manager Fabric source
docs/                          Canonical product/system/operations docs
```

The current implemented Fabric mod is **LazyBuilder Map Manager**. Utility Manager and Performance Manager remain separate planned client managers; each Manager is one Fabric mod and one output JAR.

The desktop has one source authority: `EngineData/Frontend/RustApp`. Svelte owns presentation/application state; Rust owns desktop-native process, filesystem, Plugin-Manager, Server-Manager, and World-Manager client behavior.

Shared client/server transport types are owned by `shared/protocol`; Fabric must not compile implementation source directly from a Paper module.

Each manager owns its own source boundary, tests, configuration, artifact, and version. Unrelated modules must remain independently updateable. One Manager produces one deployable mod/JAR; internal subfeatures must not become unnecessary standalone mods.

The active development authority is `Local`; `main` remains the stable/release authority. Structural work is validated through CI before live-server testing.

## Verification authority

Current readiness is determined from the **latest `Verify` workflow for the exact current `Local` HEAD**, not from an older completion document or previously green SHA. See `docs/05-operations/current-verification.md` for the proof hierarchy and the boundary between `REMOTE_GITHUB`, `LOCAL_CODE`, and `LIVE_SERVER` evidence.
