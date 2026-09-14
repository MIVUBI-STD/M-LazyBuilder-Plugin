# World Management

Canonical owner for LazyBuilder managed-world lifecycle, Paper runtime coordination, settings, filesystem operations, and import/export behavior.

## Ownership

LazyBuilder World Manager is the native managed-world authority for the required builder workflow. It talks directly to Paper/Bukkit APIs; Multiverse is not part of the target runtime architecture.

Fabric and Desktop are presentation/control clients. They do not own world lifecycle, filesystem publication, conversion, registry state, or Paper mutations.

## Canonical world model

Each managed world has one stable LazyBuilder identity independent from presentation name and current Paper load state.

```text
WorldId          → internal UUID identity
folderName       → canonical filesystem identity
displayName      → builder-facing name
kind             → FLAT | VOID | IMPORTED
lifecycle        → ACTIVE | ARCHIVED
defaultGameMode  → durable world-entry preference
```

There is deliberately no durable `autoLoad` field and no durable runtime-state field.

Persistent product lifecycle is exactly:

```text
ACTIVE
ARCHIVED
```

`Loaded`, `Unloaded`, `Loading`, and `Unloading` are transient Paper runtime facts and must not become registry lifecycle metadata, client product states, or a second runtime-state registry.

Folder identity is not renamed by a normal metadata update. Any future filesystem rename must be an explicit file/lifecycle operation so registry metadata can never get ahead of disk state. Folder uniqueness remains case-insensitive.

The in-memory registry is the canonical managed-world metadata owner. Durable metadata is persisted through one YAML persistence boundary. Startup performs one bounded read plus bounded adoption of eligible existing Paper world folders; there is no registry polling or filesystem watcher.

Legacy registry input may contain removed fields such as `auto-load`; migration may tolerate and ignore those keys, but current persistence must not write them back.

## Automatic runtime behavior

Runtime ownership is Paper-authoritative:

```text
required world use
→ load automatically if needed

world empty + idle timeout + no conflicting operation
→ unload automatically
```

Normal users do not manually Load or Unload worlds.

`WorldRuntimeService` is a coordination boundary over Paper truth, not a shadow state machine. It may ask the runtime gateway whether a world is currently loaded and may load/unload it when an application use case requires that behavior.

Runtime safety rules:

- archived worlds cannot be loaded for normal use;
- fallback/default world cannot be unsafely unloaded;
- idle unload applies only to eligible managed ACTIVE worlds;
- a world with builders inside is not unloaded for a snapshot/destructive operation;
- active world-operation leases block conflicting external runtime changes;
- automatic unload is delayed by idle timeout to avoid thrashing.

## Create World

```text
Create World
├── Flat World
└── Void World
```

Creation exposes only the builder-facing name and world type. Internal folder identity is derived/sanitized automatically. Advanced settings belong to World Settings.

Both kinds use one `WorldCreationService` path:

```text
validate identity
→ create through Paper runtime adapter
→ apply BUILD_READY
→ register
→ persist registry
→ publish success
```

If registry publication fails after runtime creation, the new runtime world is rolled back rather than reporting a partially managed world.

### Flat

- vanilla `WorldType.FLAT` generation;
- vanilla-compatible flat layers;
- structures disabled through the approved policy;
- automatic BUILD_READY profile;
- no separate custom flat generator.

### Void

- one minimal all-air `ChunkGenerator`;
- noise/surface/caves/decorations/mobs/structures disabled;
- small safe spawn platform;
- automatic BUILD_READY profile.

## BUILD_READY

`BuildReadyPolicy` owns initial builder-safe defaults. It is applied during world creation or by explicit Reset Builder Defaults. It is not a background enforcement loop.

Default policy:

```text
structures             OFF
natural mob spawning   OFF
default game mode      CREATIVE
difficulty             NORMAL
PVP                     OFF
weather                 CLEAR
weather cycle           OFF
daylight cycle          OFF
time                    DAY (6000)
fire tick               OFF
mob griefing            OFF
random tick speed       0
patrol spawning         OFF
wandering trader        OFF
insomnia / phantom      OFF
warden spawning         OFF
raids                   OFF
spawn-chunk persistence OFF when safe
```

