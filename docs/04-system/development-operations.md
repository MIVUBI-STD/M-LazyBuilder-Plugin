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
verify <paper|fabric|launcher>
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
├── scripts/verify/          targeted/local acceptance and artifact verification
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
→ DEV.cmd verify <scope> for the smallest stable source boundary
→ DEV.cmd build        when integrated packaging is material
→ DEV.cmd test         when runtime/installer behavior is material
→ commit
```

Do not run full repository CI on every `Local` commit merely for reassurance.

## Targeted verification

`verify` gives developers and agents a stable bounded route without requiring them to remember Maven/Gradle/npm/Cargo command details.

Supported scopes are intentionally limited:

```text
DEV.cmd verify paper
→ repository Maven wrapper
→ shared/protocol + Paper plugin compile/tests

DEV.cmd verify fabric
→ tooling/windows-toolchain/scripts/verify/verify-fabric.ps1
→ Map Manager + Utility Manager + Performance Manager Gradle build/tests

DEV.cmd verify launcher
→ npm ci
→ apps/launcher package script verify:source
→ Svelte/Vite source verification + locked Cargo check/tests
```

These are **source verification boundaries**, not runtime acceptance and not packaging/release proof.

Do not add `verify all`; integrated `build`/`finalize-local` already owns that role. Do not create a subcommand for every individual plugin/mod unless a repeated independent workflow proves that boundary is stable and useful. The targeted router must delegate to existing native owners rather than become a second build DSL.

Reusable lane owners should also be consumed by CI where practical. The required three-manager Fabric sequence therefore has one reusable owner instead of separate copies in local build and CI, while Launcher source verification is owned by one package script consumed locally and remotely.

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

## Developer failure contract

The root orchestrator must stop at the **first failed operation** and present a compact actionable failure envelope:

```text
Operation
Exit code
Evidence
Recovery
```

The envelope is routing/diagnostic context only. Domain scripts remain owners of detailed errors and proof artifacts.

Canonical evidence locations include:

```text
setup/check      → toolchain.json + failed tool-check row / installer output
verify paper     → first failing Maven/shared/Paper module or test
verify fabric    → first failing required Fabric manager build/test
verify launcher  → first failing npm/Svelte/Vite/Cargo verification step
build            → first failing Maven/Gradle/npm/Cargo/Tauri step; dist/ exists only after successful publication
test             → .runtime-proof/ runtime evidence + dist/Local/ installer/package input
```

Rules:

- do not hide the child process output;
- do not replace the original non-zero exit code with a success/fallback path;
- recovery guidance points back to the canonical `DEV.cmd` surface or the first failing semantic owner;
- do not add a logging service, telemetry database, or second diagnostic runner merely to format failures;
- generated diagnostic evidence stays outside source ownership.

## CI policy

The full `Verify` workflow is an **independent final verification layer**, not the inner development loop.

Canonical triggers:

```text
push to Local       → no automatic full CI
workflow_dispatch   → full CI on demand
pull request        → full CI except supporting evidence/history-only changes
push to main        → full CI except supporting evidence/history-only changes
```

Path scoping may exclude only supporting evidence/history documents that cannot affect source, runtime, build, packaging, canonical policy, or Skills. Source, tooling, canonical docs, versioning, workflows, and Skills must not be broadly excluded from final verification.

Dedicated Launcher/Paper/visual workflows may exist for manual or pull-request evidence, but they must not become parallel repository-readiness authorities.

CI must build from repository source/lockfiles and must not trust developer-machine outputs.

The workflow may produce exact-commit artifacts and provenance. A green unrelated job is not proof for a changed behavior outside that job's boundary.

### CI supply-chain discipline

Verification workflows are part of the build supply chain and follow the same reproducibility standard as toolchains and lockfiles:

- external GitHub Actions are referenced by immutable full commit SHA, with the intended release/major retained only as a readable comment;
- workflow `GITHUB_TOKEN` permissions are explicit and least-privilege (`contents: read` for current verification/preview workflows);
- read-only checkout disables persisted repository credentials;
- every job declares an explicit timeout suited to that proof lane rather than inheriting GitHub's long default;
- caches are acceleration only and never become artifact/proof authority;
- focused visual workflows do not run on ordinary `Local` pushes;
- action upgrades are deliberate dependency changes: resolve the upstream release/tag to its reviewed commit SHA, update the pin, then verify the affected workflow contract.

Release-only capabilities such as OIDC credentials, signing, artifact attestations, deployment environments, or write permissions are introduced only when a real release/publish requirement exists. They do not belong in ordinary Local verification by default.

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
- stop at the first failed operation and show actionable evidence/recovery guidance;
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
Does failure output identify the failed operation, useful evidence, and recovery path?
Are generated outputs isolated from source?
Does local proof remain separate from CI and deployment proof?
Did it avoid adding a dependency solely to run other dependencies?
Is the change necessary for a repeated workflow rather than a one-off convenience?
```

If the answer is no, simplify before adding another operational layer.
