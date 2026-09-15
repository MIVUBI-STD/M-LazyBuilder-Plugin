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
- WebView2: Evergreen Runtime for installed/end-user runtime
- Native build: Visual Studio 2022 Build Tools + Desktop development with C++
- Python: not a mandatory build dependency

## Canonical developer command surface

`tooling/windows-toolchain/dev.ps1` is the single developer orchestration authority. `DEV.cmd` is a thin Windows convenience shim so the same command surface is easy to launch from Command Prompt or Explorer.

```text
DEV.cmd setup
DEV.cmd check
DEV.cmd build
DEV.cmd test
DEV.cmd update
DEV.cmd finalize-local
```

The command surface delegates to the existing specialist scripts; it does not duplicate build, bootstrap, runtime-proof, or installer logic.

Canonical lifecycle:

```text
fresh clone
→ DEV.cmd setup
→ DEV.cmd check
→ normal development
→ DEV.cmd build
→ DEV.cmd test
→ DEV.cmd finalize-local when the revision is ready for final remote CI
```

`finalize-local` is intentionally local only. Remote GitHub Actions remains an independent final proof and is dispatched separately when development reaches a reviewable checkpoint.

Legacy root entrypoints (`SETUP-DEV.cmd`, `CHECK-DEV.cmd`, `BUILD-LAUNCHER.cmd`, `TEST-LOCAL.cmd`, `UPDATE-LAUNCHER.cmd`) remain compatibility conveniences during migration. New documentation, automation, and agent instructions should target `DEV.cmd` / `dev.ps1` as the canonical interface.

## Command ownership

- `setup` delegates to the bootstrap owner and may repair supported missing prerequisites.
- `check` is non-mutating environment validation.
- `build` delegates to the runtime-ready Launcher build/package pipeline.
- `test` delegates to local Paper/restart/installer acceptance proof.
- `update` uses the same build owner with installed-app update semantics.
- `finalize-local` composes `check → build → test`; it does not invent a parallel proof implementation.

## Ownership rules

- JavaScript dependencies are locked by `apps/launcher/package-lock.json` and installed with `npm ci`.
- Rust dependencies are locked by Cargo and the Rust compiler version is repository-pinned.
- Maven and Gradle are repository-wrapper owned; global installations are not developer requirements.
- Native C++ build support is supplied by Visual Studio 2022 Build Tools with `Microsoft.VisualStudio.Workload.VCTools`.
- Runtime acceptance composes existing proof owners; the developer CLI is orchestration only.
- Python is not part of the canonical mandatory developer toolchain.
- End users must never need Node, npm, Rust, Cargo, Maven, Gradle, Python, Git, or MSVC to run an installed LazyBuilder build.

See `docs/INTEGRATION.md` and `docs/VALIDATION.md` before changing the production build pipeline.
