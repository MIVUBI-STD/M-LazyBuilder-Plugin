# World Manager Client Flow

Canonical UX contract for the first-party World Manager client surface.

## Entry

`L` opens the World Manager. The screen refreshes one server-authoritative world list over `lazybuilder:world`; the client does not maintain a second world registry.

```text
World Manager
├── Create
├── Refresh
└── Managed Worlds
    ├── Teleport
    ├── Load / Unload
    ├── Archive / Restore
    ├── Clone
    ├── Settings
    └── Delete
```

Xaero map actions remain under `lazybuilder:map`. File bytes remain under `lazybuilder:transfer`. General world management stays under the single `lazybuilder:world` channel.

## Create

Create exposes only:

```text
World Folder
Display Name
Type: Flat | Void
```

Advanced settings are intentionally not duplicated into Create. Newly created worlds use the canonical `BUILD_READY` policy, then later changes belong to World Settings.

## Lifecycle safety

Archive uses an explicit confirmation screen because it unloads the world. Restore is non-destructive and can run directly.

Delete is permanent and therefore requires the user to type the exact canonical world folder name. The server repeats the same exact-name validation before the phased delete begins.

Clone asks only for destination folder and display name. The server owns source quiescing, sanitized filesystem copy, publication, rollback, and restoration of the source load state. Heavy clone/delete filesystem work stays off the Paper main thread.

## General settings

Settings are loaded from the server on demand and return a canonical snapshot. The current compact General surface includes:

```text
Auto Load
Default Game Mode
Difficulty
PVP
Set Current Position as Spawn
Reset to BUILD_READY
```

The screen also shows canonical Weather, Time, and Spawn values from Paper as read-only context. Client controls never assume a mutation succeeded; every write returns a new server snapshot.

Opening Settings may load an unloaded ACTIVE world once because Paper-owned settings are the source of truth. There is no settings polling loop.

## State refresh

The server returns bounded `WorldSummary` / `SettingsSnapshot` responses. Client revision changes rebuild only the open LazyBuilder screen. No background world-list refresh runs while the user is idle.

## Remaining UX slice before live validation

Whole-world Export and Import publication still need to be surfaced through the same World Manager flow. Their file bytes must continue to use the existing transfer channel rather than creating another upload/download system.

After those two flows are connected, remote work should stop at a final pre-test audit. Runtime button placement, Paper lifecycle behavior, native dialogs, Xaero transforms, and real filesystem/network performance require local/live proof.
