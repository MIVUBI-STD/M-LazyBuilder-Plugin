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

## Canonical developer flow

From a fresh Windows clone, use one path:

```text
SETUP-DEV.cmd
→ CHECK-DEV.cmd
→ BUILD-LAUNCHER.cmd
→ TEST-LOCAL.cmd
```

`SETUP-DEV.cmd` is the canonical bootstrap entrypoint. It first validates the current machine, then repairs supported missing developer foundations when possible using `winget` and `rustup`. It does not install global Maven or Gradle because repository wrappers are authoritative.

Use:

```text
SETUP-DEV.cmd -CheckOnly
```

for a non-mutating bootstrap check. `CHECK-DEV.cmd` is always validation-only.

`BUILD-LAUNCHER.cmd` performs the runtime-ready local build path: Maven verification, required Fabric builds, client-artifact verification, exact frontend dependency install, Svelte typecheck, Rust check/tests, and Tauri/NSIS packaging. Its preflight fails early when required reproducibility files, pinned versions, or the MSVC C++ workload are missing.

`TEST-LOCAL.cmd` is the canonical local acceptance entrypoint. By default it reuses build outputs and runs:

```text
Paper runtime behavior proof
→ Paper restart/persistence proof
→ installed Launcher clean-PATH smoke
```

The script resolves and caches the current stable Paper 1.21.4 runtime under `.runtime-proof`, then delegates to the existing canonical runtime/restart/installer verifiers. It does not duplicate their proof logic.

Useful variants:

```text
TEST-LOCAL.cmd -Build          # run BUILD-LAUNCHER first, then acceptance
TEST-LOCAL.cmd -PaperOnly      # Paper proof lanes only
TEST-LOCAL.cmd -SkipRestart    # skip restart persistence proof
TEST-LOCAL.cmd -SkipInstaller  # skip installed Launcher smoke
```

`UPDATE-LAUNCHER.cmd` remains the installed-local-app update path and is not a replacement for developer bootstrap or acceptance.

## Ownership rules

- JavaScript dependencies are locked by `apps/launcher/package-lock.json` and installed with `npm ci`.
- Rust dependencies are locked by Cargo and the Rust compiler version is repository-pinned.
- Maven and Gradle are repository-wrapper owned; global installations are not developer requirements.
- Native C++ build support is supplied by Visual Studio 2022 Build Tools with `Microsoft.VisualStudio.Workload.VCTools`.
- Runtime acceptance composes existing proof owners; `TEST-LOCAL.cmd` is orchestration only.
- Python is not part of the canonical mandatory developer toolchain.
- End users must never need Node, npm, Rust, Cargo, Maven, Gradle, Python, Git, or MSVC to run an installed LazyBuilder build.

See `docs/INTEGRATION.md` and `docs/VALIDATION.md` before changing the production build pipeline.
