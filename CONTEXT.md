# LazyBuilder — Stable Context

## Product

LazyBuilder is the umbrella product for a Minecraft Java 1.21.4 builder-server workspace. Its purpose is to replace difficult, legacy, or overlapping server workflows with a smaller, clearer, maintainable system.

Canonical components:

```text
LazyBuilder
├── Server-Manager
├── Plugin-Manager
├── World-Manager
└── Utilities-Manager
```

External build tools such as Axiom, FastAsyncWorldEdit, FastAsyncVoxelSniper, ezEdits, and MetaBrushes remain external and are not rebuilt unless a separate explicit requirement appears.

## Repository Authority

```text
Local = active development / source authority
main  = stable / release authority
```

Do not create side development branches unless the user explicitly requests isolation.

## Engineering Model

- one semantic owner per responsibility;
- one primary execution path per behavior;
- no duplicate managers, registries, schedulers, config systems, process markers, or filesystem authorities;
- modules remain independently maintainable;
- internal maintenance stays internal unless the user has a real product decision;
- prefer deletion/consolidation before introducing a new abstraction;
- source/CI proof is distinct from local/live runtime proof;
- no NMS unless a proven requirement cannot be met through stable Paper/Bukkit APIs;
- no idle/background subsystem without a concrete need;
- use the cheapest proof capable of falsifying the changed claim.

Canonical development discipline:

```text
docs/04-system/development-discipline.md
```

Canonical specialist routing:

```text
docs/04-system/skill-routing.md
```

Current specialist set is intentionally limited to five:

```text
lazybuilder-desktop-runtime
lazybuilder-plugin-management
lazybuilder-world-management
lazybuilder-ui
lazybuilder-protocol
```

Do not add another Skill unless a repeated responsibility has a materially different execution procedure that cannot be routed cleanly to these owners.

## Remote Proof History

An earlier complete remote source/CI gate exists:

```text
head: fcae5870192252bcecc63c5f0c458bed446a64a8
run:  Verify #509

Paper modules/tests  SUCCESS
Fabric client build  SUCCESS
Tauri desktop        SUCCESS
Overall              SUCCESS
```

Later source simplification continued on `Local`; therefore that older gate is historical evidence, not proof of the current post-simplification head.

The current continuation/proof owner is:

```text
docs/05-operations/README.md
```

