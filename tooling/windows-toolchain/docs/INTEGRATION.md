# Integration Plan

This package is a deployment/toolchain hardening layer, not a second build system.

## Required integration

1. Treat repository-root `toolchain.json` as the canonical version policy.
2. Keep `.node-version` and `rust-toolchain.toml` at repository root.
3. Add Maven Wrapper and pin it to 3.9.16.
4. Add Gradle Wrapper and initially pin it to the current repository baseline, 8.12.
5. Update `apps/launcher/build-local.ps1` to call repository wrappers instead of global Maven/Gradle.
6. Replace `python scripts/verify_client_artifacts.py` with `tooling/windows-toolchain/scripts/verify/verify-client-artifacts.ps1`.
7. Remove Python from mandatory build checks.
8. Require Node 24.x, Java 21, and the exact repository Rust toolchain.
9. Keep `npm ci`, `package-lock.json`, `Cargo.lock`, and Tauri NSIS packaging.
10. End-user installer must not require Node, npm, Rust, Cargo, Maven, Gradle, Python, Git, or MSVC.

## Important version policy

- Do not use `Node 24+`; use `Node 24.x LTS`.
- Do not upgrade Gradle while hardening deployment. Test an upgrade separately.
- Do not float Rust on `stable`; production builds use the exact repository pin.
- Security patch updates within approved LTS majors are allowed after validation.

## Final developer flow

```text
clone
  -> CHECK-DEV / SETUP-DEV
  -> build preflight
  -> mvnw.cmd verify
  -> gradlew.bat builds
  -> npm ci
  -> Svelte checks
  -> cargo check/test --locked
  -> tauri build
  -> dist/LazyBuilder/LazyBuilder-Setup.exe
```
