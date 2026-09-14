# LazyBuilder Launcher

Canonical desktop application source for LazyBuilder.

## Location

```text
apps/launcher/
```

## Stack

```text
Tauri 2
Svelte 5
Vite
TypeScript
Tailwind CSS 4
Rust
```

## Ownership

```text
src/                    presentation + application state
src/app/bridge/         typed Tauri command boundary
src-tauri/src/commands/ thin Tauri command adapters
src-tauri/src/engine/   desktop-native runtime/domain logic
```

Minecraft world authority remains in `plugins/world-manager/`. The Launcher may call its authenticated loopback bridge, but it must not duplicate world lifecycle, transfer, conversion, or filesystem ownership.

## Local Windows build

Repository-root entrypoints:

```text
BUILD-LAUNCHER.cmd
UPDATE-LAUNCHER.cmd
```

Normal runtime-ready build:

```text
BUILD-LAUNCHER.cmd
→ mvn verify
→ stage matching World-Manager + Utilities-Manager JARs
→ npm ci
→ Svelte typecheck/build
→ cargo check/test
→ Tauri + NSIS package
```

Output:

```text
dist/LazyBuilder/
├── LazyBuilder-Setup.exe
├── LazyBuilder.exe
└── README.txt
```

For repeated installed-app testing, close LazyBuilder and use:

```text
UPDATE-LAUNCHER.cmd
```

The update path rebuilds/tests the current Paper core, stages matching JARs, builds a temporary installer, updates the installed application, then removes the temporary installer handoff. Server workspaces and user data are preserved.

Required local tools:

```text
Windows 10/11
Java 21
Apache Maven
Node.js 24+
Rust stable toolchain
Microsoft C++ Build Tools
WebView2 runtime
```

## Core resources

Runtime-ready builds require:

```text
src-tauri/resources/core/
├── World-Manager-0.1.0-SNAPSHOT.jar
└── Utilities-Manager-0.1.0-SNAPSHOT.jar
```

The root build/update entrypoints stage these automatically from:

```text
plugins/world-manager/target/
plugins/utilities-manager/target/
```

For explicit Launcher compile/typecheck work only:

```powershell
cd apps\launcher
.\build-local.ps1 -AllowMissingCore
```

Compile-only mode is not suitable for fresh-server runtime validation or installed-app updating.

## Protocol boundary

The Launcher expects desktop loopback protocol version `2`. This contract is separate from the Minecraft World Control V5 and Map Action V2 protocols.

## Windows behavior

The Tauri executable uses the Windows GUI subsystem. Managed Java/Paper processes are launched without separate console windows; server output is surfaced through LazyBuilder.
