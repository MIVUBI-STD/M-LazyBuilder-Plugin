# LazyBuilder v1 Deployment Contract

## Scope

LazyBuilder v1 is a Windows desktop application for managing Paper 1.21.4 builder-server workspaces.

Locked v1 boundaries:

- Desktop: Tauri 2 + Rust + Svelte 5
- Installer: Tauri NSIS, current-user install
- Minecraft: Java Edition 1.21.4
- Server platform: Paper
- Java runtime: managed Temurin Java 21
- One active workspace per LazyBuilder application instance
- Core modules: World-Manager + Utilities-Manager bundled from the same source/release as the desktop app
- Application updater: manual NSIS upgrade for v1; built-in auto updater is deferred

## Current deployment phase

The current `Local` deployment phase is **installed Local PC validation**.

Canonical execution plan:

[`local-pc-validation-plan.md`](local-pc-validation-plan.md)

Remote CI proves that the candidate can be built, packaged, smoke-installed, and that tested Paper lifecycle paths run successfully. It does not replace the final target-PC acceptance flow.

The required Local PC order begins at the installer and proceeds through the full product:

```text
Installer
→ Launcher
→ managed Java + Paper
→ Plugin Manager
→ World Manager / Utilities Manager
→ Client Setup / Modrinth
→ Map Manager / Utility Manager
→ interoperability/restart/update
→ large-world/storage/conversion
```

## Ownership

### Installer / NSIS

Owns only the installed LazyBuilder application:

- install
- upgrade
- repair/reinstall application files
- uninstall
- shortcuts/application registration

It must never own, move, delete, or reset Minecraft server workspaces.

### Desktop Rust runtime

Owns:

- global workspace registry
- active workspace
- Create New Server
- Open Existing LazyBuilder Server
- Adopt Existing Paper Server
- managed Java runtime
- Paper provisioning/cache
- core-module synchronization
- explicit server-runtime updates

### Server workspace

Owns server-specific Paper files, worlds, plugin data, and LazyBuilder server metadata.

## Application data

Application install and server workspaces are independent.

Global app data:

```text
%LOCALAPPDATA%/LazyBuilder/
├── workspaces.json
├── runtimes/
│   └── java-21/
├── cache/
│   └── paper/
└── logs/
```

A server workspace is located wherever the user chooses:

```text
<My Server>/
├── server/
│   ├── paper.jar
│   ├── server.properties
│   ├── eula.txt
│   └── plugins/
├── world-system/
│   ├── worlds/
│   ├── imports/
│   ├── exports/
│   ├── backups/
│   └── work/
└── tools/lazybuilder/
    ├── config/
    │   └── workspace.json
    ├── cache/
    ├── logs/
    ├── disabled-plugins/
    └── plugin-backups/
```

## Create New Server

The launcher asks for:

1. server name
2. parent location

LazyBuilder creates the workspace and activates it. Provisioning is a separate idempotent action.

Provisioning sequence:

```text
WORKSPACE_CREATED
→ JAVA_READY
→ PAPER_READY
→ CORE_MODULES_READY
→ CONFIG_READY
→ EULA_REQUIRED
→ READY
```

EULA acceptance is always explicit user action and is never silently written by provisioning.

## Managed Java

LazyBuilder uses one managed Temurin Java 21 runtime shared across workspaces.

The archive checksum is verified before extraction. Individual workspaces reference the managed java executable through their server-manager configuration.

## Paper

New/unprovisioned workspaces resolve a stable Paper 1.21.4 build through PaperMC's downloads service.

Rules:

- stable channel only
- identified LazyBuilder User-Agent
- SHA-256 verification
- global download cache
- copy/install per workspace
- once a workspace records a Paper build, normal Prepare Server does not silently advance it

Paper updates are explicit user actions.

Before an explicit Paper update, the existing JAR is retained as:

```text
server/paper.jar.previous
```

## Core modules

World-Manager and Utilities-Manager are built from the same repository commit/release as the desktop app and staged into the Tauri application resources.

There is no separate remote core-module update authority in v1.

Core synchronization is explicit after initial provisioning and is blocked while the server is running.

Existing module JARs are backed up under `tools/lazybuilder/plugin-backups/` before replacement.

## Open Existing LazyBuilder Server

Only a valid LazyBuilder workspace is opened directly.

Canonical identity:

```text
tools/lazybuilder/config/workspace.json
```

Legacy LazyBuilder layout may be migrated to this manifest. A plain Paper server is not silently treated as a LazyBuilder workspace.

## Adopt Existing Paper Server

Adoption is two-stage:

```text
Select existing Paper root
→ Analyze
→ Show migration plan
→ Explicit Adopt Server
→ Execute
```

Analysis identifies:

- Paper server JAR
- world folders (`level.dat`)
- recognized Paper runtime files/directories
- legacy plugins explicitly replaced by LazyBuilder
- unknown root entries that will remain untouched

Migration rules:

- detected worlds → `world-system/worlds/`
- recognized Paper runtime → `server/`
- active Paper JAR → `server/paper.jar`
- Multiverse-Core / VoidWorld / BuildersUtilities families → `tools/lazybuilder/disabled-plugins/`
- unrelated plugins remain active
- unknown root files/folders remain untouched
- no destination is overwritten
- moves are rolled back if migration/registration fails

The existing independently launched server must be stopped before adoption.

## Workspace switching

Create/Open/Adopt/Switch is blocked unless the currently active managed server is `Offline` or `Crashed`.

v1 intentionally does not manage multiple active Paper processes from one app instance.

## Runtime update policy

Runtime update checks are user initiated.

LazyBuilder does not query PaperMC every time the app opens.

Explicit update controls:

- Check Runtime Updates
- Update Paper
- Sync Core Modules

Paper/core mutations are blocked while the server is running.

Application updates, Paper updates, core-module updates, and workspace-schema migrations remain separate mechanisms.

## Start gate

Start/Restart is allowed only when provisioning is complete:

- workspace metadata ready
- managed Java 21 ready
- Paper ready
- core modules ready
- config ready
- Minecraft EULA accepted

## Installer

Tauri bundles the Windows app through NSIS using current-user mode.

The release build must:

1. build Paper modules from the same commit
2. stage World-Manager and Utilities-Manager JARs into Tauri resources
3. typecheck/build Svelte
4. check/build Rust/Tauri
5. emit the NSIS installer

The installer never creates a Minecraft server by itself. Server creation is always performed from LazyBuilder's launcher after installation.

## Local PC deployment acceptance

The NSIS artifact is not considered end-user validated only because CI can install it silently.

Before promotion discussion, the installed Local candidate must complete the acceptance sequence in `local-pc-validation-plan.md`, including:

- clean install and update over an existing install;
- Launcher first run;
- managed Java/Paper provisioning and process lifecycle;
- representative Plugin Manager flow;
- World Manager and Utilities Manager behavior;
- real Modrinth Client Setup;
- real Map Manager and Utility Manager behavior inside Minecraft;
- reconnect/restart/full-PC reboot recovery;
- representative large-world/storage/conversion testing.

No unresolved P0/P1 defect may remain before final audit/promotion discussion.
