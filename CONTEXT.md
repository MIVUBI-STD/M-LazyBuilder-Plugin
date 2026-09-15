# LazyBuilder — Stable Context

## Product

LazyBuilder is a modular Minecraft Java 1.21.4 builder-server workspace. It is intentionally split by semantic ownership rather than one master runtime component.

```text
LazyBuilder
├── apps/launcher/                 Desktop application
├── plugins/                       Paper server plugins
│   ├── world-manager/
│   └── utilities-manager/
├── mods/                          Fabric client mods
│   ├── map-manager/
│   ├── utility-manager/
│   └── performance-manager/       deferred research source
└── shared/protocol/               Neutral Paper/Fabric contracts
```

External build/edit tools such as Vanilla Minecraft, Axiom, WorldEdit/FAWE, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external specialist owners.

## Repository authority

```text
Local = active development / source authority
main  = stable / release authority
```

Do not silently fall back to `main`. Do not create side development branches unless explicitly requested.

## Current continuation phase

The repository has completed the current remote hardening cycle and the next canonical phase is **Local PC validation**.

Canonical handoff:

`docs/05-operations/local-pc-validation-plan.md`

The next work is to exercise the existing product from the outside in:

```text
installer
→ Launcher
→ managed Java / Paper
→ Plugin Manager
→ World Manager
→ Utilities Manager
→ Client Setup / Modrinth
→ Map Manager
→ Utility Manager
→ full Paper/Fabric interoperability
→ restart/recovery/update
→ large-world/storage/conversion validation
→ final repository audit
```

During this phase, do not expand architecture merely because target-machine proof is incomplete. Fix reproducible defects at the smallest owning boundary.

## Repository organization

The root is reserved for repository-level entrypoints, policy, versioning, docs, and CI support.

```text
apps/       end-user applications
plugins/    Paper server plugins
mods/       Fabric client mods
shared/     neutral cross-runtime contracts only
docs/       canonical product/system/operations docs
scripts/    repository verification/build support
tooling/    repository-owned build/bootstrap/distribution tooling
```

Do not reintroduce generic `EngineData`, `modules`, or `client` source buckets. New source belongs to the semantic runtime owner above.

## Engineering model

- one semantic owner per responsibility;
- one primary execution path per behavior;
- one persisted fact has one authority;
- no duplicate managers, registries, schedulers, config systems, process markers, transfer systems, or filesystem authorities;
- prefer deletion/consolidation before introducing a new abstraction;
- source/CI proof is distinct from local/live runtime proof;
- no NMS unless a proven requirement cannot be met through stable Paper/Bukkit APIs;
- no idle/background subsystem without a concrete runtime need;
- deferred source stays isolated until explicit promotion based on measured evidence.

Canonical execution discipline: `docs/04-system/development-discipline.md`.
Canonical specialist routing: `docs/04-system/skill-routing.md`.
Current proof authority: `docs/05-operations/current-verification.md`.
Current Local PC handoff: `docs/05-operations/local-pc-validation-plan.md`.

## Component ownership

### Launcher / Server Manager

`apps/launcher/` is the canonical Tauri 2 + Svelte 5 + Rust desktop source. It owns workspace bootstrap, managed Java/Paper discovery, start/stop/restart, process identity/recovery, health/resource settings, Paper provisioning/update, and internal bundled runtime synchronization.

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

LazyBuilder Client Setup target ownership is limited to:

```text
detect Modrinth profiles
user-selected profile persistence
Minecraft 1.21.4 + Fabric compatibility verification
status/install/update/repair for LazyBuilder-owned required client components
preserve unrelated files in the selected profile mods/ directory
```

The intended V1 required Fabric set is:

```text
lazybuilder-map-manager-*.jar
lazybuilder-utility-manager-*.jar
```

`mods/performance-manager/` is deferred research source. Current `Local` Launcher/CI source may still reference or bundle its JAR as transitional packaging. That state must not be used as justification to add new Performance Manager dependencies or expand its product scope during Local PC validation.

Client Setup must never modify unrelated files in the selected profile `mods/` directory, create Minecraft instances, or become a second general mod manager. There is no background profile watcher; checks are request-bound to the Client Setup surface and `Sync Client`.

Runtime-ready Launcher packages must use tested same-revision LazyBuilder client artifacts rather than fetching arbitrary LazyBuilder client builds at runtime.

### World Manager

`plugins/world-manager/` is the Paper-side authority for managed world lifecycle, runtime coordination, files, settings, import/export/conversion, transfer safety, and server authorization.

Durable lifecycle:

```text
ACTIVE
ARCHIVED
```

Manual Load/Unload and per-world `autoLoad` are not product features. Runtime loading is automatic; empty active worlds may idle-unload when safe. User-facing terminology is `Duplicate`, never `Clone`.

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

### Performance Manager (Fabric)

`mods/performance-manager/` is an isolated deferred performance-research module. Its existing frame observation/background-FPS experiments do not make it required V1 runtime infrastructure.

No Paper plugin, shared protocol, Map Manager, Utility Manager, or unrelated desktop subsystem may gain a dependency on Performance Manager while it is deferred. Promotion requires a concrete client bottleneck, a measurable success criterion, representative Minecraft-client proof, and an explicit product decision.

## Shared protocol

`shared/protocol/` is the only neutral Paper/Fabric contract source.

```text
lazybuilder:world     World Control V5
lazybuilder:map       Map Action V2
lazybuilder:transfer  bounded file bytes only
```

Desktop ↔ Paper local control is a separate authenticated loopback contract currently at protocol version 2. Client Setup is local desktop/filesystem integration and does not add another Minecraft network protocol.

## Runtime workspace target

```text
Work Server - 1.21.4/
├── LazyBuilder.exe
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

## Validation authority

Do not hard-code a permanent current SHA or workflow run in stable context. Determine readiness from:

```text
current Local HEAD
→ latest relevant Verify workflow for that exact HEAD
→ component-specific build/test evidence
→ dedicated Paper Runtime Proof where applicable
→ target-machine / Minecraft-client proof where CI cannot faithfully reproduce behavior
```

`REMOTE_GITHUB` can prove more than static compilation when a workflow actually boots the relevant runtime. In particular, the dedicated Paper Runtime Proof is authoritative for the Paper lifecycle cases it explicitly executes, including real Paper boot/restart and tested managed-world persistence.

Remote CI still does **not** prove arbitrary target-PC Installer/Launcher behavior, real Modrinth profile discovery/sync on the user's machine, Fabric UI/input inside a real Minecraft client, representative Plugin Manager behavior on a long-lived server, real-player gameplay interaction, representative production-scale large-world throughput, full-PC reboot recovery, or representative cross-edition conversion quality.

Those are now explicit Local PC acceptance tasks in `docs/05-operations/local-pc-validation-plan.md`.
