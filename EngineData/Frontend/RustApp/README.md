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

The repository includes two Launcher-only Windows entrypoints at the repository root:

```text
BUILD-LAUNCHER.cmd
UPDATE-LAUNCHER.cmd
```

### First install / package handoff

Use:

```powershell
.\BUILD-LAUNCHER.cmd
```

`BUILD-LAUNCHER.cmd` is only the build helper. The actual LazyBuilder application delivered to the user is an `.exe`, matching the familiar Windows launcher model used by apps such as Modrinth.

A successful normal build publishes exactly one clean handoff folder:

```text
dist/
└── LazyBuilder/
    ├── LazyBuilder-Setup.exe   ← recommended installer/update package
    ├── LazyBuilder.exe         ← raw developer diagnostic binary
    └── README.txt
```

Before every build, the previous `dist/LazyBuilder` handoff and old generated NSIS installer EXEs are removed. Old local installer files therefore do not accumulate between tests.

### Updating an already installed local Launcher

For repeated Local-PC testing after LazyBuilder is already installed, close the Launcher and run:

```powershell
.\UPDATE-LAUNCHER.cmd
```

This is the preferred local iteration flow. It:

```text
cleans old local installer handoff files
→ verifies/typechecks/tests Launcher
→ builds the current Tauri package
→ uses that package as a temporary in-place update transaction
→ updates the installed LazyBuilder
→ deletes the temporary installer package
```

After success there is no new user-facing installer left behind. Open LazyBuilder normally from its existing Start Menu/desktop shortcut. Server workspaces and normal LazyBuilder user data are preserved; only the installed application files/resources are refreshed by the installer owner.

The update flow intentionally refuses to run while `lazybuilder.exe` is open and refuses compile-only builds with missing core JARs. This avoids partial application replacement.

The build/update scripts intentionally operate only on the Desktop Launcher. They do not compile Fabric or Paper modules.

The verification/build performs:

```text
npm ci
→ Svelte typecheck
→ frontend production build
→ Tauri icon generation
→ cargo check --locked
→ cargo test --locked
→ Windows Tauri + NSIS package
```

Required local tools:

```text
Windows 10/11
Node.js 24+
Rust stable toolchain (rustup/cargo)
Microsoft C++ Build Tools required by Tauri
WebView2 runtime
```

### Core JAR requirement

A Launcher build does not compile Paper modules. Runtime-ready build/update requires matching core JARs already present in:

```text
src-tauri/resources/core/
├── World-Manager-0.1.0-SNAPSHOT.jar
└── Utilities-Manager-0.1.0-SNAPSHOT.jar
```

If either JAR is missing, normal packaging and installed-app update stop before replacing the app. This prevents an apparently successful package that cannot complete `Prepare server` on a fresh workspace.

For an explicit Launcher compile/typecheck check only, run:

```powershell
.\BUILD-LAUNCHER.cmd -AllowMissingCore
```

or invoke the PowerShell script directly:

```powershell
cd EngineData\Frontend\RustApp
.\build-local.ps1 -AllowMissingCore
```

Compile-only mode is not suitable for updating the installed Launcher or validating fresh-server provisioning.

### World Manager compatibility

The Launcher checks the running World Manager desktop bridge protocol before listing worlds. This Launcher expects protocol version `2`. A mismatched core component is reported as an update/synchronization problem rather than being allowed to fail later through stale world actions.

### Windows window behavior

The Tauri executable uses the Windows GUI subsystem and managed Java/Paper child processes are created without console windows. Normal use should therefore show only the LazyBuilder Launcher window. Server output remains available through the Launcher's log reader rather than a separate Command Prompt.
