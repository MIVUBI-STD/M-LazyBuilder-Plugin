# World Manager Client Flow

Canonical owner for the user-facing World Manager navigation and operation flow.

## Goal

World Manager is a daily builder workspace, not a server administration panel. Builders should only see decisions they actually need to make. Internal folder naming, artifact routing, transfer sessions, registry ownership, and filesystem details remain implementation concerns.

The client stays presentation/input only; it does not create a second lifecycle, settings, transfer, registry, or filesystem authority.

## Daily navigation

```text
M
→ World Map
→ Worlds
   ├── choose a world
   │   ├── Teleport
   │   ├── Load World / Unload World
   │   ├── Export World
   │   ├── Settings
   │   ├── Clone
   │   ├── Archive
   │   └── Delete
   └── + Add World
       ├── Create New World
       └── Import Existing World
```

The map is the primary entry surface. World Manager is secondary and returns to the same map instance so camera/zoom state is preserved.

## Builder-facing information hierarchy

The world list shows only what helps a builder choose a world:

```text
Display Name
Loaded indicator when relevant
```

The selected-world detail shows:

```text
Display Name
Loaded / Not loaded / Archived
World Type
Actions
```

Internal folder names are intentionally not part of the normal daily detail view. They appear only where required for safety, such as permanent-delete confirmation.

## Responsive navigation

Wide GUI layouts use a two-pane workspace:

```text
World list | Selected world + actions
```

Narrow GUI layouts use a single-pane flow:

```text
World list
→ choose world
→ world detail
→ Back to Worlds
```

`ESC`/Back from a compact detail returns to the world list before leaving World Manager. UI must not require a desktop-sized logical resolution or a specific Minecraft GUI Scale.

## Primary actions

Daily actions are visually stronger than management actions:

```text
Primary
- Teleport
- Load World / Unload World
- Export World

Management
- Settings
- Clone
- Archive
- Delete
```

Delete remains visually dangerous and intentionally requires more friction than everyday actions.

## Create World

```text
Create New World
→ World Name
→ World Type: Flat | Void
→ Create World
→ visible creation state
→ authoritative result
→ return to World Manager
```

The builder does not enter a server folder. A unique internal folder is derived automatically.

Advanced settings are intentionally not duplicated into Create. They remain in World Settings after creation.

## Import World

```text
Import Existing World
→ optional World Name
→ native file picker (.zip / .mcworld)
→ prepare/hash
→ bounded upload with progress
→ validate/import
→ publish managed world
→ return to World Manager
```

The builder does not choose a destination directory. File selection, upload, validation, conversion when applicable, and publication are one continuous Import operation.

There is no separate Upload Manager.

## Export World

```text
Export World
→ File Name
→ prepare safe world backup
→ native Save As
→ bounded download with progress
→ finalize/checksum
→ return to World Manager
```

The client currently exposes the native Java 1.21.4 world-backup target. Additional formats should only appear when a verified supported-format catalog exists.

`Export Area` remains a map action because spatial selection belongs to the map, while still reusing canonical export and transfer owners.

## Clone

```text
Clone
→ Clone Name
→ Clone World
→ visible clone state
→ authoritative result
```

The destination folder is derived automatically and is not exposed as a normal builder decision.

## Settings

The settings screen owns its initial settings request; World Manager only navigates to it. This avoids duplicate request paths.

Builder-facing labels include:

```text
Load on Server Start
PVP
Default Game Mode
Difficulty
Set Current Position as World Spawn
Reset Builder Defaults
```

The implementation may still use internal protocol names such as `autoLoad` or `BUILD_READY`, but those internal names should not leak into normal UI wording.

## Archive and delete

Archive is reversible and requires explicit confirmation.

Permanent delete is intentionally stricter:

```text
Delete
→ warning
→ type exact internal folder name
→ Delete Permanently
→ visible deletion state
→ authoritative result
```

Exact-name confirmation is one of the few places where internal folder identity is intentionally surfaced because it is a destructive safety guard.

## Operation feedback

Use one consistent state model:

```text
idle
running
success
error
```

Rules:

- do not infer success locally;
- do not fake progress percentages when there is no authoritative percentage;
- byte transfer progress may show a real percentage;
- world operations without measurable progress show a clear activity label;
- errors remain visible in the screen where the operation was initiated or in World Manager if the user navigated back;
- leaving an operation screen does not imply cancellation unless an explicit Cancel operation exists.

## Custom UI system

World Manager and its child screens use the first-party LazyBuilder UI system rather than vanilla Minecraft button chrome:

```text
LbUi
LbButtonWidget
```

Visual hierarchy:

```text
Primary    everyday main action
Secondary  normal supporting action
Ghost      low-emphasis/navigation action
Danger     destructive action
```

The UI system stays intentionally small. It does not introduce a second application framework or replace Minecraft screen/input lifecycle ownership.

## Channel / protocol shape

```text
lazybuilder:world     world list/create/manage/settings intents
lazybuilder:map       spatial map intents only
lazybuilder:transfer  file bytes only
```

The client does not own duplicate lifecycle, settings, operation locking, conversion, transfer ordering, or filesystem business logic.

## Efficiency rules

- world list refreshes on screen entry, explicit refresh, or relevant mutation;
- no world-list polling;
- no settings polling;
- one settings request owner;
- no client-side shadow registry;
- no client directory scanning for server worlds;
- internal folder naming is derived only when an operation needs it;
- expensive file/conversion work remains request-bound;
- responsive layout is calculated from current logical screen size rather than duplicated desktop/mobile screens.

## Proof boundary

Source review proves ownership, navigation structure, terminology, and state wiring. Final proof still requires local Fabric compilation and live 1.21.4 client/server validation across multiple GUI Scale settings, native dialogs, real import/export transfer, permissions, world lifecycle actions, and perceived builder usability.
