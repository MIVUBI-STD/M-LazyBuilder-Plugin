# LazyBuilder Windows Toolchain

Canonical Windows bootstrap/build/verification/distribution control plane for LazyBuilder.

## Authority

```text
toolchain.json                       supported toolchain/version policy
DEV.cmd                              root compatibility/convenience shim
dev.ps1                              canonical developer command router
scripts/bootstrap/                   environment validation/repair
scripts/build/                       build preflight
scripts/wrappers/                    repository-owned Maven/Gradle execution
scripts/verify/                      local acceptance/artifact verification
scripts/distribution/                package/installer verification
```

Do not create another task runner or root-level developer workflow when a subcommand can extend the existing command surface.

## Canonical usage

From repository root:

```text
DEV.cmd setup
DEV.cmd check
DEV.cmd build
DEV.cmd test
DEV.cmd update
DEV.cmd finalize-local
```

`DEV.cmd` contains no development semantics. It exists so Windows users and automation have a predictable entry shim that can invoke the canonical PowerShell orchestrator without requiring a machine-wide execution-policy change.

`dev.ps1` delegates to the existing operation owners and runs them in isolated child PowerShell processes so their exit behavior and exit codes remain bounded and composable.

Arguments after `setup`, `check`, `build`, `test`, or `update` are forwarded to the owning operation. `finalize-local` intentionally has no passthrough flags because it is the canonical integrated local gate.

## Fresh clone

```text
DEV.cmd setup
→ DEV.cmd check
→ development
```

Use `DEV.cmd build` when integrated package correctness matters and `DEV.cmd test` when runtime/installer behavior matters.

Before requesting the final CI pass for a coherent development phase:

```text
DEV.cmd finalize-local
```

which performs:

```text
check
→ build
→ local acceptance
```

## Root-entrypoint rule

`DEV.cmd` is the only developer-operation entrypoint kept at repository root. Historical command aliases were removed after consolidation. New commands must normally be added as `dev.ps1` subcommands rather than new root scripts.

## Baseline

- Java: Eclipse Temurin 21 LTS
- Node.js: 24.x LTS
- Maven: repository wrapper target 3.9.16
- Gradle: repository wrapper baseline 8.12
- Rust: exact toolchain pin from repository configuration
- Tauri: 2.x resolved through lockfiles
- WebView2: Evergreen Runtime for installed/end-user runtime
- Native build: Visual Studio Build Tools + Desktop development with C++
- Python: not a mandatory developer build dependency

## Reproducibility rules

- `toolchain.json` owns supported tool policy; scripts read it rather than maintaining shadow version lists.
- Java build tools are wrapper-owned; global Maven/Gradle installations are not required.
- wrapper downloads are checksum verified and cached below LazyBuilder-owned LocalAppData paths.
- frontend dependencies use `npm ci` and the committed lockfile.
- Rust uses the committed dependency lock plus repository-pinned compiler.
- expensive build work starts only after prerequisite validation.
- generated artifacts, runtime proofs, and caches remain outside source authority.
- installed/end-user LazyBuilder must not depend on Node, Rust, Maven, Gradle, Git, Python, or MSVC.

## CI and deployment boundary

Local development and CI are deliberately separate:

```text
Local commits       → no automatic full CI
workflow dispatch   → full final CI on demand
pull request        → full CI
push to main        → full CI
```

Local proof does not replace CI; CI does not replace target-machine/runtime acceptance. Promotion and release side effects remain explicit operations, never hidden inside ordinary build/test commands.

See `docs/04-system/development-operations.md` for the durable architecture contract and `docs/05-operations/` for current validation state.
