# Current Verification Authority

This file defines how to determine the current LazyBuilder repository state and where each proof layer stops.

## Source authority

```text
Local = active development / remediation / source authority
main  = stable / release authority
```

Never infer current readiness from an older completion report, commit note, or successful workflow attached to a superseded revision.

## Current phase

The current continuation phase is **source remediation and synchronization** following defects reproduced on the installed Local PC on 15 September 2026.

Current remediation authority:

[`local-pc-remediation-2026-09-15.md`](local-pc-remediation-2026-09-15.md)

Local PC acceptance is currently closed. It reopens only after the exact current `Local` revision passes all relevant source/remote gates and the remediation authority has no unresolved source-side P0/P1 contradiction.

## Verification layers

LazyBuilder separates fast development from independent final verification.

```text
Local development
→ targeted module proof as needed
→ DEV.cmd finalize-local for integrated local proof

Verify
→ canonical integrated remote/final CI
→ repository consistency
→ Paper/shared unit/build verification
→ Fabric build/artifact verification
→ Launcher frontend/Rust verification
→ exact-head Paper runtime + restart persistence proof
→ Windows/Tauri/NSIS packaging
→ installer smoke verification

Dedicated workflows
→ manual or review-specific evidence only
→ not parallel readiness authorities
```

Normal pushes to `Local` intentionally do not run full CI. Full `Verify` runs on explicit `workflow_dispatch`, pull requests to `Local`/`main`, and pushes to `main`.

## Required `Verify` gate

The canonical `Verify` workflow contains:

```text
consistency          version/repository/scope contracts
paper                full Maven Paper/shared compile + tests + core artifacts
paper-runtime-smoke  exact-head Paper boot + managed-world lifecycle + restart persistence
fabric               three Fabric manager builds + client artifact verification
launcher-check       Svelte typecheck/build + Rust check/test on Windows
tauri-desktop        package exact tested core/client artifacts into Windows NSIS build
```

A later Local PC candidate must use an artifact produced from a successful `Verify` run for the exact revision being tested.

## Client-suite state

Canonical V1 Fabric client suite:

```text
Required Fabric
├── Map Manager
├── Utility Manager
└── Performance Manager
```

All three are active source/runtime components and are built, verified, bundled, installed and repaired by the same Client Setup transaction. Performance Manager remains bounded to client performance policy/diagnostics; it does not become a cross-manager scheduler or workload authority.

## Dedicated workflows

Dedicated workflows exist for focused proof/debugging but do not replace integrated `Verify`.

```text
Paper Runtime Proof
→ manual focused Paper lifecycle/restart evidence

Launcher Verify
→ manual focused Launcher source/contract evidence

Launcher UI Preview
→ manual or pull-request visual evidence

Minecraft UI Preview
→ manual or pull-request real-renderer visual evidence
```

These workflows may provide faster specialist evidence during review or debugging. Repository/package readiness is still determined from the integrated `Verify` workflow for the exact candidate revision.

## Paper runtime proof contract

The Paper runtime proof executes against a disposable stable Paper 1.21.4 server and covers:

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

Runtime evidence may be uploaded as a short-lived workflow artifact for inspection.

## Determining `REMOTE_GITHUB` status

Repository/package readiness:

```text
successful integrated Verify
for the exact candidate revision
→ repository/package REMOTE_GITHUB green
```

A successful focused workflow proves only its named boundary. It does not make the whole repository/package candidate green.

## Current target product scope

```text
Desktop
├── Server Manager
├── Plugin Manager
└── Client Setup

Paper
├── World Manager
└── Utilities Manager

Fabric
├── Map Manager
├── Utility Manager
└── Performance Manager

Shared
└── Protocol
```

Client Setup owns only LazyBuilder-owned client JAR prefixes. Modrinth remains owner of the Minecraft profile, Fabric loader, unrelated third-party mods/modpack, and game launching.

## Launcher simplification lock

The V1 target intentionally does **not** add:

- a second Minecraft launcher or general mod manager;
- source-tree fallback JAR resolution for packaged client/core runtime components;
- automatic mutation of unrelated Paper gameplay/performance configuration during Start;
- duplicate cross-manager performance ownership;
- a hidden shared scheduler without a second real consumer and stable contract;
- a parallel conversion runtime owner;
- a second installer/build system.

Runtime-ready packages use bundled, tested same-revision LazyBuilder resources as their runtime source.

## Remote proof boundary

Remote GitHub **does prove** the cases it explicitly executes: unit/contract tests, artifact contents, Paper boot/lifecycle/restart, frontend/Rust compile/tests, same-revision packaging, and installer smoke verification.

Remote GitHub still does **not** prove:

- installer UX on the actual target Windows PC;
- installed Launcher first-run behavior under that PC's software/security environment;
- long-lived persistent server workspace behavior;
- representative third-party plugin compatibility;
- real Modrinth profile behavior under the user's installation;
- Fabric screens/input inside a real Minecraft client unless the dedicated real-renderer proof explicitly ran;
- real-player teleport/navigation and Utilities gameplay behavior;
- full-PC reboot recovery;
- production-scale large-world/storage-pressure behavior;
- representative Java↔Bedrock conversion quality.

Those remain later target-machine acceptance items. They are not a reason to skip source remediation first.

## Static invariants

Current source must continue to satisfy:

- World Control and Map Action protocols remain separate from desktop loopback protocol;
- Map Manager consumes shared protocol contracts, not World Manager implementation source;
- Utility Manager has no hidden World Manager implementation dependency;
- Performance Manager remains bounded to performance policy/diagnostics and does not own other managers' workloads;
- Client Setup mutates only LazyBuilder-owned prefixes and preserves unrelated mods;
- packaged core/client resolution has one runtime authority: bundled tested resources;
- server start does not rewrite unrelated Paper gameplay/performance settings;
- no duplicate Launcher/server performance owner is reintroduced;
- missing server paths remain visible rather than silently discarded;
- active workspace is runtime-memory state, not stale persisted UI authority;
- one world registry, one filesystem authority, one task system, and one conversion-runtime owner remain canonical;
- Maven and Gradle build paths remain repository-owned;
- installed runtime and developer Java build paths use the LazyBuilder-owned Windows temp policy where relevant;
- developer commands route through the canonical `DEV.cmd` / `dev.ps1` control plane;
- Local distributables use one canonical `dist/Local` layout.

## Reopening Local PC validation

Local PC acceptance may reopen only after:

```text
remediation source items resolved
→ exact Local candidate DEV.cmd finalize-local passes where applicable
→ exact candidate integrated Verify green
→ canonical installer artifact produced
→ final source/repository audit clean
→ explicit decision to reopen target-machine acceptance
```

Then follow only:

[`local-pc-validation-plan.md`](local-pc-validation-plan.md)

## Historical reports

`remote-github-complete.md`, `local-pc-testing-notes.md`, and older reports are evidence snapshots. Older phase labels, client-manager sets, workflow counts, trigger rules, or packaging details must not override current source plus the remediation and verification authorities.

## Update policy

Do not hard-code a workflow run number or HEAD SHA as permanent current state. Keep this file semantic. Exact tested revisions belong in workflow/build provenance and Local PC test records.
