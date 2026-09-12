# World Manager Client Flow

Canonical UX contract for the first-party World Manager client surface.

## Entry

`L` opens the World Manager. The screen refreshes one server-authoritative world list over `lazybuilder:world`; the client does not maintain a second world registry.

```text
World Manager
├── Create
├── Import
├── Refresh
└── Managed Worlds
    ├── Teleport
    ├── Load / Unload
    ├── Archive / Restore
    ├── Clone
    ├── Settings
    ├── Export
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

## Import

Import remains one visible user flow even though it crosses two canonical server owners:

```text
Import World
→ choose destination folder + display name
→ native .zip/.mcworld picker
→ lazybuilder:transfer upload
→ server checksum publication into world/imports
→ lazybuilder:world ImportWorld request
→ archive validation / optional conversion
→ publish managed world
→ return canonical WorldSummary
```

The transfer controller exposes only an upload-completion continuation to the World Manager UI. It does not learn import/conversion semantics. If upload fails, publication is never requested.

## Whole-world Export

V1 exposes the native Java 1.21.4 whole-world export fast path first:

```text
Export
→ artifact name
→ JAVA_1_21_4
→ safe snapshot
→ source world restored immediately after snapshot
→ package export artifact
→ ExportReady(artifact)
→ existing transfer download
→ native Save dialog
```

The World Manager does not create a second download system. Cross-version/Bedrock conversion already exists behind the server export service, but additional target-format selection should be surfaced only after the client receives a verified supported-format catalog rather than hardcoding converter versions into UI.

Export Area remains the specialized Xaero path and reuses the same export/transfer owners.

## State refresh

The server returns bounded `WorldSummary` / `SettingsSnapshot` responses. Client revision changes rebuild only the open LazyBuilder screen. No background world-list refresh runs while the user is idle.

## Pre-test boundary

The first-party World Manager flow is now connected for Create, list, Teleport, Load/Unload, Archive/Restore, Clone, General Settings, permanent Delete, Import publication, and native whole-world Export. Remote development should now be limited to compile/test fixes and one final failure-path/ownership audit.

Runtime button placement, Paper lifecycle behavior, native dialogs, Xaero transforms, converter quality, and real filesystem/network performance require local/live proof.
