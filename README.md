# LazyBuilder

LazyBuilder is a modular Minecraft Java 1.21.4 builder-server workspace.

```text
LazyBuilder
├── Launcher / Desktop
│   ├── Server Manager
│   ├── Plugin Manager
│   └── Client Setup
├── Paper Plugins
│   ├── World Manager
│   └── Utilities Manager
├── Fabric Mods
│   ├── Map Manager
│   ├── Utility Manager
│   └── Performance Manager (deferred research source)
└── Shared Contracts
    └── Protocol
```

## Current phase: Local PC validation

The current next step is **not additional architecture development**. The product should now be tested on a representative Windows PC from the installed user experience outward.

Start with:

[`docs/05-operations/local-pc-validation-plan.md`](docs/05-operations/local-pc-validation-plan.md)

Required order:

```text
1. LazyBuilder-Setup-Local.exe
2. Launcher first run
3. managed Java + Paper server
4. Plugin Manager
5. World Manager
6. Utilities Manager
7. Modrinth / Client Setup
8. Map Manager in real Minecraft
9. Utility Manager in real Minecraft
10. Paper ↔ Fabric interoperability
11. restart / reconnect / update / reboot recovery
12. large-world / storage-pressure / conversion tests
13. final repository audit
```

For normal Local PC acceptance testing, use the canonical installer artifact from the successful `Verify` run for the exact `Local` revision. Do **not** use a local source build as the normal test product.

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
└── performance-manager/         deferred performance research source

shared/
└── protocol/                    Neutral Paper/Fabric wire contracts

docs/                            Canonical product/system/operations docs
scripts/                         Repository verification/build support
tooling/                         Repository-owned bootstrap/distribution tooling
```

The root is intentionally reserved for repository-level entrypoints and policy files. Runtime implementation belongs under `apps/`, `plugins/`, `mods/`, or `shared/`.

## Ownership rules

- `apps/launcher/` owns desktop presentation and desktop-native Server/Plugin/Client Setup management.
- `plugins/` contains server-side Paper plugins only.
- `mods/` contains Minecraft Fabric client mods only.
- `shared/protocol/` contains neutral Paper/Fabric contracts only.
- External build/edit tools such as Vanilla, Axiom, and WorldEdit remain external specialist owners.
- One Manager produces one deployable artifact and does not import another Manager's implementation packages.
- Performance Manager remains deferred until measured evidence and an explicit product decision justify promotion.

## Branch authority

```text
Local = active development / source / LocalTest authority
main  = stable / release authority
```

Do not promote to `main` merely because remote CI is green. Complete target-machine validation and final audit first.

## Verification authority

Current readiness is determined from:

```text
current Local revision
→ Verify
→ relevant Paper Runtime Proof
→ Local PC validation
→ final audit
→ explicit promotion decision
```

See:

- [`docs/05-operations/current-verification.md`](docs/05-operations/current-verification.md) for proof boundaries;
- [`docs/05-operations/local-pc-validation-plan.md`](docs/05-operations/local-pc-validation-plan.md) for the exact next-to-do on the Local PC;
- [`docs/README.md`](docs/README.md) for documentation routing.
