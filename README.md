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

## Current phase: source remediation and synchronization

Installed Local PC testing on 15 September 2026 exposed several cross-boundary defects. The repository is therefore back in **source remediation**. Do not start another Local PC acceptance pass until the remediation handoff has no unresolved source-side P0/P1 item and the exact current `Local` HEAD passes repository/CI verification.

Current authority:

[`docs/05-operations/local-pc-remediation-2026-09-15.md`](docs/05-operations/local-pc-remediation-2026-09-15.md)

The older Local PC validation plan remains the acceptance procedure to use **after** source remediation is complete:

[`docs/05-operations/local-pc-validation-plan.md`](docs/05-operations/local-pc-validation-plan.md)

Required order now:

```text
1. classify every reproduced Local PC defect
2. repair the owning source/module only
3. synchronize build/runtime/documentation contracts
4. add regression coverage for each reproducible source defect
5. run repository consistency checks
6. run Paper/Fabric/frontend/Rust verification
7. run Paper runtime smoke proof
8. build and verify canonical installer artifact
9. perform final source audit
10. only then reopen Local PC acceptance testing
```

A green compile is necessary but not sufficient. Local PC testing is blocked while the remediation handoff still marks a source-side P0/P1 as open.

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
Local = active development / source remediation / LocalTest authority
main  = stable / release authority
```

Do not promote to `main` merely because remote CI is green. Complete remediation, source audit, target-machine validation, and final audit first.

## Verification authority

Current readiness is determined from:

```text
current Local revision
→ remediation source audit
→ Verify
→ relevant Paper Runtime Proof
→ canonical installer proof
→ explicit decision to reopen Local PC validation
→ Local PC validation
→ final promotion audit
→ explicit promotion decision
```

See:

- [`docs/05-operations/local-pc-remediation-2026-09-15.md`](docs/05-operations/local-pc-remediation-2026-09-15.md) for the current remediation authority;
- [`docs/05-operations/current-verification.md`](docs/05-operations/current-verification.md) for proof boundaries;
- [`docs/05-operations/local-pc-validation-plan.md`](docs/05-operations/local-pc-validation-plan.md) for the later acceptance pass after remediation;
- [`docs/README.md`](docs/README.md) for documentation routing.