Unrelated vanilla gamerules remain vanilla until explicitly changed.

`defaultGameMode` is a durable LazyBuilder world-entry preference. Teleport to World applies the target world's preference after successful teleport.

## Teleport

Teleport is the normal user path into a managed world:

```text
Teleport request
→ require ACTIVE world
→ load world if needed
→ teleport player to canonical spawn
→ apply world-entry game mode
→ authoritative success
```

Manual Load/Unload is not a prerequisite and is not a user-facing workflow.

Map teleport uses the same world/runtime ownership and only adds spatial location resolution.

## World Settings

World Settings uses one `WorldSettingsService`. Opening a settings snapshot is an explicit request and may load an ACTIVE world if Paper-owned settings need to be read. There is no settings polling.

Ownership:

```text
LazyBuilder registry
└── Default Game Mode

Paper world state
├── Difficulty
├── PVP
├── Time
├── Weather
├── Spawn Location
├── Gamerules
└── Spawning controls
```

`Auto Load` / `Load on Server Start` is not a World Settings feature.

The client displays canonical values returned by the server after mutations; it does not assume local success.

### Gamerules

The full gamerule list is discovered from the active Paper API when needed. LazyBuilder must not maintain a second static version list.

### Spawning

Spawning presentation maps to existing Paper/vanilla state; LazyBuilder does not run a custom spawn engine.

```text
Natural Mob Spawning  → doMobSpawning
Animals               → Paper animal spawn flag
Monsters              → Paper monster spawn flag
Ambient               → SpawnCategory.AMBIENT interval
Water                 → vanilla water spawn categories
Patrol                 → doPatrolSpawning
Wandering Trader      → doTraderSpawning
Insomnia / Phantoms   → doInsomnia
Warden                 → doWardenSpawning
Raids                  → inverse disableRaids
```

### Reset Builder Defaults

Explicitly reapplies `BuildReadyPolicy` and restores the durable Default Game Mode preference. It does not create future enforcement.

## Operation model

Heavy or lifecycle work is represented as transient operations, never as world lifecycle values.

```text
DUPLICATE
BACKUP
IMPORT
EXPORT
ARCHIVE
RESTORE
DELETE
```

`WorldOperationCoordinator` owns one active operation lease per affected world. It is request-bound and does not introduce a scheduler/polling daemon.

Different worlds may operate independently where the underlying resource owner allows it. Conversion remains globally serialized by its dedicated conversion lease because the conversion runtime requires that protection.

## Filesystem foundation

`WorldFileRepository` is the single path-safe managed-world filesystem boundary for staging, publishing, deleting, and workspace cleanup.

Rules:

- managed world folders resolve only under the canonical world root;
- user input never becomes an arbitrary delete/publish path;
- symbolic-link world roots / staged links fail closed;
- partial staging is cleaned on failure;
- heavy copy/delete work runs away from the Paper main thread after the required Paper quiesce step;
- Paper mutations remain on the primary thread.

Copy profiles:

```text
SNAPSHOT
├── keep world identity/data
└── omit session.lock

DUPLICATE
├── omit session.lock
├── omit uid.dat
└── omit playerdata / advancements / stats
```

Do not recreate `CLONE` terminology or a parallel copy framework.

## Duplicate

User-facing and backend terminology is **Duplicate**.

```text
source ACTIVE world
→ validate destination identity
→ block if builders remain inside when snapshot consistency requires unload
→ acquire DUPLICATE lease
→ snapshot/copy through WorldFileRepository
→ publish independent destination
→ fresh WorldId
→ lifecycle ACTIVE
→ persist
→ restore source runtime if it was previously loaded
```

Duplicate does not inherit Pinned/Recent client preferences or runtime loaded state.

## Archive / Restore

Archive is reversible workspace cleanup, not a physical archive directory.

