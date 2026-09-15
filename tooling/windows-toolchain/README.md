# LazyBuilder Windows Toolchain

Canonical toolchain/bootstrap package for the LazyBuilder Windows build and deployment flow.

## Source of truth

The repository-root `toolchain.json` defines the supported build baseline. Scripts in this package must read that manifest instead of duplicating version numbers.

## Baseline

- Java: Eclipse Temurin 21 LTS
- Node.js: 24.x LTS
- Maven: repository wrapper target 3.9.16
- Gradle: repository wrapper baseline 8.12
- Rust: exact toolchain pin via repository `rust-toolchain.toml`
- Tauri: 2.x resolved by lockfiles
- WebView2: Evergreen Runtime
- Native build: Visual Studio 2022 Build Tools + Desktop development with C++
- Python: not a mandatory build dependency

## Entrypoints

From repository root:

```text
CHECK-DEV.cmd
SETUP-DEV.cmd
BUILD-LAUNCHER.cmd
UPDATE-LAUNCHER.cmd
```

`CHECK-DEV.cmd` only validates. `SETUP-DEV.cmd` provides bootstrap guidance. It intentionally does not silently install or mutate global compiler toolchains.

## Rule

End users must never need Node, npm, Rust, Cargo, Maven, Gradle, Python, Git, or MSVC to run an installed LazyBuilder build.

See `docs/INTEGRATION.md` and `docs/VALIDATION.md` before changing the production build pipeline.
