# LazyBuilder Launcher — Pre-Local Test Gate

This checklist is Launcher-only. Paper/Fabric failures outside the desktop Launcher do not change the Launcher verification result, but a full fresh-server runtime test still requires matching bundled core JARs.

## Source gate

Before moving to the local PC, confirm the current `Local` HEAD passes:

```text
npm ci
npm run typecheck
npm run build:frontend
npm run prepare:icons
cargo check --locked --manifest-path src-tauri/Cargo.toml
cargo test --locked --manifest-path src-tauri/Cargo.toml
```

The Windows workflow job `launcher-check` is the canonical CI proof for this gate.

## Runtime-ready package gate

A normal local package must contain matching core modules:

```text
src-tauri/resources/core/
├── World-Manager-0.1.0-SNAPSHOT.jar
└── Utilities-Manager-0.1.0-SNAPSHOT.jar
```

`BUILD-LAUNCHER.cmd` must stop instead of producing a runtime-ready installer when either core JAR is missing.

## First local install

Run:

```text
BUILD-LAUNCHER.cmd
```

Use only:

```text
dist/LazyBuilder/LazyBuilder-Setup.exe
```

Expected behavior:

- one LazyBuilder window only;
- no Launcher console window;
- Java validation does not open Command Prompt;
- starting Paper does not open Command Prompt;
- server log is viewed inside LazyBuilder;
- no server auto-start when LazyBuilder opens.

## Main local smoke test

Test in this order:

1. Open LazyBuilder.
2. Create or select one server.
3. Prepare the server and accept the EULA when required.
4. Start Paper and confirm the UI reaches `Running`.
5. Open the server log from Overview.
6. Open Worlds and Plugins and confirm the Launcher stays responsive.
7. Stop Paper and confirm the UI returns to `Offline`.
8. Close and reopen LazyBuilder and confirm the workspace remains registered without auto-starting Paper.

## Detached-process safety

Test once:

1. Start Server A.
2. Simulate an abnormal Launcher exit while Paper remains alive.
3. Reopen LazyBuilder.
4. Confirm Server B cannot be opened/created/adopted while Server A remains alive.
5. Confirm Server A can still be reopened for detached-process recovery.
6. Recover/stop Server A, then confirm switching to Server B becomes available.

## Clean update test

After the first install, use:

```text
UPDATE-LAUNCHER.cmd
```

Expected behavior:

- update is blocked while LazyBuilder is still running;
- the installed LazyBuilder is replaced in place;
- existing server workspaces/configuration remain intact;
- temporary update installer/output is removed after success;
- old installers do not accumulate;
- LazyBuilder opens normally after the update.

## Pass condition

Local testing is considered clean when:

```text
Launcher verification green
+ no console popups
+ one managed Paper instance only
+ workspace switching safety works
+ update-in-place preserves data
+ no stale installer/update artifacts remain
```