```text
ACTIVE
→ Archive
→ ARCHIVED

ARCHIVED
→ Restore
→ ACTIVE
```

Archive is blocked while builders are inside the world. It does not silently move them to fallback merely to complete the action.

Archived worlds are excluded from normal daily use and must be restored before Teleport, Settings requiring runtime access, Duplicate, Backup, or Export.

## Delete

Delete is permanent and uses immutable WorldId as backend identity.

Safety:

- builder confirmation uses exact visible display name;
- fallback/default world is protected;
- occupied world deletion is blocked;
- file deletion stages before registry commit where possible;
- failure before commit restores staged world data;
- cleanup retry state remains bounded to the operation.

Internal folder identity is not required from the builder merely for confirmation.

## Backup

Backup uses the same snapshot/quiesce safety model and one existing backup store. It is not exposed as a second world lifecycle.

Occupied worlds are blocked if the backup path requires consistent unload/snapshot semantics.

## Import / Export / conversion

Detailed conversion behavior is canonical in [`conversion.md`](conversion.md).

Product model:

```text
World Manager
↓
Import / Export
↓
existing Import / Export services
↓
verified on-demand conversion runtime when required
```

Chunker/converter runtime names are implementation details and must not become product navigation.

### Export

- native Java 1.21.4 keeps a direct fast path;
- additional Java/Bedrock targets appear only from the verified runtime catalog;
- whole-world and Map Export Area reuse `WorldExportService`;
- selected map rectangle is transient request context;
- extension is derived by target (`.zip` Java, `.mcworld` Bedrock);
- snapshot source is restored as soon as the consistent snapshot is secured;
- occupied-world export is blocked rather than silently ejecting builders.

### Import

- file-first `.zip` / `.mcworld` workflow;
- bounded upload/extraction validation;
- source edition/version detection;
- canonical managed target Java 1.21.4;
- fresh WorldId;
- lifecycle ACTIVE;
- no silent overwrite of an existing world.

## Current capability surface

```text
World Map
Teleport Here
Export Area
World Browser / Teleport
Create Flat / Void
Manage World
World Settings
Import / Export
Duplicate
Archive / Restore
Delete
automatic runtime loading / idle unload
```

There is no normal capability named:

```text
Load World
Unload World
Auto Load
Clone
```

## Client/server boundary

Fabric presentation is canonical in `docs/03-client-ui/`.

Current shared contracts:

```text
World Control V3
Map Action V2
Transfer bounded protocol
```

World Control V3 intentionally excludes manual runtime-state product actions and carries `canManage` / `canTeleport` only for presentation shaping; Paper still performs final authorization.

Map Action V2 can push authoritative current-world changes from actual player world transitions, including an explicit clear when the player enters an unmanaged world.

## Safety rules

- Server plugin is authoritative.
- Paper world lifecycle/settings calls execute on the primary server thread.
- Create never adopts an arbitrary existing folder as a new world.
- Registry persistence is fail-closed for malformed ownership metadata.
- Runtime load state is derived from Paper, never persisted as lifecycle.
- File operations resolve canonical owned roots only.
- Conflicting world operations are rejected by one operation coordinator.
- Occupied worlds are not silently evacuated for heavy/destructive file operations.
- Fallback/default world remains protected.
- Import/export transfer validates bounds/checksums and cleans partial state.
- Runtime claims require LIVE_SERVER proof.

## Proof boundary

Current `Local` source is ahead of fresh proof. Historical CI does not prove the current head.

Final validation must cover:

```text
compile/unit tests
Paper + Fabric World V3 / Map V2 interoperability
existing-world adoption
Teleport auto-load
idle auto-unload
occupied-world guards
World Settings
Duplicate
Archive / Restore / Delete
whole-world Export
Map Export Area
Import .zip / .mcworld
conversion targets
large transfer / disk-space failure / checksum
permissions
reconnect / shutdown cleanup
```

Do not claim these runtime behaviors validated until the final local/live phase has run.
