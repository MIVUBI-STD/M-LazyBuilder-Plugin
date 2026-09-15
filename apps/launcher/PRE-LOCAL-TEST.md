# LazyBuilder Launcher — Pre-Local Acceptance Gate

This document defines the **gate that must be satisfied before** another installed Local PC acceptance pass. It is not an instruction to start Local testing immediately.

Current remediation authority:

```text
docs/05-operations/local-pc-remediation-2026-09-15.md
```

## Gate status

```text
CLOSED while source remediation is active.
```

Before reopening target-machine acceptance, the exact current `Local` HEAD must pass:

```text
consistency
paper
paper-runtime-smoke
fabric
launcher-check
tauri-desktop
```

Applicable Paper-path revisions must also pass the dedicated `Paper Runtime Proof` workflow.

Launcher checks include:

```text
npm ci
npm run typecheck
npm run build:frontend
npm run prepare:icons
cargo check --locked --manifest-path src-tauri/Cargo.toml
cargo test --locked --manifest-path src-tauri/Cargo.toml
```

Remote CI is source/build/runtime-smoke/package proof. It is not a substitute for later installed-machine acceptance.

## Runtime-ready package gate

A normal V1 runtime-ready package must contain matching tested artifacts from the same source revision:

```text
src-tauri/resources/core/
├── World-Manager-0.1.0-SNAPSHOT.jar
└── Utilities-Manager-0.1.0-SNAPSHOT.jar

src-tauri/resources/client-mods/
├── lazybuilder-map-manager-0.1.0-SNAPSHOT.jar
├── lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar
└── lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar
```

The three Fabric managers are one required Client Setup suite. This must match:

```text
client-manager architecture lock
→ Fabric build workflow
→ artifact verifier
→ Launcher bundled resources
→ Client Setup sync transaction
```

`BUILD-LAUNCHER.cmd` owns the complete local developer packaging path:

```text
repository-owned Maven verify
→ build Map Manager through repository Gradle wrapper
→ build Utility Manager through repository Gradle wrapper
→ build Performance Manager through repository Gradle wrapper
→ verify all three client artifacts
→ stage matching Paper + Fabric JARs
→ npm ci
→ Svelte typecheck/build
→ cargo check/test
→ Tauri + NSIS package
```

The build must stop if any required runtime artifact is missing. Compile-only mode is never acceptance proof.

## Windows runtime gate

Before acceptance can reopen, source and CI must preserve one environment policy:

```text
%LOCALAPPDATA%\LazyBuilder\temp
```

Launcher/Paper and repository-owned Maven/Gradle flows must not depend on arbitrary inherited Windows TEMP/TMP behavior and must not mutate global user environment variables.

## Paper process-safety gate

Source/remote verification must retain:

- one managed Paper process per active workspace;
- PID + process-start-time identity markers;
- command-line/workspace verification before detached-process termination;
- stale-marker cleanup when a process no longer exists;
- safe stop/restart recovery for both `Stopping` and `Detached` states;
- global server-start coordination lock;
- live external-server adoption guard.

## Client Setup gate

Before later Local PC acceptance:

- Client Setup must own only LazyBuilder client JAR prefixes;
- all three required manager JARs must be staged before mutation;
- sync remains transactional with rollback;
- unrelated third-party Modrinth mods remain untouched;
- selected profile persistence remains outside server-workspace ownership;
- profile-in-use protection remains active.

## Conversion runtime gate

World Manager conversion bootstrap must remain:

```text
stable upstream release
→ exact CLI artifact
→ SHA-256 required
→ compatibility probe
→ staged candidate
→ atomic promotion
→ rollback/discard on failure
```

Current default policy is `AUTOMATIC_STABLE`; capability exposure remains based on verified runtime-supported formats rather than documentation assumptions.

## Reconnect gate

Utility Manager reconnect must retain both vanilla UI paths:

```text
DisconnectedScreen → Reconnect
MultiplayerScreen/server list fallback → Reconnect
```

The attempted server is captured before JOIN and confirmed again on JOIN. Mixin registration is required and covered by a packaging regression test.

## Final source-audit gate

Before this document can be used as an installed test checklist, verify there is no remaining contradiction across:

```text
README / operations authority
architecture locks
build scripts
GitHub workflows
artifact verifiers
Launcher runtime ownership
Paper/Fabric module ownership
conversion ownership
```

## Reopening decision

Only after all pre-local gates are green should the project explicitly move to:

```text
docs/05-operations/local-pc-validation-plan.md
```

Until then, do not use `BUILD-LAUNCHER.cmd`, installer deployment, or the target PC as a substitute for unresolved source-side remediation.
