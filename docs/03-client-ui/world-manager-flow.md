# World Manager Client Flow

Canonical owner for the user-facing World Manager navigation and operation flow.

## Product goal

World Manager is a daily builder workspace, not a server administration panel. Builders should only see decisions they actually need to make. Runtime loading, filesystem identity, operation leases, transfer sessions, converter implementation, and storage routing remain backend concerns.

The client stays presentation/input only. It must not create a second world lifecycle, settings owner, transfer owner, registry, or filesystem authority.

## Canonical user model

Persistent world lifecycle has only two product states:

```text
ACTIVE
ARCHIVED
```

`Loaded`, `Unloaded`, `Loading`, and `Unloading` are not builder-facing world states. Runtime loading is automatic:

```text
Teleport
→ load world if required
→ teleport player

world empty + no conflicting operation + idle timeout
→ unload automatically
```

There is no normal client action named Load World or Unload World.

## Daily navigation

```text
M
→ World Map
→ Worlds
   ├── Search
   ├── Pinned
   ├── Recent
   ├── All Worlds
   ├── Archived
   └── + Add World
       ├── Create World
       └── Import World
```

The map is the primary entry surface. Returning from World Manager preserves the same map instance/camera state where possible.

## World list

The world list is a quick-navigation surface. A normal active row contains only what helps a builder choose where to work:

```text
★ Display Name                         [Teleport] [Manage]
```

Do not show technical runtime labels such as Loaded or Unloaded on normal rows.

Contextual labels may appear only when useful:

```text
You are here
2 builders here
Archived
operation progress / error
```

When the selected world is the player's current world, the redundant Teleport action may be omitted.

### Pinned

Pinned is a user navigation preference, not a world lifecycle or keep-loaded flag. Pinning must never prevent automatic idle unload.

### Recent

Recent means worlds the player successfully entered, not worlds merely clicked or managed. Recent history is bounded and user-scoped.

### Search

Normal mode is sectioned (Pinned / Recent / All Worlds). Search mode is one deduplicated result list rather than repeating the same world across multiple sections.

### Archived

Archived worlds are removed from the daily active list but remain discoverable in an Archived section/screen. Restore returns the world to ACTIVE. Archived worlds are never auto-loaded for normal use.

## Manage World

`Manage` opens the secondary world-management surface. Action hierarchy is:

```text
Primary
Teleport

Common
Import / Export
Duplicate
World Settings

Lifecycle
Archive

Danger
Delete
```

The UI should render common management actions as clean rows/cards rather than many equal-weight buttons.

## Import / Export workspace

Import and Export share one final workspace, not a hierarchy of intermediate menus:

```text
IMPORT / EXPORT
[ Export ] [ Import ]
```

`Export` and `Import` are tabs. Entering from Manage World defaults to Export. Entering from Add World defaults to Import. `Export Area` from the map enters the same Export tab with the current selection supplied as transient context.

### Daily defaults

Import/Export uses one user-scoped, server-scoped set of daily defaults. These are universal defaults for the builder's normal workflow, not per-world presets.

Normal Export should therefore be fast:

```text
EXPORT
Tana Samawa

Default settings
Java Edition • 1.21.4
Entire World • All Dimensions

[ Export World ]
Advanced options ▾
```

Advanced settings edit the same configuration. A user may either use changes once or explicitly save them as the new default for future worlds.

Do not persist transient values such as a selected map rectangle or a literal file name as daily defaults.

### Advanced Export

Advanced Export is inline/expandable on the same screen. It may include only capabilities actually supported by the verified backend/conversion runtime, for example:

```text
Edition
Version
Dimensions
Compatibility options supported by the active runtime
File name override
```

Never invent controls for converter features that do not exist. Edition/version catalogs come from authoritative backend capabilities; the client must not hardcode unsupported targets.

`Selected Area` is transient and appears only when a current map selection exists. Otherwise area is Entire World.

Java output uses `.zip`; Bedrock output uses `.mcworld`. File extension is decided by the system, not the user.

### Import

Import is file-first:

```text
IMPORT
[ Choose World File ]
```

After file selection:

```text
Detected edition/version
World name
Canonical server target
[ Import World ]
Advanced options ▾
```

Source edition/version is detected automatically. The server remains authoritative for validation. Import always creates a new managed world and never silently overwrites an existing one. Naming collisions are resolved before heavy processing, with an editable safe suggestion.

