# World Management

Canonical owner for LazyBuilder managed-world lifecycle, Paper runtime coordination, settings, filesystem operations, and import/export behavior.

**WM-01 goal:** Give builders one reliable, low-friction workspace to enter, organize, protect and transfer worlds, without turning World Manager into a server administration panel, world editor, gameplay engine or replacement for Axiom/WorldEdit. Safety of existing/imported worlds takes priority over silent optimization. Daily controls show builder decisions; internal folder/runtime/lease/converter details remain with their source owners. [Detailed lifecycle rules](manage-world.md) and [conversion contract](conversion.md) specialize this goal.

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

### Optional Chunky pregeneration and idle unload

World Manager's existing 30-second idle check does not unload a managed world while
Chunky reports active pregeneration for its overworld, Nether or End world-family
name. The guard uses the optional Chunky service API `version()/isRunning(world)`
without adding a Chunky runtime dependency or a second scheduler. If Chunky is
enabled but its service API/version cannot be inspected, idle unload is
conservatively held and a warning is logged once. This avoids interrupting
external generation, but may keep empty worlds resident until the integration
is repaired. Without Chunky, normal idle unload behavior is unchanged.

The `/lazyperf` command bridge sends Chunky commands, so successful Bukkit
dispatch alone is **not** proof that Chunky accepted or finished an operation.
Especially, `/lazyperf cancel <world>` sends Chunky's pending cancel request:
a separate `/chunky confirm` from the **server console** is required to commit
that cancellation. Messages must say *requested* rather than *completed*.
Confirm active progress in Chunky itself.

The canonical work needed for release proof is a real Paper + compatible Chunky
session that covers active overworld/Nether/End jobs, pause/continue/cancel,
incompatible or unregistered service API, and idle-unload resumption after job
completion. The injected protection-predicate JUnit fixture covers the idle
timer contract only, not live plugin service discovery.

### Launcher World UI and output folders

The Desktop Worlds page keeps familiar world actions in the existing per-world
menu, displays readable result messages, and uses an activity indicator instead
of treating phase checkpoints as measured byte progress. After a successful
world backup or export it can open the active server's folder using the
existing Launcher workspace command. For Launcher-managed canonical workspaces,
world backup ZIPs are in `world-system/backups` and export artifacts are in
`world-system/exports`. Opening the server folder does not automatically open
the nested destination directory.

**World backup and full-server restore points are different products.**
The Desktop Worlds surface now exposes **Restore from backup → Restore as copy**
for canonical Launcher-managed workspaces. The backup picker restricts selection
to regular ZIP files directly under that workspace's `world-system/backups`;
it rejects redirected backup roots and unsafe selected files. The selected
backup is imported as a **new managed world** through the existing read-only
backup resolver and canonical WorldImportService. It uses bounded ZIP entry and
uncompressed-size checks, available-disk preflight, a request-pinned SHA-256
and a post-extraction SHA-256 check before staged world publication. The
original world and original backup ZIP remain unchanged. This is **not** the
Archive / Restore lifecycle action.

The current direct-restore route does **not** transmit the ZIP over the local
HTTP upload endpoint or create an additional compressed ZIP staging copy.
Consequently the default 8 GiB *upload* cap does not apply to a server-owned
backup restore. The existing archive file-count and uncompressed-size limits
(e.g. 100,000 files / 16 GiB at default configuration) **still apply**, and
disk space for extracted data and any required conversion is still necessary.
The backup source is accepted only from the selected server's canonical
`world-system/backups` directory. Launcher rechecks the selected server and
the original picker path immediately before invoking Paper so a stale picker
selection from another workspace cannot silently target a different backup.
Only its validated leaf filename is serialized to the Paper API.

The request ID and destination folder persist across ambiguous UI responses.
Paper replays committed results from world-registry import provenance with a
`backup:` source namespace rather than starting a second restore.
Do not claim that this is a transactional point-in-time snapshot of a backup
being modified by an unrelated process; the source is expected to remain
immutable and checksum verification rejects changed bytes before publication.

