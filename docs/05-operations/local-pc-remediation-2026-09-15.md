# Local PC Remediation — 15 September 2026

This note is the implementation handoff for defects reproduced during installed Local PC validation on branch `Local`.

It complements `local-pc-testing-notes.md`; when an older observation conflicts with current source, this remediation note and current source take precedence.

## Implemented in source

### 1. Windows runtime TEMP/TMP ownership

Observed failure:

```text
java.io.IOException: Unable to establish loopback connection
java.net.SocketException: Invalid argument: connect
```

The same Windows environment problem affected Gradle and Paper. Manual validation proved that a dedicated temporary directory removed the failure.

Current policy:

```text
%LOCALAPPDATA%\LazyBuilder\temp
```

Launcher bootstrap now creates that directory and assigns process-local `TEMP` and `TMP` before Tauri/runtime workers start. Paper and other child processes inherit the stable environment without changing the user's global Windows variables.

Acceptance proof required on the next installed build:

1. launch LazyBuilder normally, with no manual PowerShell TEMP/TMP override;
2. confirm launcher log records `LazyBuilder runtime TEMP/TMP`;
3. start Paper twice, including one full stop/restart cycle;
4. confirm no loopback/`Invalid argument: connect` failure appears.

### 2. Reconnect diagnosis is now observable

Earlier notes listed failure to capture the server only through JOIN as a possible cause. Current source already captures the attempted server at `ConnectScreen.connect(...)` HEAD and refreshes it again on JOIN.

The remaining diagnosis boundary is therefore runtime loading/injection/screen flow.

Utility Manager now logs:

```text
Utility Manager loaded; reconnect=...
Captured reconnect target <address>
DisconnectedScreen utility injection active; reconnectEnabled=..., reconnectAvailable=...
Reconnect button added for <address>
Reconnect requested for <address>
```

A packaging regression test also locks the required registration of `ConnectScreenMixin` and `DisconnectedScreenMixin`.

Next reproduction must use the Minecraft client log instead of inferring behavior only from UI.

Decision tree:

```text
no "Utility Manager loaded"
→ wrong/missing JAR or entrypoint failure

loaded, but no "Captured reconnect target"
→ ConnectScreen path/mixin capture problem

captured, but no "DisconnectedScreen utility injection active"
→ disconnect follows a different screen path or mixin is not executing

injection active + reconnectAvailable=false
→ reconnect state was lost/overwritten

"Reconnect button added" but button not visible
→ layout/render interaction problem
```

## Still open / must be proven

The following are not declared fixed until target-machine proof exists:

- Paper crash/restart reconciliation after an unexpected failure;
- complete Utility Manager runtime matrix: keep draft, extended history, F3+T notice, contextual screenshots, borderless window;
- native Java import/export round trip on installed runtime;
- Bedrock conversion runtime provisioning and compatibility probe;
- Maven Shade overlap classification;
- Gradle/Loom stale-process behavior during developer builds.

## Source-of-truth rule

Do not reintroduce a second Local PC workaround script or a parallel launcher path.

```text
Launcher bootstrap
→ one process-local runtime environment policy
→ Server Manager / child runtime inheritance
```

Do not set global Windows `TEMP`, `TMP`, `PATH`, or `JAVA_HOME` as part of the fix.

## Next validation order

```text
1. build canonical Local installer from current HEAD
2. install/update on Local PC
3. verify launcher-owned TEMP/TMP in launcher.log
4. Paper start → stop → start
5. forced/unexpected Paper failure → recovery/restart
6. Minecraft client launch with synced Utility Manager JAR
7. reproduce disconnect and capture Utility Manager log sequence
8. run remaining Utility Manager feature matrix
9. Java import/export round trip
10. only then continue Bedrock conversion runtime validation
```

Promotion remains blocked while any P0/P1 target-machine defect is unresolved.
