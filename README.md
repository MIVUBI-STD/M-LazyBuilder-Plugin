# LazyBuilder

LazyBuilder is a modular Minecraft Java 1.21.4 builder-server workspace.

```text
LazyBuilder
├── Launcher / Desktop
│   ├── Server-Manager
│   └── Plugin-Manager
├── Paper Plugins
│   ├── World-Manager
│   └── Utilities-Manager
├── Fabric Mods
│   ├── Map Manager
│   ├── Utility Manager
│   └── Performance Manager
└── Shared Contracts
    └── Protocol
```

## Repository layout

```text
apps/
└── launcher/                    Tauri 2 + Svelte 5 + Rust desktop app

plugins/
├── world-manager/               Paper world lifecycle/import-export authority
└── utilities-manager/           Paper builder/server conveniences

mods/
├── map-manager/                 Fabric world/map/transfer client
├── utility-manager/             Fabric passive client convenience
└── performance-manager/         Fabric performance/resource coordination

shared/
└── protocol/                    Neutral Paper/Fabric wire contracts

docs/                            Canonical product/system/operations docs
scripts/                         Repository verification/build support
```

The root is intentionally reserved for repository-level entrypoints and policy files. Runtime implementation belongs under `apps/`, `plugins/`, `mods/`, or `shared/`.

## Ownership rules

- `apps/launcher/` owns desktop presentation and desktop-native Server/Plugin management.
- `plugins/` contains server-side Paper plugins only.
- `mods/` contains Minecraft Fabric client mods only.
- `shared/protocol/` contains neutral Paper/Fabric contracts only.
- External build/edit tools such as Vanilla, Axiom, and WorldEdit remain external specialist owners.
- One Manager produces one deployable artifact and does not import another Manager's implementation packages.

## Branch authority

```text
Local = active development / source authority
main  = stable / release authority
```

## Verification authority

Current readiness is determined from the latest `Verify` workflow for the exact current `Local` HEAD. See `docs/05-operations/current-verification.md` for the boundary between `REMOTE_GITHUB`, `LOCAL_CODE`, and `LIVE_SERVER` proof.
