# LazyBuilder — Stable Context

## Product

LazyBuilder is a modular Minecraft Java 1.21.4 builder-server workspace. It is split by semantic ownership rather than one master runtime component.

```text
LazyBuilder
├── apps/launcher/                 Desktop application
├── plugins/                       Paper server plugins
│   ├── world-manager/
│   └── utilities-manager/
├── mods/                          Fabric client mods
│   ├── map-manager/               core Client Setup
│   ├── utility-manager/           core Client Setup
│   ├── performance-manager/       core Client Setup
│   ├── builder-utilities/         separate Axiom-first extension
│   └── terraform-manager/         legacy/prototype lane
└── shared/
    ├── protocol/                   neutral Paper/Fabric contracts
    └── terraform-core/             legacy/prototype terrain kernel
```

Axiom remains the primary builder editor/interaction owner. Builder Utilities is the LazyBuilder-owned extension lane for proven missing builder capabilities; it is not a fourth core Manager. WorldEdit/FAWE, FastAsyncVoxelSniper, ezEdits and MetaBrushes remain external specialist/reference tools. Terraform remains a legacy/prototype lane pending retirement or a proven distinct responsibility.

## Repository authority

```text
Local = active development / remediation / source authority
main  = stable / release authority
```

Do not silently fall back to `main`. Do not create side development branches unless explicitly requested.

## Current continuation phase

Installed Local PC testing on 15 September 2026 exposed reproducible cross-boundary defects. The current canonical phase is therefore **source remediation and synchronization**.

Current handoff:

```text
docs/05-operations/local-pc-remediation-2026-09-15.md
```

Current order:

```text
Local PC findings
→ source remediation
→ runtime/build/product synchronization
→ regression coverage
→ local finalization when applicable
→ integrated exact-head Verify
→ canonical installer/provenance proof
→ final source/repository audit
→ explicit decision to reopen Local PC acceptance
```

Focused Launcher/Paper/visual workflows may provide additional evidence when requested, but they are not parallel readiness authorities.

Do not move directly to target-machine acceptance while source-side P0/P1 remediation or synchronization contradictions remain.

The later acceptance procedure is:

```text
docs/05-operations/local-pc-validation-plan.md
```

## Repository organization

The root is reserved for repository-level entrypoints, policy, versioning, docs, and CI support.

```text
DEV.cmd    canonical developer entry shim
apps/       end-user applications
plugins/    Paper server plugins
mods/       Fabric client mods
shared/     neutral cross-runtime contracts only
docs/       canonical product/system/operations docs
scripts/    repository verification/runtime proof utilities
tooling/    repository-owned build/bootstrap/distribution control plane
```

Do not reintroduce generic `EngineData`, `modules`, or `client` source buckets. New source belongs to the semantic runtime owner above.

## Engineering model

- one semantic owner per responsibility;
- one primary execution path per behavior;
- one persisted fact has one authority;
- one developer command surface (`DEV.cmd` → `tooling/windows-toolchain/dev.ps1`);
- one runtime-ready Local package publisher (`package-local.ps1` → `dist/Local/`);
- no duplicate managers, registries, schedulers, config systems, process markers, transfer systems, filesystem authorities, build publishers, or verification authorities;
- prefer deletion/consolidation before introducing a new abstraction;
- source/CI proof is distinct from local/live runtime proof;
- no NMS unless a proven requirement cannot be met through stable Paper/Bukkit APIs;
- no idle/background subsystem without a concrete runtime need;
- required components must match source, packaging, Client Setup, CI and documentation.

Canonical execution discipline: `docs/04-system/development-discipline.md`.
Canonical specialist routing: `docs/04-system/skill-routing.md`.
Canonical developer/deployment operations: `docs/04-system/development-operations.md`.
Current remediation authority: `docs/05-operations/local-pc-remediation-2026-09-15.md`.
Current proof authority: `docs/05-operations/current-verification.md`.
Later Local PC handoff: `docs/05-operations/local-pc-validation-plan.md`.

## Component ownership

### Launcher / Server Manager