### Conversion presentation

`Chunker`, converter worker names, runtime manifests, pruning, format ids, artifact terminology, and job-slot terminology are internal implementation details and must never be exposed as product navigation.

User-facing copy should say things such as:

```text
Java Edition 1.21.4
Bedrock Edition
Will be converted to Java Edition 1.21.4
Conversion required
```

Cross-edition warnings are concise and contextual.

### Default validation

Saved daily defaults are validated against the current supported-format/capability catalog whenever the workspace opens. Unsupported saved values automatically fall back to the nearest recommended supported value with a small notice rather than producing a late failure.

## Export Area

Spatial selection belongs to the map:

```text
Map selection
→ Export Area
→ Import / Export workspace (Export tab)
→ Selected Area supplied as transient context
```

This reuses the canonical Export service and UI. There is no second export implementation.

## Create World

```text
Create World
→ World Name
→ World Type: Flat | Void
→ Create
→ authoritative result
→ return to Worlds
```

Internal folder identity is derived automatically. Advanced world settings remain in World Settings after creation.

## Duplicate

User-facing naming is **Duplicate** everywhere.

```text
Duplicate
→ New Name
→ Duplicate World
→ authoritative result
```

The duplicate receives a fresh world identity and ACTIVE lifecycle. It does not inherit user pin/recent navigation preferences or runtime loaded state.

## World Settings

World Settings owns its initial settings request. World Manager only navigates to it.

Builder-facing settings may include:

```text
Default Game Mode
Difficulty
PVP
World Rules
Set Current Position as World Spawn
Reset Builder Defaults
```

Manual load/unload and `Load on Server Start` are not part of the normal builder workflow. Runtime loading is automatic.

## Archive and Delete

Archive is reversible. Archiving an occupied world should be blocked rather than silently moving active builders.

Delete is permanent and intentionally requires stronger confirmation. Confirmation should use the builder-visible display name while the backend deletes by immutable WorldId. Internal folder identity should not be exposed merely for confirmation.

## Operation feedback

Use one presentation model:

```text
idle
running
success
error
```

Rules:

- never infer success locally;
- do not fake progress percentages;
- byte transfer progress may show authoritative percentages;
- non-measurable world operations use clear activity labels;
- leaving a screen does not imply cancellation;
- cancellation is shown only when the backend supports safe cancellation;
- otherwise use clear continue-in-background semantics;
- disconnect/reconnect must not leave permanent stuck busy state.

User-facing progress uses meaningful stages such as:

```text
Export: Preparing world → Converting → Creating file → Saving
Import: Reading file → Checking world → Converting → Adding world
```

Do not show internal worker/phase names.

## Failure and empty states

Required UX states include:

```text
Loading worlds…
No worlds yet
No search results
Server unavailable
Permission denied
Not enough server storage
Not enough client save storage
Operation in progress
```

Each state should provide the clearest available next action.

## Responsive and large-list behavior

Prefer a scrollable searchable list over page-number navigation for large world collections. Layout must remain usable across Minecraft GUI Scale settings and narrow logical resolutions.

## Custom UI system

World Manager uses the first-party LazyBuilder UI primitives (`LbUi`, `LbButtonWidget`) rather than vanilla gray button chrome.

Visual hierarchy:

```text
Primary    everyday main action
Secondary  normal supporting action
Ghost      navigation / low emphasis
Danger     destructive action
```

## Channel / protocol shape

```text
lazybuilder:world     world list/create/manage/settings/transfer intents
lazybuilder:map       spatial map intents only
lazybuilder:transfer  file bytes only
```

The protocol should model product concepts, not leak stale runtime-state machinery. A world summary should not require a persisted-looking `runtimeState` merely to render the list.

## Efficiency rules

- no world-list polling;
- no settings polling;
- no converter idle daemon;
- no duplicate client registry;
- no client scanning of server world directories;
- conversion/file work remains request-bound;
- runtime world loading/unloading remains server-owned and automatic;
- one implementation path for whole-world and area export;
- one Import UI and one Export UI regardless of entry point.

## Proof boundary

Source review can prove ownership, terminology, navigation, and contracts. Final proof still requires local Fabric compilation and live Java 1.21.4 client/server validation across GUI scales, real native dialogs, large transfers, reconnects, conversion paths, permissions, archive/delete safeguards, automatic idle unloading, and builder usability.