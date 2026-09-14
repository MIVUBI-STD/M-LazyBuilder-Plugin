# Current Operations

This directory owns current continuation/proof only. Durable product and architecture rules live in their domain docs.

## Current authority

```text
Local = active development/source authority
main  = stable/release authority
```

Do not pin current readiness to an old SHA, workflow number, or historical completion report. Resolve current status from `current-verification.md` and the latest `Verify` workflow for the exact current `Local` HEAD.

## Current development state

Remote architecture/source simplification is closed unless local/live evidence reveals a concrete defect. The current V1 runtime boundary is:

```text
Desktop Runtime
├── Server-Manager
├── Plugin-Manager
└── Client Setup

Paper
├── World-Manager
└── Utilities-Manager

Required Fabric V1
├── Map Manager
└── Utility Manager

Deferred research source
└── Performance Manager
```

Performance Manager is not bundled or installed by Client Setup in V1 and is not part of V1 readiness. Do not expand architecture unless a reproducible runtime failure proves an accepted requirement cannot be met by the current owner.

## Proof hierarchy

```text
REMOTE_GITHUB
→ repository consistency
→ Paper compile/tests
→ required Fabric V1 builds (Map + Utility)
→ Launcher frontend/Rust checks/tests
→ Windows Tauri/NSIS package

LOCAL_CODE
→ target-PC toolchain/filesystem/build behavior
→ installed Launcher + Modrinth integration

LIVE_SERVER
→ real Paper/Minecraft/Fabric/gameplay behavior
```

A green lower layer must never be described as proof of a higher layer.

## Required remote gate

For the exact candidate `Local` HEAD, the required `Verify` jobs are:

```text
consistency
paper
fabric
launcher-check
tauri-desktop
```

The repository is `REMOTE_GITHUB green` only when all five jobs for that exact HEAD complete successfully.

## Local handoff entrypoints

Runtime-ready package:

```powershell
.\BUILD-LAUNCHER.cmd
```

Normal build flow:

```text
Maven Paper reactor verify
→ build Map Manager + Utility Manager
→ stage tested Paper core + required client JARs
→ Svelte typecheck/build
→ Rust check/tests
→ Tauri/NSIS package
```

For explicit compile-only Launcher work:

```powershell
cd apps\launcher
.\build-local.ps1 -AllowMissingRuntime
```

Compile-only mode must not be used for fresh-server or Client Setup runtime validation.

For an already installed Launcher:

```powershell
.\UPDATE-LAUNCHER.cmd
```

The update flow must preserve server workspaces, selected Modrinth profile, and normal LazyBuilder user data.

## Local prerequisites

```text
Windows 10/11
Java 21
Maven
Gradle 8.12
Node.js 24+
npm
Rust stable
Microsoft C++ Build Tools
WebView2 Runtime
```

A PATH/toolchain failure is an environment defect until source evidence proves otherwise. Do not edit source merely to work around a local PATH issue.

## LOCAL_CODE proof order

```text
1. confirm current Local HEAD
2. verify Java 21, Maven, Gradle 8.12, Node/npm and Rust
3. run python scripts/verify_versions.py
4. run Maven Paper/shared verify
5. build Map Manager
6. build Utility Manager
7. run BUILD-LAUNCHER.cmd
8. install/open LazyBuilder
9. verify global Client Setup with an actual Modrinth profile
10. begin server/client runtime smoke tests
```

Performance Manager testing is separate deferred research and must not block V1 proof.

If a command fails, record the component, command, expected result, actual result, exact error, first wrong owner, and smallest proposed fix. Fix only that owner and rerun the failing target first.

## LIVE_SERVER proof sequence

```text
1. install/open LazyBuilder
2. select/sync a real Modrinth 1.21.4 Fabric profile
3. verify exactly Map Manager + Utility Manager are maintained by Client Setup
4. create/open/adopt one server workspace
5. Prepare Server and accept EULA when required
6. start Paper 1.21.4
7. verify start / stop / restart and detached recovery
8. verify World-Manager and Utilities-Manager enable
9. exercise World Manager lifecycle/permissions/import/export/transfer
10. exercise Map Manager client workflow
11. exercise Utility Manager client convenience
12. exercise Paper Utilities behavior
13. exercise Plugin Manager lifecycle
14. verify shutdown/restart persistence and cleanup
```

## High-value runtime checks

### Desktop / Server Manager

- managed Java 21 discovery/use;
- no server auto-start when Launcher opens;
- no unwanted console windows;
- one Paper process authority and detached recovery;
- safe workspace switching;
- update-in-place preserves user data;
- missing workspace locations remain registered rather than being silently deleted.

### Client Setup / Modrinth

- known-path detection;
- manual exact-profile selection for custom paths;
- selected profile persistence and revalidation;
- Minecraft 1.21.4 + Fabric last-launch verification;
- `<profile>\mods` derived automatically;
- transactional Map + Utility sync;
- unrelated mods remain untouched;
- Sync blocked while Minecraft is using the selected profile.

### World Manager / Map Manager

- managed-world adoption/current-world synchronization;
- automatic load + idle unload;
- occupied-world safeguards;
- Duplicate / Archive / Restore / Delete;
- permissions and server authorization;
- whole-world Export;
- first-party Map Export Area selection;
- Import upload → inspection → review → explicit Import;
- large transfer/checksum/storage failures;
- disconnect/reconnect completion behavior;
- World Control V5 / Map Action V2 interoperability.

### Utility Manager / Paper Utilities

- chat history/draft;
- reconnect/copy-details;
- borderless/F11 behavior;
- resource-reload notification;
- contextual screenshot naming;
- Movement state restoration;
- Build Helpers behavior;
- World Safety protections;
- permission/help behavior.

## Defect handling

Only reproducible local/live failures reopen source work. Route defects to the smallest owner and do not weaken tests, authorization, data-loss protection, bounded resource limits, or recovery rules merely to make a check pass.

## STOP condition

Stop architecture work when the accepted V1 behavior and matching proof are complete. Remaining work belongs to LOCAL_CODE/LIVE_SERVER proof unless a concrete reproducible defect proves otherwise.
