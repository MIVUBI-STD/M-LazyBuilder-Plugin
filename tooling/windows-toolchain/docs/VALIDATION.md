# Windows Validation Contract

Canonical validation expectations for the LazyBuilder Windows developer and distribution toolchain.

Operational routing is owned by [`../../../docs/04-system/development-operations.md`](../../../docs/04-system/development-operations.md). This file defines Windows-specific acceptance boundaries only; it is not a second workflow.

## Gate A — developer environment

From a supported Windows 10/11 x64 machine:

```text
DEV.cmd setup
→ DEV.cmd check
```

Required foundations:

- Java 21 for runtime-ready Paper builds;
- Node 24.x LTS + npm;
- exact repository Rust toolchain;
- Visual Studio Build Tools with Desktop development with C++;
- Git for source workflow;
- WebView2 where required by the installed Launcher runtime.

Global Maven, global Gradle, and Python are not canonical developer requirements. Maven and Gradle are repository-wrapper owned.

## Gate B — integrated local build

```text
DEV.cmd build
```

A normal runtime-ready build must prove or execute the canonical build owners:

```text
repository preflight
→ Maven wrapper verify
→ required Fabric wrapper builds
→ client artifact verification
→ npm ci
→ Svelte typecheck
→ cargo check/test with locked dependencies
→ Tauri/NSIS build
→ canonical Local package publication
```

Runtime-ready output belongs under:

```text
dist/Local/
```

Compile-only diagnostics, when explicitly requested, belong under `dist/CompileOnly/` and are never runtime acceptance candidates.

## Gate C — local runtime acceptance

```text
DEV.cmd test
```

Default acceptance composes the existing proof owners:

```text
Paper runtime lifecycle proof
→ Paper restart/persistence proof
→ installed Launcher clean-PATH smoke
```

This is local-machine evidence only. It does not replace independent GitHub verification.

## Gate D — local finalization

Before requesting final remote CI for a coherent development phase:

```text
DEV.cmd finalize-local
```

which executes:

```text
check
→ build
→ test
```

Each operation is isolated by the canonical orchestrator so exit codes remain reliable.

## Gate E — independent remote verification

The integrated GitHub `Verify` workflow is the final remote build/package authority for the exact revision under review.

Normal triggers:

```text
manual workflow dispatch
pull request checkpoint
push to main
```

Ordinary pushes to `Local` do not run the full CI pipeline.

Targeted Launcher, Paper runtime, or visual-proof workflows are supplementary evidence/debug lanes, not parallel readiness authorities.

## Gate F — target-machine acceptance and promotion

A green local build or remote CI run does not by itself authorize promotion.

Normal progression:

```text
current Local revision
→ local finalization where applicable
→ integrated Verify
→ required target-PC/native acceptance
→ final source/proof audit
→ explicit promotion decision
→ main
```

Release publication/signing/update-channel operations remain separate explicit actions.

## Reproducibility contract

Two builds from the same revision must resolve the same repository-controlled inputs:

- Maven wrapper version;
- Gradle wrapper version;
- Node major policy;
- exact Rust toolchain;
- npm lockfile;
- Cargo lockfile;
- first-party runtime component versions.

Artifact provenance must identify the producing revision and digest where the package owner supports it.

## Upgrade discipline

Major tool upgrades are explicit migration work, never incidental bootstrap behavior:

- Java LTS change;
- Node LTS major change;
- Gradle major change;
- Rust toolchain pin change;
- Tauri major change.

Security/patch updates inside an approved policy boundary still require matching build validation before becoming the new baseline.
