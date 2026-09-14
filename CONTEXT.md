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
│   └── performance-manager/
└── shared/protocol/               Neutral Paper/Fabric contracts
```

External build/edit tools such as Vanilla Minecraft, Axiom, WorldEdit/FAWE, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external specialist owners.

## Repository authority

```text
Local = active development / source authority
main  = stable / release authority
```

Do not silently fall back to `main`. Do not create side development branches unless explicitly requested.

## Repository organization

The root is reserved for repository-level entrypoints, policy, versioning, docs, and CI support.

```text
apps/       end-user applications
plugins/    Paper server plugins
mods/       Fabric client mods
shared/     neutral cross-runtime contracts only
docs/       canonical product/system/operations docs
scripts/    repository verification/build support
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
- no idle/background subsystem without a concrete runtime need.

Canonical execution discipline: `docs/04-system/development-discipline.md`.
Canonical specialist routing: `docs/04-system/skill-routing.md`.
Current proof authority: `docs/05-operations/current-verification.md`.

## Component ownership

### Launcher / Server-Manager

`apps/launcher/` is the canonical Tauri 2 + Svelte 5 + Rust desktop source. It owns workspace bootstrap, managed Java/Paper discovery, start/stop/restart, process identity/recovery, health/resource settings, Paper provisioning/update, and internal bundled runtime synchronization.

### Plugin-Manager

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
detect Modrinth profiles
user-selected profile persistence
Minecraft 1.21.4 + Fabric compatibility verification
status/install/update/repair for:
  lazybuilder-map-manager-*.jar
  lazybuilder-utility-manager-*.jar
  lazybuilder-performance-manager-*.jar
```

It must never modify unrelated files in the selected profile `mods/` directory, create Minecraft instances, or become a second general mod manager. There is no background profile watcher; checks are request-bound to the Client Setup surface and `Sync Client`.

Current Modrinth metadata is database-backed. LazyBuilder intentionally does not couple to Modrinth's private database schema. Profile folders remain under Modrinth's `profiles/` directory; compatibility is verified from profile-local runtime evidence (`logs/latest.log`) after the profile has been launched, with legacy `profile.json` accepted only as a compatibility fallback.

Runtime-ready Launcher packages include the three tested Fabric JARs from the same source revision as the desktop package so Client Setup does not fetch arbitrary client builds at runtime.

### World-Manager

`plugins/world-manager/` is the Paper-side authority for managed world lifecycle, runtime coordination, files, settings, import/export/conversion, transfer safety, and server authorization.

Durable lifecycle:

```text
ACTIVE
ARCHIVED
```

Manual Load/Unload and per-world `autoLoad` are not product features. Runtime loading is automatic; empty active worlds may idle-unload when safe. User-facing terminology is `Duplicate`, never `Clone`.

### Utilities-Manager (Paper)

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

`mods/performance-manager/` owns lightweight performance/resource observation and background-FPS policy. External renderer/shader/culling engines remain external; when Dynamic FPS is present, LazyBuilder yields background-FPS ownership.

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
→ latest Verify workflow for that exact HEAD
→ component-specific build/test evidence
→ LOCAL_CODE proof where CI cannot prove behavior
→ LIVE_SERVER proof for actual Paper/Minecraft behavior
```

`REMOTE_GITHUB` success proves source/static/build/package claims only. It does not prove installed Windows behavior, real Paper lifecycle, Fabric interaction inside Minecraft, actual Modrinth profile discovery/sync on the target PC, large-file transfer behavior, gameplay behavior, or restart/shutdown persistence.

The phase after repository synchronization is local/runtime proof, not architecture expansion. Fix reproducible defects at the smallest owning boundary.