`apps/launcher/` is the canonical Tauri 2 + Svelte 5 + Rust desktop source. It owns workspace bootstrap, managed Java/Paper discovery, start/stop/restart, process identity/recovery, health/resource settings, Paper provisioning/update, and internal bundled runtime synchronization.

Runtime/process invariants include:

```text
PID + process-start-time identity
one managed Paper process
safe detached-process validation before termination
safe Stopping/Detached stop-restart recovery
one server-start coordination lock
LazyBuilder-owned process-local TEMP/TMP policy
```

Canonical runtime temp root:

```text
%LOCALAPPDATA%\LazyBuilder\temp
```

The installer must not mutate the user's global `TEMP`, `TMP`, `PATH`, `JAVA_HOME`, Maven, Gradle, Node or Rust environment.

### Plugin Manager

Also inside `apps/launcher/`. It owns third-party Paper plugin inventory, metadata, install/update, dependency/compatibility checks, duplicate resolution, restart-safe enable/disable, safe JAR removal with plugin data preserved, and minimum rollback state.

### Client Setup / Modrinth integration

Also inside `apps/launcher/`, under the desktop runtime `client_integration` owner.

Modrinth App remains authoritative for:

```text
Minecraft installation/profile
Fabric loader installation
general mods and modpacks
launching Minecraft
```

LazyBuilder Client Setup owns only:

```text
detect/select Modrinth profile
persist the selected profile
verify Minecraft 1.21.4 + Fabric compatibility
status/install/update/repair LazyBuilder-owned client components
preserve all unrelated files in the selected profile mods directory
```

Builder Utilities is intentionally separate from Client Setup and the required three-manager V1 bundle. It requires Axiom and has its own verification/provisioning decision boundary.

Canonical V1 required Fabric set:

```text
lazybuilder-map-manager-*.jar
lazybuilder-utility-manager-*.jar
lazybuilder-performance-manager-*.jar
```

The three managers are one tested/bundled/synchronized client suite. This definition must stay identical across architecture docs, artifact verification, Gradle/CI build, Launcher resources, and Client Setup transaction logic.

Client Setup must never become a second Minecraft launcher or general mod manager. Runtime-ready packages use tested same-revision LazyBuilder client artifacts rather than fetching arbitrary LazyBuilder builds at runtime.

### World Manager

`plugins/world-manager/` is the Paper-side authority for managed world lifecycle, runtime coordination, files, settings, import/export/conversion, transfer safety, and server authorization.

Durable lifecycle:

```text
ACTIVE
ARCHIVED
```

Manual Load/Unload and per-world `autoLoad` are not product features. Runtime loading is automatic; empty active worlds may idle-unload when safe. User-facing terminology is `Duplicate`, never `Clone`.

World Manager is the single owner of cross-edition conversion runtime. Default runtime policy is `AUTOMATIC_STABLE`, while activation remains fail-closed:

```text
stable release metadata
→ exact CLI artifact
→ SHA-256 required
→ download
→ compatibility probe
→ staged candidate
→ atomic promotion
→ rollback/discard on failure
```

### Utilities Manager (Paper)

`plugins/utilities-manager/` owns small server-side builder conveniences only:

```text
World Safety
Movement
Build Helpers
```

### Map Manager (Fabric)

`mods/map-manager/` owns first-party world/map UI, navigation, current managed-world presentation, lifecycle/settings presentation, Import/Export/transfer UI, Map Export Area, and the shared World-Manager protocol client.

### Utility Manager (Fabric)

`mods/utility-manager/` owns passive non-building client convenience such as chat/session convenience, reconnect/disconnect presentation, borderless-window presentation, reload notification, screenshot naming, and local preferences.

Reconnect target capture occurs before JOIN and is reconfirmed on JOIN. Reconnect presentation must cover both observed vanilla paths:

```text
DisconnectedScreen
MultiplayerScreen/server-list fallback
```

### Performance Manager (Fabric)

`mods/performance-manager/` is the required first-party client performance owner. It owns performance behavior that remains independent from Map Manager and Utility Manager product semantics:

