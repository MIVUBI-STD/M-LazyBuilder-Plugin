# Review / Collaboration Audit

## Purpose

This audit reviews whether LazyBuilder should add builder-facing review/collaboration support after the Session / Project Context audit.

The goal is not to create an issue tracker, notes database, marker system, or live collaboration platform inside Minecraft. The goal is to identify one narrow handoff workflow that adds value without competing with existing build tools or external team systems.

## Existing context available

Map Manager already owns the authoritative current managed-world identity through `MapActionWireProtocol.CurrentWorldResult` and `ClientMapController.currentWorld()`.

Minecraft client state already knows the local player's current position and dimension while in-world.

Therefore, a useful review handoff can be generated on demand from existing state. No new server-side persistence, project database, marker registry, or collaboration protocol is required for the first scope.

## Candidate audit

| Candidate | Decision | Reason |
| --- | --- | --- |
| In-game issue tracker | Reject | Duplicates external project-management systems and creates persistent workflow ownership. |
| In-game notes database | Reject | Introduces storage, sync, search, editing, and lifecycle concerns unrelated to Map Manager. |
| Shared review markers / pins | Reject for current scope | Becomes a persistent collaboration/map annotation subsystem and requires authority/sync rules. |
| Live collaborator presence | Reject | Requires a new collaboration protocol and duplicates server/player awareness. |
| Comments attached to coordinates | Reject | Becomes issue/annotation storage rather than lightweight handoff. |
| Saved review cameras | Reject | Overlaps build-camera tooling already kept external. |
| Automatic screenshot + upload workflow | Reject | Expands into media/storage/sharing infrastructure and duplicates vanilla F2 plus external sharing tools. |
| Teleport-link protocol | Reject for now | Adds a new transport/command contract when plain coordinates are sufficient for handoff. |
| Copy managed-world reference | Keep as component | Uses existing authoritative Map Manager world state and no persistence. |
| Copy current review reference | Implemented | Combines existing world identity with current local location into one portable text handoff. |

## Implemented minimal concept: Review Reference

The only collaboration feature approved by this audit is an on-demand **Review Reference**.

Its purpose is simple: let a builder copy enough context to paste into Slack, Discord, email, GitHub, a task tracker, or another existing communication system.

Canonical text shape:

```text
World: <display name>
World ID: <world id>
Location: <x> <y> <z>
Dimension: <dimension id>
```

The reference is generated at the moment the user requests it. It is not stored by LazyBuilder.

## Ownership

The feature belongs to **Map Manager**, because the authoritative managed-world identity is already owned there.

```text
Map Manager
├── current managed-world identity
├── local current location read
└── Review Reference formatting / contextual copy action
```

Utility Manager is not a dependency merely because it contains a small clipboard helper. Cross-manager implementation imports remain forbidden. Map Manager uses Minecraft's native clipboard path directly for this contextual action.

No shared client module is extracted for clipboard behavior; the operation is too small to justify new coupling.

## Interaction contract

Review Reference remains contextual and low-friction:

- no default keybind;
- no radial menu;
- no permanent HUD;
- no automatic copy;
- no background tracking;
- no persistence;
- no server-side notes or marker storage;
- no screenshot requirement;
- no teleport side effect.

The implementation is placed in the existing `WorldMapScreen` right-click action surface. The action is disabled when no managed world, player, or client world is available.

## Location rules

Location is observation only, not a build/edit feature.

The implementation reads the player's current block position and current dimension at copy time. It does not add a permanent coordinate overlay, saved waypoint list, camera bookmark, or navigation system.

Coordinates remain plain portable information. Existing map/build tools remain responsible for navigation, waypointing, cameras, and editing.

## External collaboration boundary

LazyBuilder produces portable context, not the receiving workflow.

```text
LazyBuilder Review Reference
        ↓ copy text
Slack / Discord / Email / GitHub / Task Tracker / other team system
```

LazyBuilder does not need integrations with those systems for the current scope.

If direct external integrations are proposed later, they require a separate product review because authentication, permissions, data ownership, and failure behavior are materially different responsibilities.

## Rejected expansion

The implemented Review Reference must not grow by default into:

- issue creation;
- assignees/status/priority;
- comments or threads;
- review-marker synchronization;
- persistent coordinate lists;
- automatic screenshots;
- media upload;
- live presence;
- shared cursors;
- voice/chat systems;
- teleport commands;
- build state snapshots.

Each of those would create a distinct subsystem rather than a lightweight handoff.

## Implementation result

`Copy Review Reference` is implemented as a small Map Manager contextual action using existing current-world authority and vanilla client state.

No protocol version change, persistent state, background process, new keybind, or cross-Manager dependency was required.

```text
Copy Review Reference
= managed world identity + current player block location + current dimension
```

Review / Collaboration is therefore complete for the current scope. Any expansion requires a new ownership/overlap review.
