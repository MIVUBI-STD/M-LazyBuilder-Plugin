# LazyBuilder Client Runtime Smoke Gate

## Purpose

Compile/test success is necessary but not sufficient for a Minecraft client release. This gate covers behavior that only becomes trustworthy after Fabric, Mixin, Minecraft UI, networking, filesystem, and window lifecycle are exercised together.

Run this checklist on the exact commit intended for `main` or a release. A failure blocks promotion until the defect is fixed or explicitly removed from active product scope.

## Environment

Record:

```text
Commit SHA:
Minecraft: 1.21.4
Fabric Loader:
Fabric API:
Java: 21
OS:
Profile:
```

Use a clean or known Modrinth profile synced through the LazyBuilder launcher. Confirm exactly these LazyBuilder Fabric mods are installed:

- LazyBuilder Map Manager
- LazyBuilder Utility Manager
- LazyBuilder Performance Manager

## Gate A — Startup and shutdown

- Minecraft reaches the title screen without Fabric/Mixin errors.
- Join the intended LazyBuilder test server successfully.
- Disconnect to title screen and join again.
- Close Minecraft normally while map cache data is dirty.
- Relaunch and confirm previously explored map terrain is still available.
- No `.tmp`/`.part` LazyBuilder files remain after normal completed operations.

## Gate B — Map Manager

### Map interaction

- Open World Map.
- Pan continuously in several directions.
- Zoom through near and far levels repeatedly.
- Player marker remains correct.
- Chunk/region overlays remain aligned when enabled.
- Revisit previously explored terrain after moving away; cached terrain remains visible.

### Scope and persistence

- Change dimension and confirm the map does not leak terrain from the previous dimension.
- Return to the previous dimension and confirm its remembered terrain is restored.
- Disconnect/reconnect and confirm map scope is still correct.
- Restart Minecraft and confirm persisted terrain can be loaded again.

### Transfer lifecycle

- Import a small known-good world archive.
- Export a world and verify the completed file can be opened/read.
- Perform an area export from a map selection.
- Cancel a file chooser and confirm the transfer controller returns to idle.
- Start an import preparation, then disconnect before it finishes; no stale upload may start afterward.
- Start a download, then disconnect; partial local data is cleaned and no stale completion callback resumes it.
- A checksum/error path reports failure rather than publishing an invalid final file.

## Gate C — Utility Manager

### Chat

- Send enough messages/history entries to exceed Vanilla's normal 100-entry retention boundary; extended history remains usable.
- Type an unsent draft, close chat, reopen chat, and confirm the draft returns.
- Disconnect and join another server; the old chat draft must not follow the old connection context.

### Connection recovery

- Successfully join server A, disconnect, then attempt server B and intentionally fail before JOIN.
- The disconnect screen's Reconnect action must target server B, not stale server A.
- Copy Details must contain the current attempted server/reason when available.

### Screenshot/reload/window

- With contextual screenshot names enabled, use F2 and confirm a safe contextual filename is created.
- Explicit screenshot filenames supplied by another path remain unchanged.
- Trigger a client resource reload after startup; one native completion toast appears.
- With borderless mode enabled, restart Minecraft and confirm borderless applies only at startup and does not override exclusive fullscreen.

## Gate D — Performance Manager

### Frame monitor

- At a normal focused 60+ FPS target, ordinary play remains `NORMAL` unless sustained frame pressure occurs.
- Intentionally cap focused Minecraft at 30 FPS; normal ~33 ms frames must not be classified as lag merely because the target is 30 FPS.
- Trigger a world/dimension loading gap; the first returning frame must not create a false severe pressure spike.

### Background policy

With defaults:

```properties
background.enabled=true
background.unfocused_fps=30
background.minimized_fps=10
```

Verify:

- focused window follows the user's configured FPS limit;
- unfocused window is capped to at most 30 FPS;
- minimized window is capped to at most 10 FPS;
- returning focus restores user authority without rewriting the configured video-option value.

### Diagnostics

Capture an on-demand Performance snapshot and verify the values are plausible for:

- FPS/frame times;
- JVM used/max memory;
- render/simulation distance;
- focus/minimized state;
- chunk/entity/particle debug values.

No permanent performance HUD/history should appear.

## Gate E — Release evidence

Record each gate as `PASS`, `FAIL`, or `N/A` with a short note. Promotion requires all active-scope items to be `PASS`.

```text
A Startup/shutdown: PASS / FAIL
B Map Manager:      PASS / FAIL
C Utility Manager:  PASS / FAIL
D Performance:      PASS / FAIL

Tester:
Date:
Exact commit:
Notes:
```

CI remains responsible for repository contracts, unit tests, Java/Rust/Svelte builds, and packaged JAR validation. This runtime gate is deliberately limited to integration behavior that static/build CI cannot prove reliably.
