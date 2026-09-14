# Current Verification Authority

This file defines how to determine the current LazyBuilder repository state.

## Source authority

```text
Local = active development authority
main  = stable/release authority
```

Never infer current readiness from an older completion report, commit note, or successful workflow attached to a previous SHA.

## Current proof rule

Use this order:

```text
1. current Local HEAD
2. latest Verify workflow for that exact HEAD SHA
3. component-specific build/test output from that workflow
4. LOCAL_CODE evidence for target-PC behavior
5. LIVE_SERVER evidence for real Paper/Minecraft behavior
```

A successful workflow for an older SHA is historical evidence only.

## Required remote gate

The current V1 `Verify` workflow requires five exact-head jobs:

```text
consistency      version/repository/scope contracts
paper            full Maven Paper/shared compile + tests + core artifacts
fabric           Map Manager + Utility Manager builds + required client artifacts
launcher-check   Svelte typecheck/build + Rust check/test on Windows
tauri-desktop    package tested core/client artifacts into Windows NSIS build
```

Performance Manager is deferred research source and is not a V1 package/runtime requirement. There is no separate `utilities` job; Utilities-Manager is covered by the full Paper reactor.

Only after all five jobs succeed for the same exact HEAD may that SHA be described as `REMOTE_GITHUB green`.

## V1 runtime scope

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

Deferred / not bundled in V1
└── Performance Manager
```

Client Setup owns only the two required LazyBuilder client JAR prefixes. Modrinth remains owner of the Minecraft profile, Fabric loader, third-party mods/modpack, and game launching.

## Launcher simplification lock

The current V1 Launcher intentionally does **not** include:

- background CPU-priority governor;
- automatic Paper gameplay/performance config mutation during Start;
- Performance Manager as a required/bundled client component;
- legacy `profile.json` compatibility parsing as a Modrinth authority;
- source-tree fallback JAR resolution for packaged client/core runtime components;
- Performance/Boost RAM presets;
- a second Minecraft launcher or general mod manager.

Runtime-ready packages use bundled, tested same-revision resources as the only runtime source for LazyBuilder-owned Paper/client JARs.

## Runtime proof boundary

A green GitHub workflow still does not prove:

- installed Windows Launcher behavior;
- Java discovery/runtime extraction on the target PC;
- actual Modrinth profile detection/manual selection;
- transactional client JAR replacement on the target filesystem;
- real Paper start/stop/restart/detached recovery;
- Fabric screens/input inside Minecraft;
- world lifecycle/import/export/transfer behavior;
- Utility Manager gameplay/client behavior;
- shutdown/restart persistence.

Those remain `LOCAL_CODE` and `LIVE_SERVER` responsibilities.

## Static invariants before local validation

Current source should continue to satisfy:

- World Control V5 and Map Action V2 remain separate from desktop loopback protocol V2;
- Map Manager consumes shared protocol source, not World Manager implementation source;
- Utility Manager has no shared World Manager protocol dependency;
- Performance Manager source remains isolated and deferred rather than becoming a hidden V1 dependency;
- Client Setup requires exactly Map Manager + Utility Manager;
- Client Sync mutates only LazyBuilder-owned required prefixes and preserves unrelated mods;
- packaged core resolution has one runtime authority: bundled `resources/core`;
- server start does not rewrite Paper performance/gameplay settings;
- no `cpu_governor` or `paper_performance` Launcher engine owner is reintroduced;
- missing server paths remain in the library rather than being silently deleted;
- active workspace is runtime-memory state, not a stale persisted registry authority.

## Current proof status

Current readiness is always resolved dynamically from the exact current `Local` HEAD. Do not preserve a permanent SHA or run number in this file.

```text
all five exact-head Verify jobs succeed
→ REMOTE_GITHUB green

any required exact-head job missing / queued / failed / cancelled
→ REMOTE_GITHUB not yet proven
```

`REMOTE_GITHUB green` does not imply `LOCAL_CODE` or `LIVE_SERVER` validation.

## LOCAL_CODE validation sequence

```text
1. confirm current Local HEAD
2. verify Java 21, Maven, Gradle 8.12, Node/npm, Rust
3. run python scripts/verify_versions.py
4. run Maven Paper/shared verify
5. build Map Manager
6. build Utility Manager
7. run BUILD-LAUNCHER.cmd
8. install/open LazyBuilder
9. test global Client Setup against a real Modrinth profile
10. confirm only Map + Utility JARs are maintained and unrelated mods remain intact
11. create/open/adopt a server workspace
12. Prepare Server / EULA / Start / Stop / Restart / detached recovery
```

## LIVE_SERVER / Minecraft sequence

```text
1. launch the selected profile from Modrinth
2. confirm Map Manager + Utility Manager load
3. verify World Control V5 / Map Action V2 interoperability
4. verify Worlds navigation/current-world synchronization
5. verify Duplicate / Archive / Restore / Delete
6. verify whole-world Export and Map Export Area
7. verify Import upload → inspection → review → explicit Import
8. verify large transfer/checksum/storage failure handling
9. verify disconnect/reconnect and shutdown cleanup
10. verify Utility Manager client convenience
11. verify Paper Utilities behavior
12. verify Plugin Manager lifecycle
```

Performance Manager testing is independent deferred research and must not block V1 completion.

## Historical reports

`remote-github-complete.md` and older reports are immutable historical snapshots. Old references to three required Fabric Managers, six Verify jobs, Dynamic FPS handoff, or a monolithic `lazybuilder-client` JAR describe older repository states and are not current V1 authority.

## Update policy

Do not hard-code a workflow run number or HEAD SHA as permanent current state. Fix reproducible defects at the smallest wrong owner. Do not reopen removed automation, duplicate architecture, or optional performance systems without measured local/runtime evidence.
