# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- `Local` is the active development/source authority; `main` remains stable/release.
- `REMOTE_GITHUB` is complete.
- World-Manager source architecture is structurally locked.
- Utilities-Manager source architecture and current scope are structurally locked.
- Canonical runtime storage wiring is source-implemented across Server-Manager, World-Manager, and Plugin-Manager.
- Desktop dependency lockfiles are committed and CI enforces locked dependency resolution.
- Source/CI proof remains distinct from actual Windows/Paper/Fabric runtime proof.

Canonical completion record:

```text
docs/05-operations/remote-github-complete.md
```

Final verified source gate:

```text
head: fcae5870192252bcecc63c5f0c458bed446a64a8
run:  Verify #509

Paper modules/tests  SUCCESS
Fabric client build  SUCCESS
Tauri desktop        SUCCESS
Overall              SUCCESS
```

Documentation commits after that gate only record the handoff and do not expand runtime scope.

## Canonical Runtime Layout

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

Archive/Restore is lifecycle metadata plus load-state handling; it does not own a second physical archive directory. Legacy World-Manager and Plugin-Manager locations are compatibility inputs only and must not become parallel active authorities.

## Reproducible Desktop Dependencies

Committed lockfiles:

```text
EngineData/Frontend/RustApp/package-lock.json
EngineData/Frontend/RustApp/src-tauri/Cargo.lock
```

Final CI behavior:

```text
npm ci
cargo check --locked
Cargo cache key -> Cargo.lock
workflow permissions -> contents: read
```

The temporary CI mechanism that generated/committed lockfiles has been removed. Dependency locking is now repository state, not CI mutation.

## World-Manager

Canonical source boundaries:

```text
WorldManagerPlugin
↓
WorldManager services
↓
shared heavy-operation orchestration / Paper dispatcher
↓
transport adapters
  ├── desktop local-control bridge
  ├── Fabric world-control bridge
  ├── transfer bridge
  └── map/Xaero bridge
```

Implemented source flows include Create, list, Teleport, Load/Unload, Settings, Archive/Restore, Clone, Backup, Delete, Import, whole-world Export, and Export Area. Heavy desktop tasks use the bounded task runner; common heavy world operations share one orchestration path. Desktop and Fabric are transports, not duplicate world authorities.

The first-party Fabric World Manager UI/control path is source-implemented for list/refresh, Create Flat/Void, Teleport, Load/Unload, Archive/Restore, Clone, Settings, permanent Delete, Import publication, and native Java 1.21.4 whole-world Export. Xaero remains contextual for Teleport Here and Export Area.

## Utilities-Manager

Current feature scope is intentionally limited to three cohesive families:

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

Creation Tools and a separate Spectator family are intentionally out of scope because they are not required by the current builder workflow or would duplicate existing ownership.

Utilities uses one `UtilityFeatureRegistry`; each family is independently configurable and lifecycle cleanup is fail-isolated. No background worker, polling loop, NMS layer, or World-Manager implementation dependency was introduced.

The glazed-terracotta helper uses explicit cardinal rotation compatible with the Paper 1.21.4 compile surface:

```text
NORTH → EAST → SOUTH → WEST → NORTH
```

This mapping is covered by a focused unit test.

## Final Remote Audit Status

Completed remote findings addressed before local/live testing:

```text
legacy World-Manager bootstrap ownership       removed
unbounded World task queue                     bounded
heavy-operation orchestration duplication      consolidated
Paper main-thread dispatch duplication         consolidated
Fabric shutdown handling                       hardened
transfer session/file I/O path                 bounded/hardened
Utilities speculative/unused feature scope     removed
stale Spectator duplicate family               removed
Glazed Terracotta unsupported API usage        fixed + tested
World runtime path authority split             consolidated
Plugin-Manager transitional storage paths      consolidated
unused physical archives directory             removed
stale architecture/product docs                aligned
frontend dependency lock                       committed
Rust dependency lock                           committed
CI dependency resolution                       locked/reproducible
```

## Current phase: LOCAL_CODE

Remote structural/source work is closed unless local/live evidence reveals a real defect.

Start local validation from current `Local`:

```text
git checkout Local
git pull
```

The branch must contain at least the verified source head:

```text
fcae5870192252bcecc63c5f0c458bed446a64a8
```

and the subsequent REMOTE_GITHUB completion documentation.

Recommended local/live sequence:

```text
LOCAL_CODE
1. verify Java 21 / Node / Rust prerequisites
2. build current Maven Paper modules
3. build Fabric client
4. npm ci + locked Tauri/Rust build
5. inspect produced artifacts
6. prepare canonical Work Server - 1.21.4 workspace

LIVE_SERVER
7. start Paper 1.21.4 through LazyBuilder desktop
8. verify canonical runtime directories
9. verify World-Manager enable + control bridges
10. verify Fabric World Manager lifecycle UI
11. verify Import/Export + native dialogs + transfer integrity
12. verify Xaero Teleport Here / Export Area
13. verify Utilities families and state restoration
14. verify Plugin-Manager canonical/legacy storage behavior
15. verify restart/shutdown persistence and cleanup
```

High-value runtime checks:

- desktop app starts cleanly and finds Java 21;
- start/stop/restart/crash states behave correctly;
- `world-system/worlds` is the actual Paper universe;
- no duplicate runtime storage authorities appear;
- plugin enable/disable leaves no stale listeners/session state;
- create/load/unload/teleport/clone/archive/restore/delete correctness;
- fallback/default world delete protection;
- Import/Export on real archives and filesystem permissions;
- transfer checksum/integrity and realistic file-size behavior;
- native Windows dialogs;
- Fabric screen input/layout;
- Xaero mixin/runtime compatibility;
- Fly/Noclip/Night Vision state restoration;
- Build Helpers behavior in Creative builder workflows;
- World Safety protections without unintended ownership;
- Plugin-Manager migration of old disabled/category paths without overwriting canonical state;
- restart/shutdown persistence and cleanup.

## Defect handling

Only reproducible local/live failures reopen source work. Record:

```text
expected
actual
exact reproduction steps
relevant log/error
owning component
smallest failing boundary
```

Fix defects directly in `Local`. Do not create side branches unless explicitly requested. Do not reopen a locked architecture when a bounded owner-level fix is sufficient.

## Proof Ceiling

`REMOTE_GITHUB` is complete. `LOCAL_CODE` and `LIVE_SERVER` are not proven until the current artifacts are actually exercised in those environments.
