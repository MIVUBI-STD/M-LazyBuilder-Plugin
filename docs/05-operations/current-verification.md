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
- live world lifecycle behavior;
- Fabric screen/input behavior inside Minecraft;
- Xaero runtime integration;
- real large-file transfers and converter output;
- gameplay behavior of Utilities features;
- restart/shutdown persistence on a live server.

Those remain `LOCAL_CODE` and `LIVE_SERVER` responsibilities.

## Local Windows handoff evidence

The current local runtime evidence was collected on:

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

The renamed profile still has a local compatibility junction at `1.21.4 Build (1)` pointing to `1.21.4 Testing`; this is not a portable repository assumption.

Confirmed local/live defects for continuation:

- native Map Preview does not yet match the intended Xaero-like presentation;
- World-Manager does not automatically adopt the active/default Paper world;
- current-world resolution and permission feedback are not yet an onboarding-safe flow;
- Map Preview action controls must be disabled until a managed-world response is available;
- Utilities-Manager displays the literal `${project.version}` in runtime metadata;
- desktop process ownership versus direct Paper launch still needs end-to-end runtime proof.

## Update policy

Do not hard-code a workflow run number or HEAD SHA in architecture documentation as the permanent current state. GitHub commit/workflow state is authoritative and changes as `Local` advances.
