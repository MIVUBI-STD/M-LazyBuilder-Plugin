# Local PC Remediation — 15 September 2026

This note is the implementation authority for defects reproduced during installed Local PC validation on branch `Local`.

It complements `local-pc-testing-notes.md`; when an older observation conflicts with current source, this remediation note and current source take precedence.

## Current gate

```text
DO NOT start another Local PC acceptance pass yet.
```

The current phase is source remediation, synchronization, regression coverage, and remote verification. Local PC acceptance only reopens after every reproducible source-side P0/P1 below is either implemented or explicitly proven to be an environment-only/non-source condition.

## Implemented in source

### 1. Windows runtime TEMP/TMP ownership

Observed failure:

```text
java.io.IOException: Unable to establish loopback connection
java.net.SocketException: Invalid argument: connect
```

The same Windows environment problem affected Gradle and Paper. Manual validation proved that a dedicated temporary directory removed the failure.

Canonical runtime policy:

```text
%LOCALAPPDATA%\LazyBuilder\temp
```

Launcher bootstrap creates that directory and assigns process-local `TEMP` and `TMP` before Tauri/runtime workers start. Paper inherits the same stable environment without changing global Windows variables.

Repository-owned Maven and Gradle wrappers now apply the same LazyBuilder-owned temp policy. They also set `java.io.tmpdir` explicitly. Gradle additionally uses a LazyBuilder-owned `GRADLE_USER_HOME` and defaults to `--no-daemon` unless the caller explicitly chooses daemon behavior. This prevents the developer build path and installed runtime path from drifting back to different Windows temp behavior.

### 2. Developer toolchain detection

The old bootstrap check used one generic first-line version parser. Local PC testing showed this could incorrectly report Java or Node as missing/wrong even when they were installed.

Current check uses tool-specific parsing:

```text
Java  -> java -version parser
Node  -> semver parser
npm   -> semver parser
Rust  -> rustc exact-version parser
Cargo -> cargo signature parser
Git   -> git version signature parser
```

This removes shell-output ordering as a source of false negatives.

### 3. Reconnect path hardening

Current source captures the attempted server at `ConnectScreen.connect(...)` HEAD and refreshes it again on JOIN.

Reconnect is now available through two UI paths:

```text
disconnect stays on DisconnectedScreen
→ Reconnect button injected there

disconnect returns directly to Multiplayer/Server List
→ MultiplayerScreen fallback Reconnect button
```

Utility Manager logs capture/injection/reconnect state and packaging regression coverage locks registration of `ConnectScreenMixin`, `DisconnectedScreenMixin`, and `MultiplayerScreenMixin`.

### 4. Conversion runtime bootstrap

World Manager already had checksum verification, compatibility probing, staged promotion, rollback storage, retained verified versions, and single-conversion bounds. The blocking policy was that the default update mode was `NOTIFY_ONLY`, so a fresh installation could discover a stable conversion runtime but would not install it.

The default is now:

```text
AUTOMATIC_STABLE
```

The runtime is still fail-closed:

```text
stable release only
→ GitHub asset SHA-256 required
→ download
→ checksum verify
→ CLI compatibility probe
→ stage candidate
→ atomic promote
→ rollback/discard on failure
```

This allows capability refresh to bootstrap verified Bedrock conversion support instead of leaving the UI permanently Java-only on a fresh installation.

### 5. Maven Shade packaging

World Manager and Utilities Manager now filter duplicate signature/license/manifest metadata and merge service descriptors deterministically. This addresses the overlap warnings caused by normal shaded dependency metadata without hiding application-class collisions.

### 6. Gradle/Loom stale-process mitigation

Repository Gradle execution now defaults to no-daemon and uses a LazyBuilder-owned Gradle user home. Combined with the dedicated temp directory, interrupted builds are less likely to leave global/shared daemon state or Loom locks affecting later builds.

## Source-side review still required before Local PC testing

The following must be resolved through source review/remote verification before another target-machine pass:

- confirm Paper crash/restart state reconciliation remains correct after removal of the loopback crash root cause;
- run complete Utility Manager compile/tests with all mixins, including the new Multiplayer reconnect fallback;
- verify native Java import/export contracts still pass after conversion policy change;
- verify conversion runtime tests cover bootstrap retry, checksum rejection, compatibility probe, promotion and rollback;
- verify shaded Paper artifacts contain only intended classes/resources;
- verify canonical installer packaging includes matching tested Paper/Fabric artifacts from the same `Local` revision;
- perform a final repository-wide source audit for duplicated ownership, stale documentation, or transitional dependencies that contradict the current product boundary.

## Source-of-truth rule

Do not reintroduce a second Local PC workaround script or a parallel launcher/build path.

```text
Launcher runtime
→ LazyBuilder-owned runtime environment
→ Server Manager / child runtime inheritance

Developer wrappers
→ same LazyBuilder-owned temp policy
→ repository-owned Maven / Gradle
```

Do not set global Windows `TEMP`, `TMP`, `PATH`, `JAVA_HOME`, `MAVEN_OPTS`, `GRADLE_OPTS`, or `GRADLE_USER_HOME` as part of LazyBuilder installation.

## Required completion order

```text
1. finish source remediation for reproduced defects
2. synchronize runtime/build/product documentation
3. add or strengthen regression coverage
4. run repository consistency verification
5. run Paper compile/tests
6. run Fabric compile/tests and packaged-JAR verification
7. run frontend/Rust checks/tests
8. run Paper runtime smoke proof
9. build canonical NSIS installer from the same tested artifacts
10. run installer smoke verification in CI
11. perform final source/repository audit
12. only then reopen Local PC acceptance testing
```

Promotion remains blocked while any P0/P1 source defect or synchronization contradiction is unresolved.
