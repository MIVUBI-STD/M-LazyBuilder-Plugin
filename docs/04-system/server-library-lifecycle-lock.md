# Server Library Lifecycle Lock

Status: source-remediation design lock for the `Local` branch.

## Ownership

Server-library lifecycle belongs to the Launcher workspace owner under `apps/launcher/`.

World lifecycle remains owned by World Manager. Do not route server duplication/deletion through World Manager merely because similar verbs exist there.

## Required server-library actions

```text
Open
Open folder
Duplicate server
Remove from library
Delete server
```

`Remove from library` is non-destructive and only removes the registry entry.

`Delete server` is destructive and must delete only a validated, registered LazyBuilder workspace after explicit typed confirmation.

## Duplicate contract

Duplicate is a full usable server copy, not a backup archive.

Required behavior:

```text
source must be Offline
→ validate registered LazyBuilder workspace
→ preflight available storage
→ copy into same-filesystem staging directory
→ exclude runtime/transient state
→ assign new workspace id + timestamps + display name
→ validate copied manifest/layout
→ atomic publish to final folder
→ register new workspace
→ leave duplicate Offline
```

Do not copy:

- PID/process markers;
- startup locks;
- transfer/incoming temporary state;
- transient work directories;
- launcher session state;
- stale `.tmp` / `.incoming` artifacts;
- server logs unless a future explicit product decision requires them.

Failure must remove incomplete staging data and leave source + registry unchanged.

## Remove contract

Remove from library:

```text
registry entry removed
→ workspace files untouched
→ active workspace cannot be removed while active
→ user may re-adopt/open later
```

No typed confirmation is required because data is not deleted, but the dialog must state clearly that files remain on disk.

## Delete contract

Delete requires:

```text
registered workspace
+ Offline process state
+ exact workspace identity
+ typed display-name confirmation
```

Delete must fail closed for `Starting`, `Online`, `Stopping`, or `Detached` states.

Canonical destructive flow:

```text
validate
→ stage workspace for deletion using same-filesystem rename where possible
→ remove registry authority
→ delete staged tree
→ recover safely on interrupted startup
```

The deletion owner must never accept arbitrary paths from UI as delete authority. The server id resolves the registered canonical path internally.

## UI lock

Server Library cards expose a small overflow menu (`…`), not permanent destructive buttons.

Recommended order:

```text
Open
Open folder
────────────
Duplicate server
────────────
Remove from library
Delete server…
```

`Delete server…` is the last action and uses danger styling.

Server Settings may expose the same canonical backend through a `Danger Zone`; it must not introduce another delete implementation.

## Confirmation lock

Delete confirmation has two gates:

1. consequence review showing server name + path + data categories;
2. exact server-name typing before `Delete permanently` enables.

The consequence copy must explicitly state that worlds, plugins, plugin data, server configuration, and LazyBuilder workspace data are permanently removed.

## Windows installation identity

Launcher packaging must preserve one canonical LazyBuilder identity across:

```text
installer
Start Menu
Desktop shortcut when chosen
executable
Windows taskbar / Alt+Tab
Installed Apps / uninstall entry
```

The bundle icon source is generated from `apps/launcher/src-tauri/app-icon.svg` via the existing icon preparation step. The NSIS installer uses the generated Windows icon.

Desktop shortcut is an install-time option and should default ON. Start Menu integration remains standard installer behavior. Do not add tray residency or Windows auto-start merely for discoverability.
