# World Manager Client Implementation

Implementation-facing companion to `world-manager-flow.md`. The flow document is the UX authority; this file records how the current first-party Fabric surface maps that UX onto the available protocols and owners.

## Entry and navigation

`M` opens the fullscreen LazyBuilder map. `Worlds` opens the World Manager quick-navigation workspace.

```text
Map
└── Worlds
    ├── Search
    ├── Pinned
    ├── Recent
    ├── All Worlds
    ├── Archived Worlds
    └── + Add World
        ├── Create World
        └── Import World
```

The list is server-authoritative. Pinned and Recent are client-owned, per-user navigation preferences scoped by server identity; they are not world metadata and never affect runtime loading.

Recent is updated only after the current managed world is authoritatively observed from the server. The map channel also pushes current-world changes caused by portal, command, plugin, or other teleport paths so `You are here` and Recent do not depend on which UI initiated movement.

## Manage World

An ACTIVE world exposes builder-facing actions only:

```text
Teleport
Import / Export
Duplicate
World Settings
Archive
Delete
```

An ARCHIVED world exposes Restore and permanent Delete. Runtime `Loaded` / `Unloaded` state is intentionally absent from the UI and from durable world metadata.

The server reports whether the player can manage worlds and/or teleport. The client uses those capabilities to omit unavailable management actions rather than rendering a large set of mysteriously disabled controls. The server remains the final permission authority for every request.

## Runtime model

Persistent lifecycle is only:

```text
ACTIVE
ARCHIVED
```

Paper is the runtime authority:

```text
Teleport / settings / required use
→ load automatically when needed

empty ACTIVE world + idle timeout + no conflicting operation
→ unload automatically
```

There is no user-facing Load / Unload action and no `Load on Server Start` product setting.

Operations that require a consistent filesystem snapshot do not force active builders out of a world. Archive, Delete, Duplicate, Backup, and Export fail with a builder-facing occupied-world message when the world must be quiesced but still contains players.

## Import / Export workspace

Import and Export use one screen:

```text
IMPORT / EXPORT
[ Export ] [ Import ]
```

Manage World enters Export. Add World enters Import. Map Export Area enters the same Export workspace with a transient selection.

### Export

Normal Export uses the builder's saved daily default for that server:

```text
Java Edition • 1.21.4
or another verified server-supported target
```

Advanced options stay inline. A temporary target can be used once, or explicitly saved as the new daily default. Literal file names and map selections are never persisted as defaults.

The supported target catalog comes from `WorldExportService.supportedFormats()`. Native `JAVA_1_21_4` is always available. Additional Java/Bedrock targets appear only when the verified conversion runtime reports them.

Whole-world and selected-area exports share the same canonical export service. The map owns spatial selection only; it does not own a second export implementation.

### Import

Import is file-first:

```text
Choose .zip or .mcworld
→ upload through lazybuilder:transfer
→ validate / detect source
→ convert only when needed
→ publish a new managed world
```

The managed runtime target remains Java Edition 1.21.4. Source edition/version is detected rather than manually selected by the builder. Name collisions receive a safe new destination instead of silently overwriting an existing world.

## Transfer ownership

File bytes use the single `lazybuilder:transfer` owner. It provides bounded chunk transfer, checksum verification, partial-file cleanup, and one active client transfer.

Server upload storage is checked before receiving the declared file. Client export download now checks usable space at the selected save location after the authoritative export size is known and aborts before writing the file when local storage is insufficient.

Leaving the Import / Export screen does not imply cancellation. While work is active the UI uses `Continue in Background`. A Cancel action must not be shown until the relevant operation has safe end-to-end cancellation semantics.

## Disconnect and recovery

Transfer sessions are aborted and cleaned on player disconnect. Heavy World Manager operations are request-bound and server-owned; a completed heavy result can be retained briefly for the player and delivered after reconnect rather than leaving the client permanently stuck waiting for a response that was sent while offline.

Current-world state is refreshed/pushed independently of World Manager screen lifetime, so reconnect or movement outside the UI cannot permanently leave `You are here` pointing to the wrong managed world.

## Delete and archive safety

Archive is reversible and blocked while builders are inside the world.

Delete is permanent and uses the builder-visible display name for typed confirmation. Backend deletion still resolves the immutable WorldId; internal folder identity is not required as user confirmation.

## Protocol ownership

```text
lazybuilder:world
World list, capability flags, create/manage/settings,
whole-world export intent, import publication, verified export-format catalog

lazybuilder:map
Current-world state, map teleport, selected-area export intent

lazybuilder:transfer
Upload/download bytes only
```

World Control protocol is on the current V3 contract, which includes permission capabilities and excludes manual load/unload and auto-load metadata. Map Action protocol is on the current V2 contract, including explicit current-world clear/update semantics.

## Failure presentation

Builder-facing failures should explain the action that could not complete rather than backend machinery. Examples:

```text
Cannot export Tana Samawa while builders are inside the world.
The selected export version is no longer supported by this server.
Conversion support could not be prepared.
Not enough space at the selected save location for this export.
```

Do not expose converter runtime names, operation leases, snapshot internals, raw format IDs, or filesystem paths merely to explain an ordinary failure.

## Proof boundary

Source review proves ownership, terminology, protocol shape, navigation intent, and static safeguards only. Final proof still requires local Fabric compilation plus live Paper 1.21.4 validation for GUI scales, native file dialogs, portal/command world changes, permissions, occupied-world guards, large imports/exports, client/server disk-pressure failures, reconnect recovery, conversion targets, and idle unload behavior.
