# Current Verification Authority

This file defines how to determine the current LazyBuilder repository state.

## Source authority

```text
Local = active development authority
main  = stable/release authority
```

Never infer current readiness from an older completion report, commit note, or successful workflow attached to a superseded server/plugin revision.

## Verification layers

LazyBuilder now has two distinct remote verification authorities:

```text
Verify
→ repository consistency
→ Paper/shared unit/build verification
→ Fabric build/artifact verification
→ Launcher frontend/Rust verification
→ Windows/Tauri packaging
→ exact-head Paper runtime smoke used by packaging

Paper Runtime Proof
→ path-scoped server/plugin/protocol authority
→ real stable Paper 1.21.4 on Windows
→ full managed-world lifecycle proof
→ clean restart + registry/filesystem persistence proof
```

The separation is intentional. `Verify` follows every repository change. `Paper Runtime Proof` follows only changes that can affect the Paper runtime path, so unrelated Launcher development does not repeatedly invalidate or cancel expensive server proof.

## Required `Verify` gate

The current `Verify` workflow contains six jobs:

```text
consistency          version/repository/scope contracts
paper                full Maven Paper/shared compile + tests + core artifacts
paper-runtime-smoke  exact-head Paper boot + lifecycle harness used by packaging
fabric               Fabric manager builds + client artifacts
launcher-check       Svelte typecheck/build + Rust check/test on Windows
tauri-desktop        package tested core/client artifacts into Windows NSIS build
```

### Transitional client-suite state

The intended V1 product scope is **Map Manager + Utility Manager**. Performance Manager is deferred research source and must not gain new dependencies, runtime ownership, or feature scope without measured client evidence and an explicit product decision.

Current `Local` source still contains Launcher/CI references that may build or bundle Performance Manager while the active Launcher/installer consolidation is in progress. That is a transitional implementation fact, not proof that Performance Manager has been promoted back into required V1 scope.

Do not independently remove, rewire, or expand those Launcher/client-packaging paths while that active consolidation is being worked elsewhere. Resolve the Launcher source and packaging contract together when that work reaches its owner.

## Dedicated Paper runtime gate

`.github/workflows/paper-runtime-proof.yml` is the deeper Paper-side runtime authority. It is triggered by changes to:

```text
root Maven ownership
plugins/world-manager/**
plugins/utilities-manager/**
shared/protocol/**
Paper runtime-proof scripts/workflow
```

A green run proves, on a real disposable Paper 1.21.4 server:

```text
Java 21 / Paper boot
→ World-Manager enable
→ Utilities-Manager enable
→ local-control authentication/status
→ managed-world listing
→ Create World
→ settings update/readback
→ Archive / Restore
→ Duplicate
→ Backup
→ native Java Export
→ authenticated upload
→ Import
→ Delete
→ clean shutdown
→ restart
→ registry reload
→ persisted WorldId/settings resolution
→ post-restart deletion
```

Runtime evidence is uploaded as a short-lived workflow artifact for inspection.

## Determining `REMOTE_GITHUB` status

Use the current `Local` HEAD and the latest relevant workflows.

For repository-wide/package readiness:

```text
all required Verify jobs for current Local HEAD succeed
→ repository/package REMOTE_GITHUB green
```

For Paper runtime readiness:

```text
latest Paper/plugin/protocol/runtime-proof revision in current Local history
has a successful Paper Runtime Proof
→ Paper runtime REMOTE_GITHUB green
```

A later commit that changes only Launcher/docs and is outside the Paper Runtime Proof path scope does not invalidate already-proven Paper binaries/source. Any later commit touching a scoped Paper path must produce a new successful Paper Runtime Proof before Paper runtime readiness is claimed again.

## V1 runtime scope

Target product scope:

```text
Desktop
├── Server Manager
├── Plugin Manager
└── global Client Setup

Paper
├── World Manager
└── Utilities Manager

Required Fabric
├── Map Manager
└── Utility Manager

Deferred research source
└── Performance Manager
```

