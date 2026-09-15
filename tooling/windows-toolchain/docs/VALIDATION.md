# Validation Gates

Do not call the deployment package production-ready until all gates pass on branch `Local`.

## Gate A — clean developer machine

Required foundations:
- Windows 10/11 x64
- Java 21
- Node 24.x
- exact repository Rust toolchain
- MSVC Build Tools
- WebView2
- Git

Must not require global Maven, global Gradle, or Python.

Expected:
- Maven wrapper verification succeeds.
- Gradle wrapper builds every required LazyBuilder client mod.
- `npm ci` succeeds from lockfile.
- Svelte typecheck succeeds.
- `cargo check --locked` succeeds.
- `cargo test --locked` succeeds.
- Tauri NSIS build succeeds.

## Gate B — clean end-user machine

The machine should not need Node, Rust, Maven, Gradle, Python, Git, or MSVC.

Expected:
- `LazyBuilder-Setup.exe` installs.
- Launcher opens.
- Bundled runtime resources are present.
- Java is required only for Paper/server functionality.
- WebView2 is detected or bootstrapped when needed.

## Gate C — reproducibility

Two builds from the same commit use the same Maven wrapper, Gradle wrapper, Node major, Rust toolchain, npm lockfile, Cargo lockfile, and first-party runtime component versions.

## Gate D — upgrade discipline

Major upgrades are never automatic:
- Java 21 -> newer LTS: migration task.
- Node 24 -> newer LTS: migration task.
- Gradle 8 -> 9: migration task.
- Tauri 2 -> future major: migration task.
