# World Manager Client Implementation

Implementation-facing companion to `world-manager-flow.md`. The flow document owns UX semantics; this file records how the current first-party Fabric surface maps those semantics onto current protocol and runtime owners.

## Entry and navigation

```text
M
→ World Map
→ Worlds
```

The list is server-authoritative. Pinned and Recent are client-owned navigation preferences scoped by user/server identity and never affect runtime loading.

Current managed-world state is derived from Paper-observed world transitions, not click history.

## Manage World

ACTIVE worlds expose builder-facing actions only:

```text
Teleport
Import / Export
Duplicate
World Settings
Archive
Delete
```

ARCHIVED worlds expose Restore and permanent Delete.

Runtime `Loaded` / `Unloaded` state is not product lifecycle. Paper loads on required use and unloads eligible empty worlds automatically.

## Import / Export

Import and Export use one screen/workspace. Manage World enters Export; Add World enters Import; Map Export Area enters the same Export path with transient selection context.

Whole-world and selected-area exports delegate to the same canonical `WorldExportService` path.

Import flow is:

```text
choose .zip / .mcworld
→ upload with lazybuilder:transfer
→ server inspection
→ review
→ explicit final Import
```

Inspection never publishes a world. Final Import revalidates and publishes. Abandoned reviewed artifacts are discarded by explicit event-driven cleanup owned by the World Control path.

## Transfer ownership

File bytes use only `lazybuilder:transfer`. Transfer owns bounded sessions, chunk ordering, checksum validation, storage preflight, partial cleanup, and disconnect cleanup. It does not own world semantics.

## Disconnect and recovery

Active byte transfers fail closed on disconnect. Completed heavy world-operation results may have bounded reconnect completion recovery; this is separate from byte-transfer resume and must not become a second resumable transfer subsystem.

## Delete / Archive safety

Archive is reversible. Delete is permanent and confirms using the builder-visible display name while the backend acts on immutable WorldId.

Operations requiring a consistent filesystem snapshot must fail safely while builders occupy the target world rather than silently ejecting them.

## Protocol ownership

```text
lazybuilder:world
→ World Control V5
→ world list/capabilities/create/manage/settings/import/export intents
→ server-authoritative Import inspection + review discard

lazybuilder:map
→ Map Action V2
→ current-world state, map teleport, selected-area export intent

lazybuilder:transfer
→ upload/download bytes only
```

World Control V5 excludes manual Load/Unload and autoLoad/runtimeState product metadata. Map Action V2 includes explicit current-world clear/update semantics.

Desktop ↔ Paper control is a separate authenticated loopback protocol and is not the Fabric World Control version.

## Failure presentation

Builder-facing errors describe the failed product action and next safe step. Do not expose internal converter/process/filesystem machinery unless it is required for actionable diagnostics.

## Proof boundary

Remote compile/build proof validates source compatibility, not real Minecraft UX. Local/live validation still owns GUI behavior, native dialogs, permission behavior, portal/command world changes, large transfers, storage failures, reconnect recovery, conversion targets, occupied-world safeguards, and idle unloading.
