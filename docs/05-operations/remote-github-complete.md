# REMOTE_GITHUB Completion Record

This document is the handoff record from remote source/CI development to local and live validation.

## Completion point

Canonical development branch:

```text
Local
```

Final REMOTE_GITHUB source head:

```text
fcae5870192252bcecc63c5f0c458bed446a64a8
ci: enforce locked desktop dependencies
```

Final verification gate:

```text
Verify #509
Paper modules/tests   SUCCESS
Fabric client build   SUCCESS
Tauri desktop         SUCCESS
Overall               SUCCESS
```

The final desktop gate uses committed dependency locks and deterministic install/check behavior:

```text
EngineData/Frontend/RustApp/package-lock.json
EngineData/Frontend/RustApp/src-tauri/Cargo.lock

npm ci
cargo check --locked
Cargo cache key -> Cargo.lock
```

CI no longer generates or commits dependency locks. Workflow permissions are read-only again.

## Proof boundary

REMOTE_GITHUB is complete.

It proves only:

- repository/source consistency;
- Paper module compilation and automated tests;
- Fabric client compilation;
- Svelte type checking and frontend production build;
- Rust/Tauri compilation with committed Cargo lock state;
- protocol/source wiring that is covered by static/automated checks;
- documented ownership and runtime-layout contracts.

It does **not** prove:

- installed Windows desktop behavior;
- real Java discovery on the target PC;
- Paper process lifecycle on the target server;
- real plugin enable/disable behavior;
- Fabric screen layout/input at runtime;
- native file dialogs;
- Xaero mixin/runtime integration;
- network transfer under real latency/file sizes;
- converter output quality;
- real filesystem permissions;
- gameplay semantics of Utilities features;
- restart/shutdown persistence under a running server.

Those belong to `LOCAL_CODE` and `LIVE_SERVER` validation.

## Final component state

```text
Server-Manager
  source architecture        stable
  runtime path bootstrap     implemented
  Java 21 validation         implemented
  Paper start/stop/restart   implemented
  live proof                 pending

Plugin-Manager
  source architecture        stable
  canonical storage          implemented
  legacy compatibility       bounded/fail-safe
  install/update/disable     implemented
  live proof                 pending

World-Manager
  source architecture        LOCKED
  lifecycle/files authority  single canonical owner
  desktop/Fabric adapters    transport only
  canonical world storage    implemented
  heavy operation sharing    implemented
  live proof                 pending

Utilities-Manager
  source architecture        LOCKED
  World Safety               implemented
  Movement                   implemented
  Build Helpers              implemented
  unused Creation Tools      removed from scope
  duplicate Spectator family removed from scope
  live proof                 pending

Fabric client
  World Manager UI           source-implemented
  world/map/transfer split   locked by responsibility
  Xaero integration          source-implemented
  live proof                 pending

Desktop
  Tauri 2 + Svelte 5 + Rust  canonical
  WPF/.NET transition source removed
  dependency locks           committed
  live proof                 pending
```

## Canonical runtime workspace

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
└── tools/
    └── lazybuilder/
        ├── config/
        ├── cache/
        ├── logs/
        ├── disabled-plugins/
        └── plugin-backups/
```

Ownership:

```text
server/
  Paper runtime + enabled Paper plugins

world-system/worlds/
  actual Paper world folders for canonical desktop-launched runtime

world-system/
  World-Manager registry/import/export/backup/request work

tools/lazybuilder/config/
  Server-Manager, world-control, Plugin-Manager configuration

tools/lazybuilder/cache/converter/
  converter support/runtime cache

tools/lazybuilder/disabled-plugins/
  disabled plugin JARs

tools/lazybuilder/plugin-backups/
  Plugin-Manager update/duplicate-resolution backups
```

`Archive/Restore` is lifecycle state, not a second physical archive store. No unused `world-system/archives/` directory is part of the canonical layout.

## Compatibility rules

World-Manager:

- canonical desktop launch supplies `LAZYBUILDER_WORKSPACE_ROOT`;
- Paper must use `<workspace>/world-system/worlds` as the world container;
- a launcher/work-container mismatch fails closed;
- manual/non-LazyBuilder launches retain the historical plugin-data layout rather than silently moving existing worlds.

Plugin-Manager:

- legacy `server/plugins-disabled/` is compatibility input only;
- legacy `tools/lazybuilder/plugin-registry.json` is compatibility input only;
- non-conflicting legacy disabled JARs may be moved to the canonical disabled directory;
- legacy category state is copied only when canonical state does not already exist;
- canonical files are never silently overwritten;
- conflicting files remain visible to duplicate/problem detection.

## World-Manager locked architecture

One bootstrap:

```text
WorldManagerPlugin
```

One canonical application/service owner for world behavior. Desktop and Fabric do not own world lifecycle/filesystem rules.

Heavy operations use shared orchestration for common phases. Paper main-thread dispatch is centralized. Desktop heavy tasks use a bounded task runner. Fabric and desktop adapters must remain transport boundaries.

Supported world-management source flows include:

```text
Create Flat/Void
List
Load / Unload
Teleport
Settings
Archive / Restore
Clone
Backup
Delete
Import
Whole-world Export
Export Area
```

File transfer, map actions, and general world control stay separated:

```text
lazybuilder:world     general world management/control
lazybuilder:map       spatial Xaero/map intents
lazybuilder:transfer  file bytes only
```

## Utilities-Manager locked scope

```text
World Safety
├── explosion block protection
├── leaves decay protection
├── farmland trample protection
└── dragon egg teleport protection

