# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current State

- `Local` is the active development/source authority; `main` remains stable/release.
- World-Manager source architecture is structurally locked.
- Utilities-Manager source architecture and current scope are structurally locked.
- Desktop, Fabric client, World-Manager Paper module, and Utilities-Manager Paper module passed the latest completed remote gate before the final consistency/documentation cleanup.
- Canonical runtime storage wiring is source-implemented across Server-Manager, World-Manager, and Plugin-Manager.
- Source/CI proof remains distinct from actual Windows/Paper/Fabric runtime proof.

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

Archive/Restore is currently lifecycle metadata plus load-state handling; it does not own a second physical archive directory. Legacy World-Manager and Plugin-Manager locations are compatibility inputs only and must not become parallel active authorities.

## World-Manager

Canonical source boundaries are unchanged:

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

Completed remote audit findings addressed before local/live testing:

```text
legacy World-Manager bootstrap ownership       removed
unbounded World task queue                     bounded
heavy-operation orchestration duplication      consolidated
Paper main-thread dispatch duplication         consolidated
Utilities speculative/unused feature scope     removed
stale Spectator duplicate family               removed
Glazed Terracotta unsupported API usage        fixed + tested
World runtime path authority split             consolidated
Plugin-Manager transitional storage paths      consolidated
unused physical archives directory             removed from canonical bootstrap
stale architecture/maintenance docs            aligned to current source
```

Latest-head changes still require their own current CI completion before being called green. Do not infer latest-head success from an earlier green run.

## Remaining Validation

The next phase is `LOCAL_CODE` / `LIVE_SERVER`; do not add speculative feature scope before this validation unless a concrete defect requires a source fix.

Recommended live sequence:

```text
1. build current Local artifacts
2. start Paper 1.21.4 through the current desktop/server flow
3. verify canonical runtime directories are created in the expected workspace
4. verify World-Manager plugin enable + control bridges
5. verify Fabric World Manager screen and core lifecycle operations
6. verify Import/Export + native dialogs + transfer integrity
7. verify Xaero Teleport Here / Export Area
8. verify Utilities families and state restoration
9. verify Plugin-Manager canonical/legacy storage behavior
10. verify restart/shutdown persistence and cleanup
11. record only reproducible runtime defects, then fix them in Local
```

High-value runtime checks include:

- plugin enable/disable without stale listeners/session state;
- world create/load/unload/teleport/clone/archive/restore/delete correctness;
- fallback/default world delete protection;
- canonical `world-system/worlds` launch and fail-closed mismatch behavior;
- Import/Export on real archives and filesystem permissions;
- transfer behavior under realistic file sizes and remote latency;
- player state restoration for Fly/Noclip/Night Vision after toggle, logout, plugin disable, and external game-mode changes;
- Build Helpers interaction behavior in Creative builder workflows;
- World Safety protections without unintended gameplay ownership;
- native Windows dialogs and Tauri process lifecycle;
- Plugin-Manager migration of old disabled/category paths without overwriting canonical state;
- Xaero mixin/runtime compatibility.

## Packaging / Reproducibility Note

The desktop source currently relies on dependency resolution during CI because lockfiles are not yet part of the repository. This is acceptable for the current source-audit proof, but release packaging should generate and commit the appropriate npm/Cargo lockfiles before treating a binary build as reproducible release evidence.

## Proof Ceiling

`REMOTE_GITHUB` covers source/static/CI evidence only. Do not claim `LOCAL_CODE` or `LIVE_SERVER` success until those environments are actually exercised with the current artifacts.
