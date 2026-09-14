# Current Verification Authority

This file defines how to determine the current LazyBuilder repository state.

## Source authority

```text
Local = active development authority
main  = stable/release authority
```

Never infer current readiness from an older completion report, commit note, or successful workflow attached to a previous SHA.

## Current proof rule

For any source/readiness decision, use this order:

```text
1. current Local HEAD
2. latest Verify workflow for that exact HEAD SHA
3. component-specific test/build output from that workflow
4. LOCAL_CODE evidence when GitHub CI cannot prove the behavior
5. LIVE_SERVER evidence for real Paper/Minecraft behavior
```

A successful workflow for an older SHA is historical evidence only. A skipped, cancelled, queued, in-progress, or failed required job is not a pass for the current HEAD.

## Historical reports

`remote-github-complete.md` and older completion records are immutable snapshots of the repository state they describe. They are useful for history and handoff context, but they are not current verification authority after `Local` advances.

## Required remote gate

The current `Verify` workflow is the minimum remote gate and must pass for the exact candidate SHA:

```text
consistency     canonical version + repository contract checks
paper           Maven compile + automated tests
fabric          pinned Gradle 8.12 client build
tauri-desktop   frontend typecheck/build + Rust check/test + Windows installer build
```

Only after all required jobs succeed may the SHA be described as `REMOTE_GITHUB green`.

## Runtime proof boundary

A green GitHub workflow still does not prove:

- installed Windows desktop behavior;
- Java discovery on the target machine;
- real Paper start/stop/restart behavior;
- live managed-world lifecycle behavior;
- Fabric screen/input behavior inside Minecraft;
- fullscreen map interaction quality across GUI scales;
- native file dialogs;
- real large-file transfers and converter output;
- gameplay behavior of Utilities features;
- restart/shutdown persistence on a live server.

Those remain `LOCAL_CODE` and `LIVE_SERVER` responsibilities.

## Current World Manager source state

The current `Local` source contract has advanced beyond the older local defect list. The following are now source-implemented and require fresh proof rather than being treated as known-unfixed architecture gaps:

```text
managed-world adoption / current-world synchronization
automatic runtime load + idle unload
ACTIVE / ARCHIVED durable lifecycle only
Duplicate terminology
permission-aware World Manager UI
Map Action V2 current-world push / clear
World Control V3 capability-aware list
unified Import / Export workspace
client + server transfer storage preflight
heavy-operation reconnect completion recovery
editable chunk-aligned Map Export Area selection
server-side full-chunk canonicalization
```

The area-selection surface is first-party LazyBuilder UI. Its chunk grid, region grid, selection rectangle, move/resize handles, coordinate HUD, snapping, and review flow do not depend on an external converter UI/runtime. External conversion software remains isolated behind the backend conversion adapter only when an actual format conversion is required.

## Current proof status

The branch is still **source-level implementation** until a fresh validation pass is run for the exact current `Local` HEAD.

Do not claim current `Local` is compile-validated or runtime-validated yet.

The final validation sequence for the current World Manager pass is:

```text
1. current Local compile/test
2. Paper + Fabric protocol compatibility (World V3 / Map V2)
3. M → Map → Worlds navigation across GUI scales
4. current-world push through LazyBuilder teleport, command, portal and unmanaged world
5. Pinned / Recent / Search / Archived behavior
6. permission-limited UI and server authorization
7. automatic load + idle unload
8. occupied-world safeguards
9. Duplicate / Archive / Restore / Delete
10. whole-world Export native fast path
11. capability-driven conversion targets
12. Map Export Area editable selection
    - chunk grid visibility by zoom
    - region grid hierarchy
    - 8 move/resize handles
    - drag-inside move
    - drag-outside pan
    - chunk snapping including negative coordinates
    - chunk-first HUD + block-range confirmation
    - Edit Selection round trip
13. area export server full-chunk canonicalization
14. Import .zip / .mcworld
15. native file dialogs, large transfer, checksum and disk-space failures
16. disconnect/reconnect
    - whole-world heavy completion recovery
    - selected-area export completion recovery
    - transfer-session cleanup/restart behavior
17. shutdown/restart cleanup and persistence
```

## Local Windows handoff evidence

Historical local runtime work used these paths:

```text
Repository: D:\Work\AI Stuff\LazyBuilder
Modrinth App: C:\Users\Administrator\AppData\Roaming\ModrinthApp
Minecraft profile: C:\Users\Administrator\AppData\Roaming\ModrinthApp\profiles\1.21.4 Testing
Client mod: <profile>\mods\lazybuilder-client-0.1.0-SNAPSHOT.jar
Paper workspace: D:\Work\Minecraft\Java-Version\Java Build Server\Test\Test
Paper endpoint: 127.0.0.1:25565
Managed Java: C:\Users\Administrator\AppData\Local\LazyBuilder\runtimes\java-21\bin\java.exe
TEMP/TMP: C:\Temp\LazyBuilderGradleTemp
```

These paths are handoff context only and must not become portable repository assumptions.

## Update policy

Do not hard-code a workflow run number or HEAD SHA in architecture documentation as the permanent current state. GitHub commit/workflow state is authoritative and changes as `Local` advances.

Fix reproducible validation defects at the smallest wrong owner. Do not reopen duplicated architecture, restore legacy runtime-state product controls, or introduce a second map/export implementation merely to work around a local bug.
