# Local PC Validation Plan

This is the canonical **later** target-machine acceptance plan. It is not active while source remediation is open.

## Entry gate

Do not begin this plan until all of the following are true for the exact candidate `Local` revision:

```text
source remediation complete
→ remediation authority has no unresolved source-side P0/P1 item
→ Verify green
→ applicable Paper Runtime Proof green
→ canonical NSIS artifact produced and smoke-verified
→ final source/repository audit clean
→ explicit decision to reopen Local PC acceptance
```

Current remediation authority:

[`local-pc-remediation-2026-09-15.md`](local-pc-remediation-2026-09-15.md)

## Test authority

```text
Repository/source authority : Local
Remote proof                : exact successful Verify + applicable Paper Runtime Proof
Target-machine proof        : this Local PC validation plan
Stable/release authority    : main, only after explicit promotion
```

Do not build from source on the Local PC for normal acceptance testing. Test the canonical installer artifact produced by the successful `Verify` run for the exact candidate revision.

Record:

```text
Local commit SHA :
Verify run       :
Paper proof run  :
Installer SHA256 :
Windows version  :
PC / disk notes  :
Test date        :
```

Use `build-info.json` and `SHA256SUMS.txt` as provenance.

## Canonical product under acceptance

```text
Desktop
├── Server Manager
├── Plugin Manager
└── Client Setup

Paper
├── World Manager
└── Utilities Manager

Fabric client suite
├── Map Manager
├── Utility Manager
└── Performance Manager
```

All three Fabric managers are required members of one Client Setup transaction. Third-party Modrinth mods remain outside LazyBuilder ownership.

# Acceptance sequence

Run phases in order:

```text
0. clean-machine preparation
1. installer
2. Launcher first run
3. managed Java + Paper server
4. Plugin Manager
5. World Manager Paper functions
6. Utilities Manager Paper functions
7. Modrinth / Client Setup
8. Map Manager Fabric functions
9. Utility + Performance Manager Fabric functions
10. Paper ↔ Fabric interoperability
11. restart / recovery / update
12. large-world / storage / conversion
```

## Phase 0 — clean-machine preparation

Preferred environment:

- Windows 10/11 x64;
- normal user account;
- no requirement for Node.js, Rust, Maven, Gradle, Python, Git, or Visual Studio Build Tools;
- enough free space for Minecraft, server workspace, backups, transfers and conversion;
- Modrinth App introduced only when Client Setup testing begins;
- Minecraft Java Edition 1.21.4 available for client testing.

**PASS:** acceptance can start from the installer package only.

## Phase 1 — installer

Test `LazyBuilder-Setup-Local.exe`.

Verify:

- clean install and update-over-existing-install;
- current-user installation/uninstall registration;
- app launches without developer tooling;
- no global `TEMP`, `TMP`, `PATH`, `JAVA_HOME`, Node, Rust, Maven, Gradle or Python mutation;
- normal LazyBuilder user/workspace data remains outside the app install directory;
- reinstall/repair/update does not destroy workspace or profile state.

**PASS:** normal user install/update works without terminal/source setup.

## Phase 2 — Launcher first run

Verify:

- Launcher opens without global Java;
- no blank/frozen window;
- errors/prerequisites are actionable;
- Server Manager, Plugin Manager and Client Setup open normally;
- intended persisted state survives restart while transient authority does not;
- Launcher log records the LazyBuilder-owned runtime temp path.

Expected runtime temp:

```text
%LOCALAPPDATA%\LazyBuilder\temp
```

**PASS:** Launcher is the normal entrypoint with no developer intervention.

## Phase 3 — managed Java and Paper server

Verify:

- managed Java 21 provisioning;
- Paper 1.21.4 provisioning;
- create/open/adopt workspace;
- start → ready → stop → start/restart;
- no loopback/`Invalid argument: connect` failure;
- duplicate start blocked safely;
- unexpected exit surfaced;
- detached process reconciled/recovered safely;
- restart can recover both `Stopping` and `Detached` states without killing unrelated Java processes;
- workspace/process state survives Launcher restart correctly.

**PASS:** healthy Paper can be operated entirely through LazyBuilder.

## Phase 4 — Plugin Manager

With Paper offline, exercise representative third-party plugins:

- detection/install/update;
- duplicate handling;
- enable/disable;
- safe JAR removal while preserving plugin data;
- restart-required reporting;
- rollback/recovery where implemented;
- mutations blocked while runtime state makes them unsafe;
- protected LazyBuilder core plugins cannot be removed as normal third-party plugins.

**PASS:** plugin lifecycle works without corrupting server/plugin data.

## Phase 5 — World Manager Paper functions

Verify installed-product behavior for:

- world list/create/settings/teleport;
- archive/restore/duplicate/backup;
- native Java export;
- authenticated upload/import;
- delete;
- world ID/settings persistence across Paper/Launcher restart;
- interrupted operation cleanup/recovery.

