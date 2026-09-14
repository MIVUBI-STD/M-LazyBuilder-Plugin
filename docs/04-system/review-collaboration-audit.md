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
| Copy current review reference | Approve for minimal implementation | Combines existing world identity with current local location into one portable text handoff. |

## Approved minimal concept: Review Reference

The only collaboration feature approved by this audit is an on-demand **Review Reference**.

Its purpose is simple: let a builder copy enough context to paste into Slack, Discord, email, GitHub, a task tracker, or another existing communication system.

Recommended canonical text shape:

```text
World: <display name>
World ID: <world id>
Location: <x> <y> <z>
Dimension: <dimension id>
```

Folder name may be included only if it materially helps internal identification. It should not replace the stable world id.

The reference is generated at the moment the user requests it. It is not stored by LazyBuilder.

## Ownership

The feature belongs to **Map Manager**, because the authoritative managed-world identity is already owned there.

```text
Map Manager
├── current managed-world identity
├── local current location read
└── Review Reference formatting / contextual copy action
```

Utility Manager must not become a dependency merely because it contains a small clipboard helper. Cross-manager implementation imports remain forbidden. Map Manager may use Minecraft's native clipboard path directly for this contextual action.

No shared client module should be extracted for clipboard behavior at this stage; the operation is too small to justify new coupling.

## Interaction contract

Review Reference must remain contextual and low-friction:

- no default keybind;
- no radial menu;
- no permanent HUD;
- no automatic copy;
- no background tracking;
- no persistence;
- no server-side notes or marker storage;
- no screenshot requirement;
- no teleport side effect.

Preferred placement is an existing Map Manager/map context surface where the current managed-world state is already visible or relevant.

The action should be unavailable when no managed world is active rather than fabricating a world identity.

## Location rules

Location is observation only, not a build/edit feature.

Use the player's current block position and current dimension at copy time. Do not add a permanent coordinate overlay, saved waypoint list, camera bookmark, or navigation system.

Coordinates remain plain portable information. Existing map/build tools remain responsible for navigation, waypointing, cameras, and editing.

## External collaboration boundary

LazyBuilder should produce portable context, not own the receiving workflow.

```text
LazyBuilder Review Reference
        ↓ copy text
Slack / Discord / Email / GitHub / Task Tracker / other team system
```

LazyBuilder does not need integrations with those systems for the current scope.

If direct external integrations are proposed later, they require a separate product review because authentication, permissions, data ownership, and failure behavior are materially different responsibilities.

## Rejected expansion

The approved Review Reference must not grow by default into:

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

## Implementation gate

A future implementation is acceptable only if it can be completed as a small Map Manager contextual action using existing current-world authority and vanilla client state.

No protocol version change is required for the first implementation because world identity already exists in Map Manager and position/dimension are available locally.

If implementation reveals that new persistent or synchronized state is required, stop and re-audit instead of expanding the feature implicitly.

## Audit result

Review / Collaboration has one approved narrow gap:

```text
Copy Review Reference
= managed world identity + current location + dimension
```

Everything else remains external or deferred.

The next non-tool builder review is **Reliability / Recovery**: identify warnings or recovery context that can prevent workflow mistakes without inventing autosave, backup, or build-state systems already owned elsewhere.