The target scope above is product authority. During the current Launcher/installer consolidation, source inspection remains authoritative for what a particular `Local` commit actually bundles.

Client Setup owns only LazyBuilder-owned client JAR prefixes. Modrinth remains owner of the Minecraft profile, Fabric loader, third-party mods/modpack, and game launching.

## Launcher simplification lock

The V1 Launcher target intentionally does **not** add:

- background CPU-priority governor;
- automatic Paper gameplay/performance config mutation during Start;
- Performance Manager as a new hidden cross-component dependency;
- legacy profile compatibility parsing as an alternate Modrinth authority;
- source-tree fallback JAR resolution for packaged client/core runtime components;
- Performance/Boost RAM presets;
- a second Minecraft launcher or general mod manager.

Runtime-ready packages should use bundled, tested same-revision resources as the runtime source for LazyBuilder-owned Paper/client JARs.

## Remote proof boundary

Remote GitHub now **does** prove real Paper start/stop/restart and the managed-world local-control lifecycle listed above. These items should no longer be described as wholly untested live-Paper behavior.

Remote GitHub still does not prove:

- installed Windows Launcher interaction on a target PC;
- Java discovery/runtime extraction against arbitrary target-machine installations;
- real Modrinth profile detection/manual selection on the user's filesystem;
- transactional client JAR replacement on a representative target profile;
- Fabric screens/input inside a real Minecraft client;
- real-player teleport and Utilities movement behavior;
- real large-world transfer throughput at production scale;
- representative cross-edition conversion quality;
- real client disconnect/reconnect behavior;
- end-to-end Plugin Manager interaction against a long-lived user server.

Those remain later target-environment or Minecraft-client validation items. Do not replace them with speculative architecture solely to increase a checklist score.

## Static invariants

Current source should continue to satisfy:

- World Control and Map Action protocols remain separate from desktop loopback protocol;
- Map Manager consumes shared protocol contracts, not World Manager implementation source;
- Utility Manager has no hidden World Manager implementation dependency;
- Performance Manager remains isolated research source until explicit promotion;
- no new component may depend on Performance Manager while it is deferred;
- Client Setup mutates only LazyBuilder-owned prefixes and preserves unrelated mods;
- packaged core resolution has one runtime authority: bundled tested resources;
- server start does not rewrite unrelated Paper gameplay/performance settings;
- no duplicate Launcher/server performance owner is reintroduced;
- missing server paths remain visible rather than being silently discarded;
- active workspace is runtime-memory state, not stale persisted UI authority;
- one world registry, one filesystem authority, one task system, and one conversion-runtime owner remain canonical.

## Later target-environment validation

When local/target-machine testing is intentionally started, prioritize only behavior that remote CI cannot faithfully reproduce:

```text
1. install/open packaged LazyBuilder on the target Windows PC
2. verify Java/runtime discovery and server process behavior on that machine
3. verify Modrinth profile selection and client JAR replacement
4. launch Minecraft with the required V1 Fabric managers
5. verify Fabric UI/input/plugin-message interoperability
6. verify real-player World teleport/navigation
7. verify Utilities movement/gameplay interaction
8. exercise representative large worlds and storage-pressure behavior
9. exercise representative Java↔Bedrock conversion quality
10. verify disconnect/reconnect against the real client
```

Do not repeat server lifecycle cases already covered reliably by the dedicated remote Paper proof unless a target-machine-specific symptom appears.

## Historical reports

`remote-github-complete.md` and older reports are historical snapshots. Older references to previous client-manager sets, workflow counts, or monolithic client packaging must not override current source plus this authority.

## Update policy

Do not hard-code a workflow run number or HEAD SHA as permanent current state. Fix reproducible defects at the smallest wrong owner. Do not reopen removed automation, duplicate architecture, or optional performance systems without measured evidence.