**PASS:** accepted world operations survive persistence and recovery correctly.

## Phase 6 — Utilities Manager Paper functions

With a real player, verify implemented server utility categories:

```text
World Safety
Movement
Build Helpers
```

Check permissions, restart persistence where applicable, and absence of ownership overlap with World Manager.

**PASS:** utilities work with a real player without hidden cross-manager dependency.

## Phase 7 — Modrinth / Client Setup

Use a representative Minecraft 1.21.4 Fabric profile.

Verify:

- profile discovery/manual selection;
- compatibility reporting;
- selected profile persistence;
- sync blocked while profile is in use;
- transactional `Sync Client`;
- exactly one current JAR for each required manager:

```text
lazybuilder-map-manager-*.jar
lazybuilder-utility-manager-*.jar
lazybuilder-performance-manager-*.jar
```

- old/duplicate LazyBuilder-owned versions cleaned;
- unrelated third-party mods byte-for-byte untouched;
- repeated sync is idempotent;
- rollback restores the previous complete three-manager set if publishing fails;
- moved/missing profiles remain visible as unavailable rather than silently recreated.

**PASS:** Client Setup safely owns only the three LazyBuilder client components.

## Phase 8 — Map Manager Fabric functions

In real Minecraft, verify:

- UI/input behavior;
- managed-world/current-world presentation;
- settings/lifecycle presentation;
- map surface/navigation/teleport;
- import/export transfer UI;
- progress/error/cancel behavior;
- reconnect does not leave stale map/transfer state.

**PASS:** Map Manager works against the real Paper World Manager.

## Phase 9 — Utility + Performance Manager Fabric functions

### Utility Manager

Verify:

- extended chat history;
- unsent chat draft behavior;
- disconnect details copy action;
- Reconnect on `DisconnectedScreen`;
- Reconnect fallback when flow returns directly to `MultiplayerScreen`/server list;
- resource reload completion notice;
- contextual screenshot naming when enabled;
- borderless-window behavior when enabled;
- preference persistence.

### Performance Manager

Verify:

- mod loads independently;
- frame-time pressure diagnostics remain bounded/passive;
- unfocused/minimized FPS policy behaves as documented;
- on-demand performance/window/workload status works;
- no required external optimization mod;
- no hidden control over Map Manager or Utility Manager implementation internals.

**PASS:** both managers function inside real Minecraft within their architecture boundaries.

## Phase 10 — Paper ↔ Fabric interoperability

Test the full installed stack:

```text
Launcher
→ Paper 1.21.4
→ World Manager / Utilities Manager
→ Modrinth Fabric 1.21.4
→ Map / Utility / Performance Managers
→ real player
```

Verify protocol connection, permissions, world synchronization, teleport/navigation, transfers, disconnect/reconnect, Paper restart while client is present, client restart while server remains running, and cleanup of stale pending UI/task state.

**PASS:** all components operate as one product while retaining documented ownership.

## Phase 11 — restart, recovery, update

Run:

```text
A. Launcher restart only
B. Paper restart only
C. Minecraft client restart only
D. Launcher + Paper restart
E. full PC reboot
F. installer update over existing installation
```

After each case verify workspace/world/plugin/profile/client-JAR integrity, no false process conflict, no stale temporary ownership, and no manual filesystem repair requirement.

**PASS:** normal restart/update paths preserve user state and recover transient state safely.

## Phase 12 — large-world, storage and conversion

Only after Phases 1–11 pass, use representative worlds (~100–500 MB, ~2 GB, ~10 GB where safe).

Exercise duplicate, backup, export, import, transfer and safe interruption/recovery. Record free disk, elapsed time, temporary disk growth, result and cleanup.

For Java↔Bedrock conversion verify:

- automatic stable conversion runtime bootstrap;
- checksum/compatibility gate behavior;
- supported targets surfaced from runtime capability;
- representative Java→Bedrock and Bedrock→Java quality;
- rollback/retry behavior after a controlled bootstrap or conversion failure.

Never intentionally exhaust the OS drive.

**PASS:** large operations fail early when unsafe, clean transactional state correctly, and produce acceptable representative conversion output.

# Defect recording

For every failure record:

```text
TEST ID / PHASE:
Local commit SHA:
build-info.json version/run:
Windows version:
component:
steps to reproduce:
expected:
actual:
exact visible error:
reproducible: yes/no
reproduces after restart: yes/no
relevant log path/file:
first suspected owner:
```

Priority:

```text
P0  data loss / destructive corruption / unsafe install
P1  installer, Launcher, server, plugin or required client path unusable
P2  accepted feature broken but workaround exists
P3  polish / non-blocking UX
```

# STOP rule

Do not promote `Local` to `main` merely because remote CI is green.

Promotion discussion begins only when all installed-product phases pass and no unresolved P0/P1 remains. Then perform one final repository audit and make an explicit promotion decision.