Movement
├── Advanced Fly
├── Noclip
└── Night Vision

Build Helpers
├── Iron Door Toggle
├── Double Slab Break
└── Glazed Terracotta Rotate
```

Explicitly out of scope unless a later concrete requirement reopens them:

```text
Banner Creator
Armor Color Creator
Special Builder Items
separate custom Spectator feature family
```

Noclip uses the stable Paper-compatible spectator transition contract rather than NMS block noclip. Glazed Terracotta uses explicit cardinal rotation:

```text
NORTH -> EAST -> SOUTH -> WEST -> NORTH
```

## Major remote hardening completed

```text
legacy World-Manager bootstrap ownership       removed
unbounded World task queue                     bounded
heavy-operation orchestration duplication      consolidated
Paper main-thread dispatch duplication         consolidated
Fabric shutdown handling                       hardened
transfer session/file I/O path                 bounded and hardened
World-Manager storage split                    consolidated
Plugin-Manager storage split                   consolidated
unused physical archive directory              removed
Utilities lifecycle cleanup isolation          hardened
unused Creation Tools scope                    removed
separate Spectator duplication                 removed
unsupported glazed rotation helper             replaced + tested
stale architecture/product documentation       aligned
frontend dependency lock                       committed
Rust dependency lock                           committed
CI dependency resolution                       locked/reproducible
```

## Local validation entry point

Do not perform more speculative remote refactors before local validation.

Start local work from:

```text
git checkout Local
git pull
# expected baseline includes fcae5870192252bcecc63c5f0c458bed446a64a8
```

The local phase should first prove that the exact current source builds and creates the expected artifacts before changing behavior.

Recommended order:

```text
LOCAL_CODE
1. verify Java 21 / Node / Rust prerequisites
2. clean checkout/update Local
3. build Maven Paper modules
4. build Fabric client
5. npm ci + Tauri/Rust locked build
6. inspect produced JARs/desktop artifact
7. prepare canonical Work Server - 1.21.4 workspace

LIVE_SERVER
8. start Paper through LazyBuilder desktop
9. verify canonical runtime folders
10. verify World-Manager enable/control bridge
11. verify Fabric World Manager lifecycle UI
12. verify Import/Export + native dialogs + transfer integrity
13. verify Xaero Teleport Here / Export Area
14. verify Utilities behavior/state restoration
15. verify Plugin-Manager canonical + legacy compatibility behavior
16. verify restart/shutdown persistence and cleanup
```

## Runtime validation checklist

High-value checks:

```text
Desktop
- app starts cleanly
- Java 21 is found/validated
- start/stop/restart Paper works
- crash state is reported correctly
- CPU/RAM values are plausible

Filesystem
- world-system/worlds is the Paper universe
- config/cache/log/disabled-plugin/backups folders land in canonical locations
- no duplicate active runtime authorities are created
- legacy compatibility paths do not overwrite canonical state

World-Manager
- plugin enables without exception
- create Flat and Void
- list/refresh
- load/unload
- teleport
- settings mutations + returned snapshot
- archive/restore
- clone
- backup
- protected delete rejection
- valid delete
- import native Java world
- export native Java world
- conversion path where applicable
- transfer checksum/integrity

Fabric/Xaero
- L opens World Manager
- screens render/input correctly
- native picker/save dialog works
- Xaero mixin loads when present
- Teleport Here resolves safe Y server-side
- Export Area uses normal export/transfer path

Utilities
- World Safety protections behave narrowly
- /fly restores prior flight state
- /noclip restores prior game mode
- Night Vision restores/relinquishes owned state correctly
- player logout/plugin disable leaves no stale state
- Iron Door Toggle behaves as intended
- Double Slab Break removes only one slab layer
- Glazed Terracotta rotates clockwise

Shutdown/restart
- transfer/task state cleans up
- world registry persists
- plugin/player state does not remain stale
- restart returns to expected managed-world state
```

## Defect policy during local/live phase

Only reproducible runtime defects should reopen source work.

For each defect record:

```text
expected
actual
exact reproduction steps
relevant log/error
owner (Server / Plugin / World / Utilities / Fabric / Desktop)
smallest failing boundary
```

Fix defects directly on `Local`. Do not create new development branches unless explicitly requested. Do not reopen locked architecture just to work around a runtime bug; first fix the smallest wrong owner.

## Stop rule

REMOTE_GITHUB is considered complete at the final green locked-dependency gate. Future remote work should be driven by evidence from `LOCAL_CODE` or `LIVE_SERVER`, not by speculative cleanup.
