# Current Verification Authority

This file defines how to determine the current LazyBuilder repository state and where each proof layer stops.

## Source authority

```text
Local = active development / source authority
main  = stable / release authority
```

Never infer current readiness from an older completion report, commit note, or successful workflow attached to a superseded revision.

## Current phase

The current continuation phase is **Local PC validation**.

Canonical execution plan:

[`local-pc-validation-plan.md`](local-pc-validation-plan.md)

Remote CI should now be treated as the prerequisite baseline for target-machine testing, not as a substitute for target-machine proof.

## Verification layers

LazyBuilder has two distinct remote verification authorities:

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

The separation is intentional. `Verify` follows every repository change. `Paper Runtime Proof` follows changes that can affect the Paper runtime path.

## Required `Verify` gate

The current `Verify` workflow contains these canonical gates:

```text
consistency          version/repository/scope contracts
paper                full Maven Paper/shared compile + tests + core artifacts
paper-runtime-smoke  exact-head Paper boot + lifecycle harness used by packaging
fabric               Fabric manager builds + client artifacts
launcher-check       Svelte typecheck/build + Rust check/test on Windows
tauri-desktop        package tested core/client artifacts into Windows NSIS build
```

A Local PC candidate must use an artifact produced from a successful `Verify` run for the exact revision being tested.

## Client-suite state

Intended V1 product scope:

```text
Required Fabric
├── Map Manager
└── Utility Manager

Deferred research source
└── Performance Manager
```

Current `Local` Launcher/CI source may still contain transitional references that build or bundle Performance Manager. That is an implementation/packaging fact, not proof that Performance Manager has been promoted to required V1 scope.

During Local PC validation:

- do not expand Performance Manager scope;
- do not create new dependencies on it;
- if bundled, confirm only that it does not break startup, packaging, compatibility, or required manager behavior.

## Dedicated Paper runtime gate

`.github/workflows/paper-runtime-proof.yml` is the deeper Paper-side runtime authority. It is triggered by changes to:

```text
root Maven ownership
plugins/world-manager/**
plugins/utilities-manager/**
shared/protocol/**
Paper runtime-proof scripts/workflow
```

A green run proves, on a disposable Paper 1.21.4 server:

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

For repository/package readiness:

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

A later commit that changes only docs/Launcher and is outside the Paper Runtime Proof path scope does not invalidate already-proven Paper behavior. Any later commit touching a scoped Paper path must produce a new successful Paper Runtime Proof before Paper runtime readiness is claimed again.

## Current target product scope

```text
Desktop
├── Server Manager
├── Plugin Manager
└── Client Setup

Paper
├── World Manager
└── Utilities Manager

Required Fabric
├── Map Manager
└── Utility Manager

Deferred research source
└── Performance Manager
```

Client Setup owns only LazyBuilder-owned client JAR prefixes. Modrinth remains owner of the Minecraft profile, Fabric loader, third-party mods/modpack, and game launching.

## Launcher simplification lock

The V1 target intentionally does **not** add:

- background CPU-priority governor;
- automatic Paper gameplay/performance config mutation during Start;
- Performance Manager as a new hidden cross-component dependency;
- legacy profile compatibility parsing as an alternate Modrinth authority;
- source-tree fallback JAR resolution for packaged client/core runtime components;
- Performance/Boost RAM presets;
- a second Minecraft launcher or general mod manager.

Runtime-ready packages should use bundled, tested same-revision LazyBuilder resources as their runtime source.

## Remote proof boundary

Remote GitHub **does prove** the Paper lifecycle cases it explicitly executes, including real Paper boot/restart and managed-world lifecycle/persistence.

Remote GitHub still does **not** prove the following target-machine behavior:

- installer UX on the actual target Windows PC;
- installed Launcher first-run behavior;
- managed Java provisioning against arbitrary target-machine conditions;
- long-lived real server workspace behavior;
- Plugin Manager against representative third-party plugins on a persistent server;
- real Modrinth profile detection/manual selection;
- transactional LazyBuilder client JAR replacement on a representative user profile;
- Fabric screens/input inside a real Minecraft client;
- real-player teleport/navigation and Utilities gameplay behavior;
- Paper ↔ Fabric disconnect/reconnect behavior in actual use;
- full-PC reboot recovery;
- installer update over an existing user installation;
- production-scale large-world throughput/storage-pressure behavior;
- representative Java↔Bedrock conversion quality.

These are the reasons for the Local PC phase. Do not add speculative architecture to simulate them remotely.

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
- missing server paths remain visible rather than silently discarded;
- active workspace is runtime-memory state, not stale persisted UI authority;
- one world registry, one filesystem authority, one task system, and one conversion-runtime owner remain canonical.

## Local PC validation authority

Once remote proof is green, follow only:

[`local-pc-validation-plan.md`](local-pc-validation-plan.md)

Its required top-level order is:

```text
1. installer
2. Launcher first run
3. managed Java + Paper server
4. Plugin Manager
5. World Manager
6. Utilities Manager
7. Modrinth / Client Setup
8. Map Manager
9. Utility Manager
10. Paper ↔ Fabric interoperability
11. restart/recovery/update matrix
12. large-world/storage/conversion tests
```

Do not repeat remote Paper lifecycle cases merely for checklist duplication. Re-run them locally only as part of the installed product flow or when a target-machine-specific symptom appears.

## Defect policy during Local PC phase

Only reproducible target-machine failures reopen source work.

For each failure:

```text
record exact Local revision/build-info
→ record steps/expected/actual/error/log
→ identify first wrong owner
→ fix smallest owner
→ rerun smallest failing proof
→ rerun affected Local PC phase
```

Do not batch unrelated Local PC defects into one architectural rewrite.

## Historical reports

`remote-github-complete.md` and older reports are historical snapshots. Older references to client-manager sets, workflow counts, or packaging details must not override current source plus this authority.

## Update policy

Do not hard-code a workflow run number or HEAD SHA as permanent current state. Keep this file semantic. The exact revision under Local PC test belongs in the Local PC test record/build-info, not as permanent architecture documentation.
