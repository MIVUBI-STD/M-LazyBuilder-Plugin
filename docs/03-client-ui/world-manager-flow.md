# World Manager Client Flow

Canonical owner for the user-facing World Manager navigation and operation flow.

## Goal

The World Manager is a daily builder workspace, not a server administration console. Builders should be able to reach common tasks quickly without understanding registry IDs, transfer sessions, destination folders, artifact storage, conversion internals, or host filesystem details.

The client remains presentation/input only; it must not create a second lifecycle, settings, transfer, registry, or filesystem authority.

## Primary navigation

```text
M
→ World Map
   └── Worlds
       → World Manager
```

The fullscreen map is the primary LazyBuilder entry. World Manager is secondary and returns to the exact existing map screen so camera/zoom/selection state is preserved.

## Daily builder paths

Common tasks must remain shallow:

```text
Move to another world
M → Worlds → select world → Teleport

Create a build world
M → Worlds → + Add World → Create New World

Import an existing world
M → Worlds → + Add World → Import Existing World

Export / back up a world
M → Worlds → select world → Export World

Change common world behavior
M → Worlds → select world → Settings
```

Internal implementation concepts are hidden unless they are required for safety.

## Worlds workspace

Desktop/wide layout uses a stable list-detail workspace:

```text
Managed Worlds            Selected World
├── world A               ├── status
├── world B               ├── Teleport / Load
└── world C               ├── Export World
                          └── Settings / Clone / Archive / Delete
```

Rows show only decision-relevant state. Folder names may appear as subdued metadata on sufficiently wide layouts, but are not treated as a primary builder decision.

Primary actions:

```text
Teleport
Load / Unload
Export World
```

Management actions:

```text
Settings
Clone
Archive / Restore
Delete
```

Destructive actions use danger styling and explicit confirmation.

## Responsive navigation

Minecraft GUI Scale can produce very narrow logical screen widths. The World Manager must not assume desktop width.

Rules:

- at normal/wide widths, use list + detail side by side;
- below the compact breakpoint, use one pane at a time;
- compact list → tap/select world → compact detail;
- Back/ESC from compact detail returns to the world list before leaving World Manager;
- action buttons stack vertically when a detail pane is too narrow for two columns;
- page size adapts to available vertical space;
- no control may require horizontal scrolling or render outside the screen.

This keeps the interaction model identical across GUI Scale settings while changing only layout density.

## Add World

`+ Add World` intentionally contains exactly two choices:

```text
Create New World
Import Existing World
```

On wide layouts these appear as two cards. On narrow layouts they stack vertically. There is no separate Upload Manager or destination-folder screen.

## Create World

```text
Create New World
→ World Name
→ World Type: Flat | Void
→ Create World
→ visible server processing
→ return to World Manager after authoritative success
```

The server folder name is generated automatically and collision-safe. Builders do not type internal folder paths.

Advanced settings remain separate so creation stays quick.

## Import World

```text
Import Existing World
→ optional World Name
→ native file picker (.zip / .mcworld)
→ prepare/hash
→ bounded upload with real byte progress
→ server validation/import/conversion
→ publish managed world
→ return after authoritative completion
```

The file picker is the first meaningful import action. Internal destination naming is automatic. Upload and server import are one visible operation rather than two disconnected screens.

## Export World

```text
Export World
→ file name
→ prepare safe server snapshot
→ Save As
→ bounded download with real byte progress
→ checksum/finalize
→ return after completion
```

Do not close the screen immediately after requesting export. The builder should always know whether the system is preparing, waiting for Save As, downloading, complete, or failed.

`Export Area` remains a map action because spatial selection belongs to the map, while still reusing the canonical export/transfer owners.

## Clone World

```text
Clone
→ optional Clone Name
→ Clone World
→ visible server processing
→ return after completion
```

The destination folder is generated automatically. The builder chooses the human-facing clone name only.

## Settings

Settings use builder-facing language and server-authoritative snapshots.

Current surface:

```text
Load on Server Start
PVP
Default Game Mode
Difficulty
Set Current Position as World Spawn
Reset Builder Defaults
```

Internal terms such as `BUILD_READY` are not exposed as primary labels.

## Archive and Delete

Archive is reversible and uses a confirmation modal.

Permanent Delete intentionally adds friction:

```text
Delete Permanently
→ warning
→ type exact internal folder name
→ server deletion
→ return after authoritative completion
```

Exact-name confirmation is retained because it is a safety mechanism, not normal navigation.

## Operation feedback

Use one consistent state model:

```text
idle
choosing input
preparing
uploading / downloading
server processing
complete
error
```

Only show percentages when an authoritative byte/operation percentage exists. Never fake progress.

While a heavy operation is active, conflicting world-management actions are disabled.

## Visual system

Client screens use the first-party LazyBuilder visual system rather than vanilla Minecraft button chrome:

```text
LbUi
LbButtonWidget
custom panels
custom fields
custom progress
primary / secondary / ghost / danger hierarchy
```

Visual direction is a restrained dark editor/workspace UI: high legibility, limited accent color, clear action hierarchy, minimal decoration, and no dependency on an external UI mod.

## Channel / protocol shape

```text
lazybuilder:world     World Manager list/create/manage/settings intents
lazybuilder:map       spatial map intents only
lazybuilder:transfer  file bytes only
```

No UI simplification may create duplicate business logic. Server validation and world state remain authoritative.

## Efficiency rules

- list worlds on screen open, explicit refresh, or relevant mutation;
- no world-list polling;
- no settings polling;
- no client-side shadow registry;
- no directory scanning on the client;
- reuse server-returned snapshots;
- one transfer controller;
- one map presentation owner;
- expensive file/conversion work remains request-bound and server-owned.

## Proof boundary

Implementation is intentionally being completed before final validation. Final proof must cover multiple Minecraft GUI Scale values, narrow and wide logical resolutions, keyboard/mouse navigation, native file dialogs, real import/export transfers, Paper permissions, operation failures, map return-state preservation, and live builder workflows on a Minecraft 1.21.4 client/server pair.