- frame-time pressure observation and bounded background/unfocused FPS policy;
- conservative entity/block-entity and render-side culling;
- chunk rebuild, upload, visibility, buffer, and terrain-submission efficiency;
- targeted memory/deduplication work;
- terrain GPU residency/reclamation and region/layer allocation;
- guarded physical-arena, per-draw transform, and multi-draw submission paths with reversible vanilla fallback;
- passive/on-demand diagnostics and opt-in runtime proof logging;
- compatibility gating when another renderer or optimization owner must remain authoritative.

It does not own Map Manager workloads or Utility Manager behavior, and it does not require a third-party optimization mod. External renderer/performance mods remain compatibility/migration references only where the corresponding first-party path is not authoritative.

Required status does not permit cross-manager implementation dependencies or duplicate performance ownership. Source/build proof establishes the implemented ownership path; representative runtime effectiveness and visual correctness remain separate performance acceptance evidence.

## Shared protocol

`shared/protocol/` is the only neutral Paper/Fabric contract source.

```text
lazybuilder:world     World Control V7
lazybuilder:map       Map Action V5
lazybuilder:transfer  bounded file bytes only
```

Desktop ↔ Paper local control is a separate authenticated loopback contract currently at protocol version 2. Client Setup is local desktop/filesystem integration and does not add another Minecraft network protocol.

## Development / build ownership

Canonical developer surface:

```text
DEV.cmd setup
DEV.cmd check
DEV.cmd build
DEV.cmd test
DEV.cmd update
DEV.cmd finalize-local
```

`DEV.cmd` is only the root Windows shim. `tooling/windows-toolchain/dev.ps1` owns routing and delegates to the exact operation owner.

Repository-owned Java build wrappers are canonical:

```text
mvnw.cmd
→ tooling/windows-toolchain/scripts/wrappers/maven.ps1

gradlew.bat
→ tooling/windows-toolchain/scripts/wrappers/gradle.ps1
```

Do not require global Maven/Gradle. The wrappers use official checksums and the LazyBuilder-owned temp policy. Gradle defaults to no-daemon unless explicitly overridden, reducing stale Loom/daemon lock risk.

`toolchain.json` is the supported toolchain-policy authority. Temporary upgrade candidates or migration notes do not belong in that manifest.

Runtime-ready Local publishing has one owner:

```text
tooling/windows-toolchain/scripts/distribution/package-local.ps1
→ dist/Local/
```

Compile-only diagnostics remain isolated under `dist/CompileOnly/` and are never acceptance candidates.

## Runtime workspace target

```text
Work Server - 1.21.4/
├── server/
│   ├── paper.jar
│   └── plugins/
├── world-system/
│   ├── worlds/
│   ├── imports/
│   ├── exports/
│   ├── backups/
│   └── work/
└── tools/lazybuilder/
    ├── config/
    ├── cache/
    ├── logs/
    ├── disabled-plugins/
    └── plugin-backups/
```

Archive is lifecycle metadata, not a second physical world store. Modrinth profiles remain external user-owned Minecraft client workspaces and are not moved into the server workspace.

## Verification authority

Do not hard-code a permanent current SHA or workflow run in stable context.

Normal final remote readiness is determined from the integrated `Verify` workflow for the exact revision under review:

```text
current Local HEAD
→ current remediation state
→ local finalization where applicable
→ integrated exact-head Verify
→ canonical installer + package/provenance evidence
→ final source/repository audit
→ explicit decision to reopen target-machine acceptance
```

`Verify` includes the relevant repository/version contracts, Paper build/tests, Paper runtime lifecycle + restart persistence, Fabric build/artifact verification, Launcher frontend/Rust proof, Windows package build, installer smoke, and exact-commit provenance.

Focused Launcher/Paper runtime/visual workflows are supplementary manual or review evidence. They do not create a second repository-readiness authority.

Remote CI proves only what it executes. Target-PC/real-client behavior is a later proof layer, not a substitute for unresolved source remediation.
