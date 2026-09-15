# Current Operations

This directory owns current continuation and proof. Durable product and architecture rules live in their domain docs.

## Current authority

```text
Local = active development, build, LocalTest and distribution authority
```

All current implementation work stays on `Local`. Do not create a parallel build/distribution branch or a second packaging system.

Read current readiness from the latest `Verify` workflow for the exact current `Local` HEAD. Never reuse an old green workflow as proof for a newer commit.

## Canonical product boundary

```text
Desktop Runtime
├── Server Manager
├── Plugin Manager
└── Client Setup

Paper
├── World Manager
└── Utilities Manager

Required Fabric client suite
├── Map Manager
├── Utility Manager
└── Performance Manager
```

The Launcher package must be built from matching tested artifacts from the same source revision.

## Two machine roles

### Developer/build machine

Developer tooling is only for editing/building source:

```text
Windows 10/11 x64
Java 21 LTS
Node.js 24.x LTS + npm
Rust toolchain pinned by rust-toolchain.toml
Microsoft C++ Build Tools
Git for Windows
WebView2
```

Maven and Gradle are repository-owned through `mvnw.cmd` and `gradlew.bat`. They are downloaded into the LazyBuilder build-tool cache using the versions in `toolchain.json` and verified against official checksums. Global Maven/Gradle are not prerequisites. Python is not a canonical Launcher build dependency.

Developer entrypoints:

```text
CHECK-DEV.cmd
SETUP-DEV.cmd
BUILD-LAUNCHER.cmd
UPDATE-LAUNCHER.cmd
```

### LocalTest/end-user machine

A LocalTest or end-user machine must not need the developer toolchain.

It must not require:

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

The normal entrypoint is the installer produced by CI:

```text
LazyBuilder-Setup-Local.exe
```

LocalTest is installer-based. Do not use the raw Tauri executable as the normal LocalTest product.

## Canonical Local pipeline

Every accepted Local candidate follows one pipeline:

```text
Local commit
→ repository consistency checks
→ Maven wrapper: Paper/shared compile + tests
→ Gradle wrapper: all required Fabric managers
→ verify client JAR contents
→ Paper runtime smoke proof
→ Svelte checks/build
→ Rust check/tests
→ Tauri build
→ NSIS installer
→ canonical Local package
→ silent installer smoke test
→ GitHub Actions artifact
```

The distributed Local package is staged as:

```text
dist/Local/
├── LazyBuilder-Setup-Local.exe
├── build-info.json
├── SHA256SUMS.txt
└── README.txt
```

`LazyBuilder-Setup-Local.exe` is the only normal LocalTest executable. Raw `lazybuilder.exe` is a separate developer diagnostic artifact.

## Windows installation behavior

The Tauri NSIS package uses current-user installation and explicitly uses the WebView2 download bootstrapper in silent mode when WebView2 is required.

Expected user flow:

```text
LazyBuilder-Setup-Local.exe
→ prerequisite handling
→ install/update LazyBuilder
→ Windows uninstall registration
→ Launcher ready
```

The installer must not install developer compilers/build systems on the target PC.

## Managed Java runtime

Java for Paper is application-managed and feature-aware. LazyBuilder owns its Java 21 runtime below the user application-data area instead of changing global `PATH` or `JAVA_HOME`.

Canonical runtime location:

```text
%LOCALAPPDATA%\LazyBuilder\runtimes\java-21\
```

Managed Java provisioning must remain transactional:

```text
download
→ SHA-256 verify
→ staging extraction
→ validate java.exe / Java 21
→ atomic publish
→ rollback previous runtime on publish failure
```

A missing Java runtime must not prevent the desktop Launcher from opening. It is prepared when server functionality needs it.

## Proof hierarchy

```text
REMOTE_GITHUB
→ repository consistency
→ Paper compile/tests
→ all required Fabric builds
→ Launcher frontend/Rust checks/tests
→ Paper runtime smoke
→ Tauri/NSIS package
→ installer smoke test
→ canonical Local artifact

LOCAL_TEST_PC
→ installer launch/install/update behavior
→ Launcher first run
→ managed Java provisioning
→ real Modrinth integration
→ real server/client workflows

LIVE_SERVER
→ real Paper/Minecraft/Fabric/gameplay behavior
```

A green lower layer is not proof of a higher layer.

## LocalTest handoff

For normal testing, do not clone/build the source on the test machine. Use the latest successful `Verify` workflow for current `Local` HEAD and retrieve the `LazyBuilder-Local-<commit>` artifact. The package contains `LazyBuilder-Setup-Local.exe` plus provenance/checksum metadata.

If the repository is also present on the machine for inspection, that does not change runtime behavior: run the prebuilt installer artifact, not a locally compiled binary.

## Build provenance

Every canonical Local package records:

```text
product
channel=local
version
full commit SHA
short commit SHA
GitHub workflow run number
installer filename
SHA-256
Minecraft target
generation timestamp
```

Use `build-info.json` when reporting a LocalTest defect so the tested binary can be tied to one exact source revision.

## Update/data-safety rules

Install/update/repair work must preserve normal LazyBuilder user data, selected Modrinth profile, server workspaces and worlds. Server/world data must not live inside the application installation directory.

Do not change global Java, Maven, Gradle, Node, Rust or Python configuration on an end-user machine.

## LOCAL_TEST proof order

```text
1. confirm latest Verify workflow is green for exact Local HEAD
2. obtain LazyBuilder-Local-<commit> artifact
3. verify SHA256SUMS.txt when needed
4. run LazyBuilder-Setup-Local.exe
5. verify Windows installation/uninstall registration
6. open LazyBuilder
7. verify application data and first-run state
8. exercise managed Java provisioning through server functionality
9. select/sync a real Modrinth 1.21.4 Fabric profile
10. create/open/adopt a server workspace
11. start Paper 1.21.4
12. exercise client/server managers and persistence
```

## Defect handling

Only reproducible failures reopen source work. Record component, expected result, actual result, exact error, source commit/build-info, first wrong owner and smallest proposed fix. Fix only that owner first and rerun the smallest failing gate before the entire workflow.

## STOP condition

The Local distribution path is ready only when a clean Windows test machine without developer tools can install `LazyBuilder-Setup-Local.exe`, open LazyBuilder, provision required application runtime when needed, and perform accepted LocalTest workflows without invoking a terminal or building source.