## Server Workspace Target

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
├── tools/
│   └── lazybuilder/
│       ├── config/
│       ├── cache/
│       ├── logs/
│       ├── disabled-plugins/
│       └── plugin-backups/
└── README-Server.txt
```

Canonical desktop-launched runtime ownership:

- actual Paper world folders → `world-system/worlds/`;
- World-Manager registry/import/export/backup/work data → `world-system/`;
- converter support assets → `tools/lazybuilder/cache/converter/`;
- Server-Manager and local-control configuration → `tools/lazybuilder/config/`;
- disabled third-party plugin JARs → `tools/lazybuilder/disabled-plugins/`;
- minimum plugin rollback snapshots → `tools/lazybuilder/plugin-backups/`;
- Paper/runtime/plugin files → `server/`.

Archive is a World-Manager lifecycle state, not a second physical world store, so no unused `world-system/archives/` directory is created.

## Component Ownership

### Server-Manager

Desktop-native authority for:

- workspace lifecycle/runtime bootstrap;
- start/stop/restart Paper;
- one Paper process owner and one detached-recovery path;
- one persisted process marker using PID + actual process start time;
- health and CPU/RAM summary;
- managed Java/runtime discovery;
- one typed `server-manager.json` config authority;
- Paper provisioning/manual Paper update;
- internal bundled World/Utilities core compatibility sync;
- resource/runtime settings;
- launching Paper against `world-system/worlds` and passing the workspace root to Paper.

It is not a Paper plugin.

Runtime policy after simplification:

```text
Paper update          -> explicit user decision
bundled core sync     -> internal automatic maintenance before start/restart
CPU scheduling        -> JVM/OS managed
resource tuning       -> Performance / Boost / Custom RAM
process identity      -> one server-process.json marker
server configuration  -> one server_config owner
```

There is no separate `process_identity` sidecar/module and no manual CPU-allocation product flow.

### Plugin-Manager

Desktop-native authority for third-party Paper plugin lifecycle:

- plugin discovery/inventory;
- derived category presentation;
- install/update;
- duplicate detection/resolution;
- dependency/compatibility checks;
- restart-safe enable/disable;
- safe JAR removal with plugin data always preserved;
- minimum rollback state required for plugin mutation.

It is not a Paper plugin.

Plugin Manager invariants after simplification:

```text
filesystem + plugin metadata = primary truth
category                     = derived, not persisted authority
remove-data/quarantine       = not a supported product path
plugin data on remove        = preserved
historical backup retention  = removed
persistent rollback          = one previous-valid JAR snapshot when needed
hot reload                    = unsupported; restart remains explicit
```

Do not recreate `plugin-registry.json` category ownership or timestamped backup history without a proven product requirement.

### World-Manager

Paper-side authority for world lifecycle and files. It owns:

- browse/select worlds;
- create Flat/Void;
- BUILD_READY application;
- teleport;
- load/unload;
- clone;
- backup;
- archive/restore;
- safe delete;
- import/export/conversion;
- settings and managed metadata;
- bounded long-running world tasks;
- transfer/import/export filesystem safety.

Desktop and Fabric only present/control these services through explicit transport contracts. They do not duplicate World-Manager business logic or filesystem ownership.

World-Manager was re-audited under the minimum-flow discipline. No architecture reduction is currently justified. Keep its bounded task runner, conversion lease, registry, Paper adapter, file/transfer owners, and conversion adapter because they each have distinct runtime responsibilities.

World-Manager source architecture remains structurally locked unless local/live evidence demonstrates a real boundary defect.

### Utilities-Manager

Paper-side builder convenience module. Current locked scope:

```text
World Safety
- explosion block protection
- leaves decay protection
- farmland trample protection
- dragon egg teleport protection

Movement
- Advanced Fly
- Noclip
- Night Vision

