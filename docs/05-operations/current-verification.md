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

## Update policy

Do not hard-code a workflow run number or HEAD SHA in architecture documentation as the permanent current state. GitHub commit/workflow state is authoritative and changes as `Local` advances.
