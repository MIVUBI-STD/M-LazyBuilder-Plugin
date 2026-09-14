# LazyBuilder Tauri Desktop Application

This is the canonical replacement for the transitional WPF desktop implementation.

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
src/
→ presentation + application state only

src/app/bridge/
→ typed Tauri command boundary

src-tauri/src/commands/
→ thin command wrappers

src-tauri/src/engine/
→ reusable desktop runtime/domain logic
```

Minecraft world authority stays inside `modules/world-manager`. The Rust desktop runtime may call its authenticated loopback control bridge but must not duplicate world lifecycle or filesystem ownership.

## Local Windows Build

The repository includes a Launcher-only build entrypoint at the repository root:

```text
BUILD-LAUNCHER.cmd
```

Double-click it on Windows, or run:

```powershell
.\BUILD-LAUNCHER.cmd
```

It intentionally verifies and builds only the Desktop Launcher. It does not compile Fabric or Paper modules.

The build performs:

```text
npm ci
→ Svelte typecheck
→ frontend production build
→ Tauri icon generation
→ cargo check --locked
→ cargo test --locked
→ Windows Tauri + NSIS build
```

Required local tools:

```text
Windows 10/11
Node.js 24+
Rust stable toolchain (rustup/cargo)
Microsoft C++ Build Tools required by Tauri
WebView2 runtime
```

Build outputs:

```text
src-tauri/target/release/lazybuilder.exe
src-tauri/target/release/bundle/nsis/*-setup.exe
```

### Core JAR note

A Launcher build does not compile Paper modules. For a full server provisioning/runtime test, matching core JARs must already exist in:

```text
src-tauri/resources/core/
├── World-Manager-0.1.0-SNAPSHOT.jar
└── Utilities-Manager-0.1.0-SNAPSHOT.jar
```

If they are absent, the build script prints a warning but still allows Launcher compilation. This keeps Launcher development independent from unrelated Paper/Fabric compile failures.

### Windows window behavior

The Tauri executable uses the Windows GUI subsystem and managed Java/Paper child processes are created without console windows. Normal use should therefore show only the LazyBuilder Launcher window. Server output remains available through the Launcher's log reader rather than a separate Command Prompt.