For Create World, a lost HTTP response is not proof of mutation failure.
Desktop re-reads the current managed-world list and warns on ambiguity instead
of automatically retrying and potentially creating another world. This
does not substitute for persistent request-ID-based create idempotency; it
is a user-safety presentation and reconciliation improvement.

Native UI, real restore round-trip and large backup acceptance on the current
`Local` revision remain `UNKNOWN / NOT EXECUTED`.

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

## Opt-in mob AI performance policy

The BUILD_READY initialization profile above is distinct from runtime mob AI changes. Paper World-Manager does **not** suppress existing mobs or breeding in managed worlds by default. The aggressive entity profile is scoped through `world-manager.build-performance.enabled-worlds` in the Paper plugin `config.yml`; its default is `[]`. An operator must explicitly list exact managed world **folder** names (not display names) before the existing `disable-all-mob-ai`, `disable-villager-ai` and `disable-breeding` switches apply. There is no wildcard/global opt-in. Config changes take effect on plugin restart.

When an eligible mob with AI enabled is suppressed, the controller writes a namespaced persistent marker of **LazyBuilder ownership** before toggling AI off. The same owned change is restored when the entity unloads, when World-Manager is disabled, or when a future load is no longer in the allowlist. Mobs that already have NoAI **without this marker** retain their authored state. `lazybuilder_gameplay_ai` is a suppression opt-out; it must not force unrelated NoAI mobs to AI-enabled. `lazybuilder_decorative` opts into suppression only inside an allowed managed world. Invalid folder lists fail closed.

Older plugin versions may have changed AI without recording ownership. Those legacy NoAI states cannot safely be auto-restored because authored NoAI decorations cannot be distinguished from past mutations. Review them individually if necessary; source-only tests do not prove Paper entity-save ordering, so unload/restart behavior remains a runtime acceptance item.

## Teleport

Teleport is the normal user path into a managed world:

```text
Teleport request
→ require ACTIVE world
→ load world if needed
→ resolve a bounded safe landing near the saved spawn
→ teleport only when the landing is verified
→ apply world-entry game mode
→ authoritative success
```

Manual Load/Unload is not a prerequisite and is not a user-facing workflow.

Map teleport uses the same world/runtime ownership and only adds spatial location resolution.
World spawn and map teleport share one Paper-side floor/headroom/hazard/water/
world-border safety predicate. World-spawn resolution first checks a bounded
3-block horizontal and vertical neighborhood. For non-Void worlds only, a
bounded nearby top-surface fallback can resolve an outdated imported spawn
height. No platform is created, no saved world spawn is rewritten, and no
unsafe teleport override is exposed in V1. If no safe landing exists the
player remains at the previous location, the world entry game mode is not
applied, and the client receives a clear error. Void worlds never get
synthetic terrain added during teleport.

The pure candidate-search policy has Java 21 standalone test coverage, but
Paper collision/heightmap behavior, real importing, teleport events from other
plugins and Fabric UI must still be validated on the exact current revision.

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
- disposable staging is cleaned on failure; uncertain destructive staging remains preserved;
- Delete moves the root to reversible staging without altering Paper dimension siblings before registry commit; failed Delete restores the root without replacing sibling data;
- committed Delete writes an immutable, durable marker after registry persistence succeeds, then removes sibling folders first and root staging last. Startup never treats registry absence alone as permission to delete a staged world; unmarked or invalidly marked Delete staging is preserved for reconciliation;
- interrupted committed cleanup keeps attributable staging for restart reconciliation;
- legacy Delete staging that contains canonical DIM-1/DIM1 data is deliberately preserved without automatic restoration, because migration can collide with existing Paper siblings; reconciliation requires verified copies and operator inspection;
- unexpected post-commit cleanup exceptions are warnings rather than evidence that the committed Delete was undone;
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

