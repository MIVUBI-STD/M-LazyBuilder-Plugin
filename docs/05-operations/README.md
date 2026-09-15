# Current Operations

This directory owns current continuation, verification, distribution proof, and Local PC acceptance routing.

## Current authority

```text
Local = active development, remediation, build, LocalTest and distribution authority
main  = stable / release authority
```

All current implementation work stays on `Local`. Do not create a parallel build/distribution branch or a second packaging system.

## Current phase

The 15 September 2026 installed Local PC pass exposed reproducible defects. The repository is therefore in **source remediation and synchronization**, not Local PC acceptance.

Current handoff:

[`local-pc-remediation-2026-09-15.md`](local-pc-remediation-2026-09-15.md)

Do not start another Local PC acceptance pass until:

```text
source remediation complete
→ product/build/runtime contracts synchronized
→ regression coverage updated
→ exact Local HEAD passes Verify
→ Paper runtime proof passes
→ canonical installer package passes CI smoke verification
→ final source audit finds no unresolved P0/P1 contradiction
```

The later target-machine procedure remains:

[`local-pc-validation-plan.md`](local-pc-validation-plan.md)

## Canonical product boundary

```text
Desktop Runtime
├── Server Manager
├── Plugin Manager
└── Client Setup

Paper
├── World Manager
└── Utilities Manager

V1 Fabric client suite
├── Map Manager
├── Utility Manager
└── Performance Manager

Shared Contracts
└── Protocol
```

The three Fabric managers are currently one tested/bundled Client Setup suite. This matches Launcher `Client Setup`, artifact verification, Gradle build orchestration, and CI packaging. Do not document Performance Manager as deferred while it remains a required runtime component.

The Launcher package must be built from matching tested artifacts from the same source revision.

## Two machine roles

### Developer/build machine

Developer tooling is only for editing, verification and packaging:

```text
Windows 10/11 x64
Java 21 LTS
Node.js 24.x + npm
Rust toolchain pinned by rust-toolchain.toml
Microsoft C++ Build Tools
Git for Windows
WebView2
```

Maven and Gradle are repository-owned through `mvnw.cmd` and `gradlew.bat`. They are downloaded into the LazyBuilder build-tool cache using versions in `toolchain.json` and verified against official checksums. Global Maven/Gradle are not prerequisites.

Repository wrappers must use the LazyBuilder-owned Windows runtime temp policy rather than relying on arbitrary inherited TEMP/TMP paths.

Developer entrypoints:

```text
CHECK-DEV.cmd
SETUP-DEV.cmd
BUILD-LAUNCHER.cmd
UPDATE-LAUNCHER.cmd
```

### LocalTest/end-user machine

A LocalTest/end-user machine must not require:

```text
Node.js / npm
Rust / Cargo
Maven
Gradle
Python
Git
Visual Studio Build Tools
source code
```

Normal installed entrypoint:

```text
LazyBuilder-Setup-Local.exe
```

LocalTest is installer-based. Raw `lazybuilder.exe` is only a developer diagnostic artifact.

## Canonical Local pipeline

Every accepted Local candidate follows one pipeline:

```text
Local commit
→ repository consistency checks
→ Maven: shared + Paper compile/tests
→ Gradle: Map + Utility + Performance client builds
→ client JAR content verification
→ Paper runtime smoke proof
→ Svelte checks/build
→ Rust check/tests
→ Tauri build
→ NSIS installer
→ canonical Local package
→ silent installer smoke verification
→ final source audit
→ explicit decision to reopen Local PC acceptance
```

Canonical distribution:

```text
dist/Local/
├── LazyBuilder-Setup-Local.exe
├── build-info.json
├── SHA256SUMS.txt
└── README.txt
```

## Windows runtime environment

LazyBuilder owns process-local temporary storage at:

```text
%LOCALAPPDATA%\LazyBuilder\temp
```

Launcher/Paper and repository-owned Maven/Gradle paths use this stable temp root to avoid the Java loopback/pipe failure reproduced on the Local PC.

Do not mutate the user's global `TEMP`, `TMP`, `PATH`, `JAVA_HOME`, `MAVEN_OPTS`, `GRADLE_OPTS`, or `GRADLE_USER_HOME`.

## Managed Java runtime

Paper uses the application-managed Java 21 runtime:

```text
%LOCALAPPDATA%\LazyBuilder\runtimes\java-21\
```

Provisioning remains transactional:

```text
download
→ SHA-256 verify
→ staging extraction
→ validate Java 21
→ atomic publish
→ rollback on publish failure
```

A missing Paper Java runtime must not prevent the desktop Launcher from opening.

## Conversion runtime

World Manager owns the optional-on-demand conversion dependency. Current default policy is automatic stable bootstrap with fail-closed verification:

```text
stable release metadata
→ exact CLI artifact
→ SHA-256 verification
→ compatibility probe
→ candidate staging
→ atomic promotion
→ rollback/discard on failure
```

The client must only expose conversion targets that the verified runtime reports as supported.

## Proof hierarchy

```text
SOURCE / REMOTE
→ repository consistency
→ Paper tests
→ Fabric tests/builds
→ packaged JAR verification
→ frontend/Rust checks/tests
→ Paper runtime smoke proof
→ Tauri/NSIS build
→ installer smoke verification
→ final source audit

LOCAL_TEST_PC (later, only after source gate opens)
→ clean install/update
→ Launcher first run
→ managed Java + Paper
→ Plugin/World/Utilities Managers
→ Modrinth Client Setup
→ all required Fabric managers
→ reconnect/restart/recovery
→ import/export/conversion
→ storage/large-world behavior
```

A green lower layer is not proof of a higher layer.

## Build provenance

Every canonical Local package records:

```text
product
channel=local
version
full commit SHA
short commit SHA
workflow run number
installer filename
SHA-256
Minecraft target
generation timestamp
```

## Data-safety rules

Install/update/repair must preserve normal LazyBuilder user data, selected Modrinth profile, server workspaces and worlds. Server/world data must remain outside the application installation directory.

## Reopening Local PC acceptance

Local PC acceptance may resume only when the current remediation handoff has no unresolved source-side P0/P1 issue and the exact candidate revision has complete remote proof.

When that gate is explicitly opened, follow `local-pc-validation-plan.md`; do not invent another checklist.
