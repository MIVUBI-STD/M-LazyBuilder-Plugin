# LazyBuilder

LazyBuilder is a modular Minecraft Java 1.21.4 builder-server workspace.

```text
LazyBuilder
├── Launcher / Desktop
│   ├── Server Manager
│   ├── Plugin Manager
│   └── Client Setup
├── Paper Core
│   ├── World Manager
│   └── Utilities Manager
├── Fabric Core
│   ├── Map Manager
│   ├── Utility Manager
│   └── Performance Manager
├── Builder Extension
│   └── Builder Utilities (Axiom-first; separate provisioning)
└── Shared
    └── Protocol
```

## Current phase: source remediation and synchronization

Installed Local PC testing on 15 September 2026 exposed several cross-boundary defects. The repository is therefore back in **source remediation**. Do not start another Local PC acceptance pass until the remediation handoff has no unresolved source-side P0/P1 item and the exact current `Local` candidate passes the required local/remote verification gates.

Current authority:

[`docs/05-operations/local-pc-remediation-2026-09-15.md`](docs/05-operations/local-pc-remediation-2026-09-15.md)

The Local PC validation plan remains the acceptance procedure to use **after** source remediation is complete:

[`docs/05-operations/local-pc-validation-plan.md`](docs/05-operations/local-pc-validation-plan.md)

Required order now:

```text
1. classify every reproduced Local PC defect
2. repair the owning source/module only
3. synchronize build/runtime/documentation contracts
4. add regression coverage for each reproducible source defect
5. run targeted local/module proof during development
6. run DEV.cmd finalize-local for the candidate revision
7. run integrated Verify for the exact candidate revision
8. inspect the canonical installer/provenance artifact
9. perform final source/repository audit
10. only then reopen Local PC acceptance testing
```

A green compile is necessary but not sufficient. Local PC testing is blocked while the remediation handoff still marks a source-side P0/P1 as open.

## Developer operations

All normal repository-level developer operations start from one command surface:

```text
DEV.cmd setup
DEV.cmd check
DEV.cmd build
DEV.cmd test
DEV.cmd update
DEV.cmd finalize-local
```

`DEV.cmd` delegates to `tooling/windows-toolchain/dev.ps1`; build/test/bootstrap details stay in their specialist owners. Do not add parallel root scripts or a second task runner for the same workflow.

Normal pushes to `Local` intentionally do not run the full final CI. Use targeted proof while iterating, then request integrated `Verify` at a reviewable checkpoint.

## Repository layout

```text
DEV.cmd                         sole repository-level developer entrypoint

apps/
└── launcher/                   Tauri 2 + Svelte 5 + Rust desktop app

plugins/
├── world-manager/              Paper world lifecycle/import-export authority
└── utilities-manager/          Paper builder/server conveniences

mods/
├── map-manager/                Fabric world/map/transfer client
├── utility-manager/            Fabric passive client convenience
├── performance-manager/        bounded Fabric client performance policy/diagnostics
└── builder-utilities/          Axiom-first builder extension; separate provisioning

shared/
└── protocol/                   neutral Paper/Fabric wire contracts

docs/                           canonical product/system/operations docs
scripts/                        repository/runtime verification utilities
tooling/windows-toolchain/      repository-owned developer/build/distribution control plane
```

The root is intentionally reserved for repository-level entrypoints and policy files. Runtime implementation belongs under `apps/`, `plugins/`, `mods/`, or `shared/`.

Generated state remains outside source authority:

```text
dist/Local/       canonical Local-channel distributables
.runtime-proof/   disposable runtime proof state/logs
.artifacts/       temporary staged proof/build artifacts
```

## Ownership rules

- `apps/launcher/` owns desktop presentation and desktop-native Server/Plugin/Client Setup management.
- `plugins/` contains server-side Paper plugins only.
- `mods/` contains Minecraft Fabric client mods only.
- `mods/builder-utilities/` is the current LazyBuilder-owned Axiom-first builder extension lane and remains outside Client Setup until provisioning is explicitly approved.
- `shared/protocol/` contains neutral Paper/Fabric wire contracts only.
- External build/edit tools such as Vanilla, Axiom, ezEdits, and WorldEdit remain external references/specialist owners.
- The former Terraform prototype has been retired from active source; Git history is its archive.
- One Manager produces one deployable artifact and does not import another Manager's implementation packages.
- V1 Client Setup owns exactly Map Manager, Utility Manager, and Performance Manager. Builder Utilities is a separate development artifact and must not enter the core bundle implicitly.
- `toolchain.json` owns supported developer toolchain policy; Maven/Gradle remain repository-wrapper owned.

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
→ DEV.cmd finalize-local where applicable
→ integrated Verify for exact candidate revision
→ canonical installer/provenance artifact
→ explicit decision to reopen Local PC validation
→ Local PC validation
→ final promotion audit
→ explicit promotion decision
```

Dedicated Paper/Launcher/visual workflows are focused evidence tools and do not replace integrated repository/package readiness verification.

See:

- [`docs/04-system/development-operations.md`](docs/04-system/development-operations.md) for durable development/delivery structure;
- [`docs/05-operations/local-pc-remediation-2026-09-15.md`](docs/05-operations/local-pc-remediation-2026-09-15.md) for the current remediation authority;
- [`docs/05-operations/current-verification.md`](docs/05-operations/current-verification.md) for proof boundaries;
- [`docs/05-operations/local-pc-validation-plan.md`](docs/05-operations/local-pc-validation-plan.md) for the later acceptance pass after remediation;
- [`docs/README.md`](docs/README.md) for documentation routing.
