# Development and Deployment Operations

Canonical operational architecture for developing, verifying, packaging, and promoting LazyBuilder.

This document owns the **shape of the developer/deployment workflow**. Domain behavior remains owned by application/plugin/mod/protocol source and their canonical docs. Tool versions remain owned by `toolchain.json`.

## Design goals

LazyBuilder operational tooling must provide:

```text
one developer command surface
one toolchain manifest
one build path per artifact
one local acceptance path
one CI verification workflow
one promotion boundary
```

The goal is reproducible delivery with the fewest durable operational owners. Do not add a second task runner, build orchestrator, dependency manager, release state store, or duplicate verification framework without a proven requirement.

## Canonical command surface

Developer-facing operations start at repository root:

```text
DEV.cmd <command> [arguments]
```

`DEV.cmd` is a Windows compatibility/convenience shim only. Canonical command routing is:

```text
tooling/windows-toolchain/dev.ps1
```

Supported commands:

```text
setup
check
build
test
update
finalize-local
```

`DEV.cmd` is the only root-level developer operation entrypoint. Historical setup/check/build/test/update aliases were removed after consolidation. New developer operations should normally become subcommands of `dev.ps1`, not new root scripts.

## Operational layers

```text
Developer Interface
DEV.cmd
  ↓
Canonical Orchestrator
tooling/windows-toolchain/dev.ps1
  ↓
Domain Operations
├── scripts/bootstrap/       environment/bootstrap validation and repair
├── scripts/build/           build preflight
├── scripts/wrappers/        repository-owned Maven/Gradle execution
├── scripts/verify/          local acceptance and artifact verification
├── scripts/distribution/    installer/package verification
├── apps/launcher/           Launcher application build owner
└── scripts/                 repository/runtime proof utilities
```

The orchestrator delegates; it does not reimplement Maven, Gradle, Tauri, Paper runtime, installer, or verification semantics.

## Toolchain authority

`toolchain.json` is the machine-readable authority for supported developer tool versions and policies.

Rules:

- Java/Node major policy and exact Rust toolchain come from repository-owned configuration;
- Java build tools use repository wrappers, not global Maven/Gradle installations;
- frontend dependencies use the committed lockfile and `npm ci`;
- Rust dependencies use Cargo lockfiles and the pinned compiler;
- Windows native build prerequisites are validated before expensive build work;
- downloaded wrapper distributions require checksum verification;
- end-user runtime must never depend on the developer toolchain.

Do not duplicate version numbers in multiple bootstrap scripts when they can be read from the canonical manifest or native lockfiles.

## Development loop

Normal development should be cheap and local:

```text
edit
→ DEV.cmd check        when environment/repository readiness is material
→ targeted module test/build during implementation
→ DEV.cmd build        when integrated packaging is material
→ DEV.cmd test         when runtime/installer behavior is material
→ commit
```

Do not run full repository CI on every `Local` commit merely for reassurance.

## Local finalization

Before requesting final CI for a coherent development phase:

```text
DEV.cmd finalize-local
```

This performs the canonical sequence:

```text
check
→ build
→ local acceptance
```

Each operation executes in an isolated child process so an internal script's `exit` behavior cannot prematurely terminate orchestration or hide later stages.

A successful local finalization proves only what the local execution context actually exercised. It does not substitute for independent CI or release promotion proof.

## CI policy

The full `Verify` workflow is an **independent final verification layer**, not the inner development loop.

Canonical triggers:

```text
push to Local       → no automatic full CI
workflow_dispatch   → full CI on demand
pull request        → full CI
push to main        → full CI
```

Dedicated Launcher/Paper/visual workflows may exist for manual or pull-request evidence, but they must not become parallel repository-readiness authorities.

CI must build from repository source/lockfiles and must not trust developer-machine outputs.

The workflow may produce exact-commit artifacts and provenance. A green unrelated job is not proof for a changed behavior outside that job's boundary.

## Deployment / promotion boundary

Branch semantics:

```text
Local = active development and Local-PC validation authority
main  = stable/release authority
```

Promotion to `main` requires a deliberate final gate. Do not use ordinary development pushes as deployment events.

Minimum promotion evidence is determined by changed behavior, but the normal integrated path is:

```text
current Local HEAD
→ local finalization where applicable
→ full final CI
→ required Local-PC/native acceptance
→ final source/proof audit
→ explicit promotion decision
→ main
```

Release publication, signing, update-channel changes, or external deployment remain separate from source promotion and require their own explicit authority. Do not hide release side effects inside normal build/test commands.

## Artifact model

Artifacts must be traceable to the exact source revision that produced them.

Where an integrated distributable is produced, record at minimum:

```text
repository
branch/ref
commit SHA
build/CI identity when applicable
artifact file name
artifact digest
verification claims actually performed
```

Do not label an artifact `verified`, `runtime-ready`, or `release` unless the matching proof has actually run.

Canonical Local-channel distributables belong under:

```text
dist/Local/
```

Do not create a second Local package directory or alternate installer naming convention for the same channel. Compile-only diagnostic outputs, if retained, must be clearly separated from runtime-ready acceptance artifacts and must not masquerade as a valid Local candidate.

## Output and state locations

Keep generated state out of source ownership:

```text
dist/Local/        canonical Local-channel distributable output
.runtime-proof/    disposable runtime proof state and logs
build/target dirs  language-native intermediate outputs
LocalAppData       reusable downloaded build-tool caches
```

Generated outputs and caches must not become product configuration authorities.

## Failure and recovery rules

Operational scripts must:

- fail fast on missing pinned prerequisites before expensive work;
- propagate non-zero exit codes accurately;
- isolate child operations when called by the root orchestrator;
- avoid partial success messages after a failed downstream operation;
- keep destructive installer/update actions explicit;
- use deterministic/recoverable paths for temporary build state;
- avoid silently falling back to a different global tool version;
- distinguish source/build proof from live/runtime proof.

## What not to add by default

Do not add these merely for perceived enterprise maturity:

```text
Make / just / Taskfile as a second task runner
another package manager solely for orchestration
a build database
an operational state service
a second CI workflow covering the same integrated contract
global Maven or Gradle requirements
a second installer/package path
a second runtime smoke framework
parallel release scripts with overlapping authority
```

A new operational system is justified only when the current canonical owner cannot satisfy a repeated requirement cleanly.

## Repository structure contract

```text
root
├── DEV.cmd                         sole developer-operation entry shim
├── toolchain.json                  toolchain policy authority
├── tooling/windows-toolchain/      Windows developer/distribution control plane
├── scripts/                        repository/runtime proof utilities
├── apps/                           shipped desktop application source
├── plugins/                        shipped Paper plugin source
├── mods/                           shipped Fabric source
├── shared/                         neutral shared contracts
└── .github/workflows/              independent CI/release automation
```

The root stays small. New developer commands should normally become subcommands of the existing orchestrator, not new root scripts.

## Completion test for operational changes

Before accepting a tooling change, answer:

```text
Did it preserve one canonical command surface?
Did it preserve one tool/version authority?
Did it reuse existing build/test owners rather than reimplementing them?
Can automation consume it without interactive pauses?
Are exit codes reliable?
Are generated outputs isolated from source?
Does local proof remain separate from CI and deployment proof?
Did it avoid adding a dependency solely to run other dependencies?
Is the change necessary for a repeated workflow rather than a one-off convenience?
```

If the answer is no, simplify before adding another operational layer.