Occupied worlds are blocked if the backup path requires consistent unload/snapshot semantics. A staging deletion failure retains request-owned cleanup evidence and is retried during `finish()` whether packaging committed or failed. If cleanup still fails, the existing safe transient-workspace startup recovery remains the last-resort path; the backup outcome is never falsely promoted by a cleanup failure.

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
- selected map rectangle is transient request context and is server-bounded before export work starts;
- extension is derived by target (`.zip` Java, `.mcworld` Bedrock);
- export publication never replaces an existing completed artifact, even if another export creates it after the initial existence check. A collision returns a failure while keeping the previous artifact and cleaning this operation's staging. The current export store serializes only the final no-clobber decision between simultaneous LazyBuilder exports; ZIP packaging stays concurrent. The existing world operation lease remains the owner; no new export manager is introduced;
- snapshot source is restored as soon as the consistent snapshot is secured;
- when an ACTIVE source is loaded with builders inside, export uses the managed live-snapshot window: Paper saves player/world state, temporarily pauses autosave while the filesystem snapshot is captured, then restores the prior save policy on the primary thread;
- live export never silently evacuates builders; source runtime restoration and save-policy restoration remain part of the export task cleanup contract.

### Import

- file-first `.zip` / `.mcworld` workflow;
- bounded upload/extraction validation;
- a Desktop import carries its verified uploaded-archive SHA-256 through the existing import transaction into optional persisted world provenance. The artifact store copies and hashes the admitted archive inside the existing owned import workspace, rejects mismatches before extracting, and extracts **only from that verified snapshot**; the disk-space preflight reserves capacity for the snapshot as well as extracted content. Snapshot and partial output share existing workspace cleanup/recovery. Older records and Fabric imports retain their original import path without an invented digest. This SHA-256 identifies the uploaded archive, **not** the extracted or converted world;
- source edition/version detection;
- canonical managed target Java 1.21.4;
- fresh WorldId;
- lifecycle ACTIVE;
- no silent overwrite of an existing world;
- the source upload is reserved by the existing World Import service during inspection, discard, and prepared-to-finished import processing (across Desktop and Fabric); concurrent requests for the same archive, including case-only filename aliases, fail closed;
- a rejected destination or completed/failed import releases its archive claim; old finish callbacks cannot release a newer operation's claim. No separate import scheduler, persistent source registry, or background cleanup daemon is introduced.

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
World Control V7
Map Action V5
Transfer bounded protocol
```

World Control V7 carries the current World Manager request/result, settings/export customization, and presentation-capability contract; Paper remains the final authorization and world-runtime authority.

Map Action V5 correlates request-bound responses so stale teleport/export/errors cannot resolve another operation. Request id `0` is reserved for authoritative server-pushed current-world transitions; client requests use positive correlation ids. Current-world pushes still include an explicit clear when the player enters an unmanaged world.

## Safety rules

### Unresolved startup world transactions

Startup recovery uses persisted registry membership to finalize committed worlds.
If a Create/Import/Duplicate operation left a world folder but no matching
registry record, the world folder (including its dimensions) and recovery
marker are preserved. World Manager blocks automatic world discovery until
this ambiguity is resolved explicitly; it does **not** delete the folder based
solely on a transaction marker. A malformed root publication marker also blocks
automatic dimension-marker cleanup, so sibling worlds remain untouched.
Startup errors include a bounded, read-only inventory of the relevant evidence
paths. Copy the world, work-directory and registry data to independent storage
before investigating. Never delete markers or world directories just to make
startup proceed. Absent world folders may still have empty
transaction markers cleared. Previously requested committed Delete operations
retain their separate delete-recovery policy.

- Server plugin is authoritative.
- Paper world lifecycle/settings calls execute on the primary server thread.
- Create never adopts an arbitrary existing folder as a new world.
- Registry persistence is fail-closed for malformed ownership metadata.
- Runtime load state is derived from Paper, never persisted as lifecycle.
- File operations resolve canonical owned roots only.
- Conflicting world operations are rejected by one operation coordinator.
- Builders are not silently evacuated for heavy/destructive file operations; export uses the explicit managed live-snapshot path when the source remains occupied.
- Fallback/default world remains protected.
- Import/export transfer validates bounds/checksums and cleans partial state.
- Runtime claims require LIVE_SERVER proof.

## Proof boundary

Current `Local` source is ahead of fresh proof. Historical CI does not prove the current head.

Final validation must cover:

```text
compile/unit tests
Paper + Fabric World V7 / Map V5 interoperability
existing-world adoption
Teleport auto-load
idle auto-unload
occupied-world guards and managed live-export snapshot restoration
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