Build Helpers
- Iron Door Toggle
- Double Slab Break
- Glazed Terracotta Rotate
```

Banner Creator, Armor Color Creator, Special Builder Items, and a custom Spectator helper family are intentionally excluded. Movement/Noclip already owns LazyBuilder-specific spectator movement transition; normal Minecraft/Paper spectator controls own camera targeting.

Utilities-Manager source architecture remains structurally locked unless local/live evidence proves a defect.

## Client / Desktop Boundaries

Desktop canonical source:

```text
EngineData/Frontend/RustApp/
```

Stack:

- Tauri 2
- Svelte 5
- TypeScript
- Rust native backend

Desktop frontend uses one grouped typed runtime bridge:

```text
runtimeApi
├── workspace
├── server
├── plugins
└── worlds
```

A compatibility alias may remain temporarily for old imports, but there is no second mapping/business layer.

Fabric client canonical source:

```text
client/fabric/
```

World control channels remain separated by responsibility:

```text
lazybuilder:world     general world control/state
lazybuilder:map       spatial map intents
lazybuilder:transfer  file bytes
```

The first-party Fabric World Manager surface is source-implemented for list/refresh, Create, Teleport, Load/Unload, Archive/Restore, Clone, Settings, permanent Delete, Import publication, native Java 1.21.4 whole-world Export, and the native LazyBuilder Map Preview entry point. Xaero is not a runtime dependency. Map Preview remains incomplete relative to the target Xaero-like experience and requires live validation.

## Local Windows Runtime Evidence

The current LIVE_SERVER validation machine uses these paths:

```text
Repository: D:\Work\AI Stuff\LazyBuilder
Modrinth App: C:\Users\Administrator\AppData\Roaming\ModrinthApp
Minecraft profile: C:\Users\Administrator\AppData\Roaming\ModrinthApp\profiles\1.21.4 Testing
Installed client mod: <profile>\mods\lazybuilder-client-0.1.0-SNAPSHOT.jar
Paper workspace: D:\Work\Minecraft\Java-Version\Java Build Server\Test\Test
Paper endpoint: 127.0.0.1:25565
Managed Java: C:\Users\Administrator\AppData\Local\LazyBuilder\runtimes\java-21\bin\java.exe
TEMP/TMP: C:\Temp\LazyBuilderGradleTemp
```

The renamed Modrinth profile retains a compatibility junction at `1.21.4 Build (1)` pointing to `1.21.4 Testing`. The local player used for live checks is `Berchman` (`7fee50f6-17ad-4ada-95e0-4595e943cc54`), configured as Paper OP level 4.

Current reproducible local/live issues for the next repair pass:

- native Map Preview is not yet visually equivalent to the Xaero-style target;
- managed-world onboarding/current-world synchronization is incomplete;
- Map Preview actions must remain disabled until a managed current world is resolved;
- existing/default Paper worlds are not automatically adopted into the World-Manager registry;
- Utilities-Manager runtime output still exposes `${project.version}` in plugin metadata;
- the Modrinth profile rename still depends on a local compatibility junction;
- desktop-managed Paper lifecycle and direct local Paper lifecycle still need one end-to-end proof path.

## BUILD_READY Defaults

New builder worlds target:

- structures disabled;
- natural mob spawning disabled;
- Creative default game mode;
- Normal difficulty unless explicitly changed;
- clear weather with weather cycle disabled;
- daylight cycle disabled and daytime selected;
- fire tick disabled;
- mob griefing disabled;
- random tick speed 0;
- patrol/trader/insomnia/warden/raid events disabled where supported;
- unnecessary spawn-chunk persistence disabled when safe;
- unrelated vanilla gamerules left vanilla until changed.

Flat uses a simple vanilla-compatible flat preset. Void is empty terrain with a small safe spawn platform.

## Plugin Modernization Direction

```text
KEEP / EXTERNAL BUILD TOOLS
- Axiom
- FastAsyncWorldEdit
- FastAsyncVoxelSniper
- ezEdits
- MetaBrushes

REPLACE WITH LAZYBUILDER MODULES
- Multiverse-Core -> World-Manager
- VoidWorld -> World-Manager
- BuildersUtilities -> Utilities-Manager current locked scope

REMOVE FROM NEW BASELINE
- EssentialsX
- EssentialsXChat
- LightOptimizer
- MasterOptimizer
- ChunkManager
- PlaceholderAPI (no current required consumer)
- SimpleCloud-Placeholder
```

Performance authority is Paper 1.21.4 native configuration rather than generic optimizer plugins.

## Proof State

Remote proof covers source/static/CI evidence only. It does not prove installed Windows desktop behavior, running Paper lifecycle, Fabric runtime UI, native dialogs, Xaero mixins, network transfer, converter quality, real filesystem permissions, or gameplay behavior.

The current phase is intentionally:

```text
LOCAL_CODE
↓
LIVE_SERVER
```

Do not add more speculative remote refactors before current source is built locally.

Priority validation:

```text
1. current local build/toolchain
2. Desktop start/stop/restart + managed Java/runtime paths
3. canonical runtime filesystem creation
4. process marker + detached recovery safety
5. internal bundled core sync / manual Paper update separation
6. Plugin Manager install/update/enable-disable/remove/duplicates
7. World-Manager enable/control bridges
8. Fabric World Manager lifecycle UI
9. Import/Export + transfer + native dialogs
10. Xaero Teleport Here / Export Area
11. Utilities World Safety / Movement / Build Helpers
12. restart/shutdown persistence and cleanup
```

Only reproducible `LOCAL_CODE` / `LIVE_SERVER` defects should reopen source work. Fix them directly on `Local` at the smallest wrong owner. Do not reopen locked architecture unless runtime evidence demonstrates a real architecture-level requirement.
