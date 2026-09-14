# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current authority

```text
Local = active development/source authority
main  = stable/release authority
```

Do not pin current readiness to an old SHA, workflow number, or historical completion report. Resolve current status from `current-verification.md` and the latest `Verify` workflow for the exact current `Local` HEAD.

## Current development state

Remote architecture/source simplification is closed unless local/live evidence reveals a concrete defect. The following boundaries are considered structurally locked:

```text
Desktop Runtime
├── Server-Manager
└── Plugin-Manager

Paper
├── World-Manager
└── Utilities-Manager

Fabric Client Suite
├── Map Manager
├── Utility Manager
└── Performance Manager
```

The current task after remote synchronization is **proof**, not another architecture expansion.

Do not add another Manager, shared client implementation module, recovery subsystem, duplicate transfer system, manual world-runtime control layer, or builder-tool layer unless a reproducible failure proves the existing owner cannot satisfy an accepted requirement.

## Proof hierarchy

```text
REMOTE_GITHUB
→ repository consistency
→ Paper compile/tests
→ Utilities independent compile/tests
→ all three Fabric Manager builds
→ Launcher frontend/Rust checks/tests
→ Windows Tauri/NSIS package

LOCAL_CODE
→ target-PC toolchain/filesystem/build behavior
→ installed desktop behavior where CI cannot prove it

LIVE_SERVER
→ real Paper/Minecraft/Fabric/gameplay behavior
```

A green lower layer must never be described as proof of a higher layer.

## Required remote gate

For the exact candidate `Local` HEAD, the required `Verify` jobs are:

```text
consistency
utilities
paper
fabric
launcher-check
tauri-desktop
```

The repository is `REMOTE_GITHUB green` only when all required jobs for that exact HEAD complete successfully.

## Local handoff entrypoints

First local/runtime-ready package:

```powershell
.\BUILD-LAUNCHER.cmd
```

Normal runtime-ready build flow:

```text
Maven Paper reactor verify
→ stage matching World-Manager + Utilities-Manager JARs
→ Svelte typecheck/build
→ Rust check/tests
→ Tauri/NSIS package
```

`BUILD-LAUNCHER.cmd -AllowMissingCore` is compile-only and must not be used for fresh-server/runtime validation.

For an already installed local Launcher:

```powershell
.\UPDATE-LAUNCHER.cmd
```

The update flow must preserve user workspaces/data and replace only the installed application/resources through the normal installer owner.

## Local prerequisites

Use one Windows shell where the following resolve correctly:

```text
Java 21
Maven
Node.js 24+
npm
Rust stable (rustup/cargo/rustc)
Microsoft C++ Build Tools
WebView2 Runtime
Gradle 8.12 for independent Fabric proof
```

A PATH/toolchain failure is an environment defect until source evidence proves otherwise. Do not edit source merely to work around a local PATH issue.

## LOCAL_CODE proof order

Recommended sequence:

```text
1. confirm branch Local and clean/understood working tree
2. verify Java 21, Maven, Node/npm, Rust, Gradle 8.12
3. run python scripts/verify_versions.py
4. run Maven Paper/shared verify
5. build Map Manager independently
6. build Utility Manager independently
7. build Performance Manager independently
8. run BUILD-LAUNCHER.cmd for runtime-ready Windows package
9. inspect expected artifacts
10. install/open LazyBuilder and begin runtime smoke tests
```

If a command fails, record:

```text
component
command
expected
actual
exact error
first wrong owner
smallest proposed fix
```

Fix only that owner, rerun the failing target first, then rerun the relevant full gate.

## LIVE_SERVER proof sequence

After LOCAL_CODE is clean:

```text
1. install/open LazyBuilder
2. create/open/adopt one workspace
3. Prepare Server and accept EULA when required
4. start Paper 1.21.4
5. verify start / stop / restart
6. verify detached-process recovery
7. verify canonical runtime directories
8. verify World-Manager and Utilities-Manager enable
9. verify all three Fabric Managers independently
10. verify all three together
11. exercise World Manager lifecycle/permissions/import/export/transfer
12. exercise Utility Manager client convenience
13. exercise Performance Manager with and without Dynamic FPS
14. exercise Paper Utilities behavior
15. exercise Plugin Manager lifecycle
16. verify shutdown/restart persistence and cleanup
```

## High-value runtime checks

### Desktop / Server Manager

- managed Java 21 discovery/use;
- no server auto-start when Launcher opens;
- one visible LazyBuilder window, no unwanted console windows;
- one Paper process authority and detached recovery;
- safe workspace switching;
- update-in-place preserves user data;
- no stale installer artifacts.

### World Manager

- managed-world adoption/current-world synchronization;
- automatic load + idle unload;
- occupied-world safeguards;
- Duplicate / Archive / Restore / Delete;
- permissions and server authorization;
- whole-world Export;
- first-party Map Export Area selection;
- Import upload → inspection → review → explicit Import;
- abandoned review cleanup;
- conversion capability handling;
- large transfer/checksum/storage failures;
- disconnect/reconnect completion behavior.

### Fabric Map Manager

- M → Map → Worlds navigation across GUI scales;
- current managed-world push/clear;
- map context actions;
- Copy Review Reference;
- Teleport Here;
- Export Area;
- World Control V5 / Map Action V2 interoperability.

### Fabric Utility Manager

- chat history/draft;
- reconnect/copy-details;
- disconnect-screen mixin;
- borderless/F11 behavior;
- resource-reload notification;
- contextual screenshots and rapid-capture naming.

### Fabric Performance Manager

- focused/unfocused/minimized/focused FPS transitions;
- user's configured FPS remains authoritative when focused;
- Dynamic FPS ownership handoff is a no-op from LazyBuilder;
- optimizer capability detection remains informational.

### Paper Utilities-Manager

- Movement state restoration;
- Build Helpers behavior;
- World Safety protections;
- permission/help behavior;
- no overlap with World-Manager lifecycle.

## Canonical runtime layout

```text
Work Server - 1.21.4/
├── LazyBuilder.exe
├── server/
│   ├── paper.jar
│   └── plugins/
├── world-system/
│   ├── worlds/
│   ├── imports/
│   ├── exports/
│   ├── backups/
│   └── work/
└── tools/lazybuilder/
    ├── config/
    ├── cache/
    ├── logs/
    ├── disabled-plugins/
    └── plugin-backups/
```

Archive is lifecycle metadata, not a second world directory. Legacy inputs may be migrated/read only where explicitly supported; they must not become parallel active authorities.

## Defect handling

Only reproducible local/live failures reopen source work. Route the defect to the smallest owner:

```text
desktop/runtime/process/config     -> lazybuilder-desktop-runtime
third-party plugin lifecycle       -> lazybuilder-plugin-management
world/Paper behavior/files         -> lazybuilder-world-management
Desktop or Fabric presentation     -> lazybuilder-ui
shared Paper/Fabric wire contract  -> lazybuilder-protocol
```

Do not weaken tests, architecture guards, authorization, data-loss protection, bounded resource limits, or recovery rules merely to make a check pass.

## STOP condition

Stop architecture work when the accepted behavior and matching proof are complete. Remaining work after remote synchronization belongs to LOCAL_CODE/LIVE_SERVER proof unless a concrete reproducible defect proves otherwise.
