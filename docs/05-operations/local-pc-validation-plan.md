# Local PC Validation Plan

This is the canonical handoff for the next LazyBuilder phase after `REMOTE_GITHUB` is green.

The purpose of this phase is **not** to add features or redesign architecture. The purpose is to install and use the current `Local` product on a representative Windows PC, starting from the installer and continuing through Launcher, Paper plugins, Fabric mods, and real Minecraft workflows.

## Test authority

```text
Repository/source authority : Local
Remote proof                : latest successful Verify + Paper Runtime Proof
Target-machine proof        : this Local PC validation plan
Stable/release authority    : main, only after explicit promotion
```

Do not build from source on the Local PC for normal acceptance testing. Test the canonical installer artifact produced by the successful `Verify` run for the exact `Local` commit under test.

Record the exact build before testing:

```text
Local commit SHA :
Verify run       :
Paper proof run  :
Installer SHA256 :
Windows version  :
PC / disk notes  :
Test date        :
```

Use `build-info.json` and `SHA256SUMS.txt` from the canonical Local package as provenance.

---

# Acceptance sequence

Run the phases in order. Do not skip ahead when an earlier foundation is broken.

```text
1. INSTALLER
   ↓
2. LAUNCHER FIRST RUN
   ↓
3. MANAGED JAVA + PAPER SERVER
   ↓
4. PLUGIN MANAGER
   ↓
5. WORLD MANAGER PAPER FUNCTIONS
   ↓
6. UTILITIES MANAGER PAPER FUNCTIONS
   ↓
7. MODRINTH / CLIENT SETUP
   ↓
8. MAP MANAGER FABRIC FUNCTIONS
   ↓
9. UTILITY MANAGER FABRIC FUNCTIONS
   ↓
10. PAPER ↔ FABRIC INTEROPERABILITY
   ↓
11. RESTART / RECOVERY / UPDATE
   ↓
12. LARGE-WORLD / STORAGE / CONVERSION TESTS
```

Performance Manager is deferred research source. If the current installer still bundles it because of transitional packaging, confirm only that it does not break startup or introduce a hidden dependency. Do not expand its feature scope during this Local PC phase.

---

## Phase 0 — clean-machine preparation

Preferred first test environment:

- Windows 10/11 x64;
- no requirement for Node.js, Rust, Maven, Gradle, Python, Git, or Visual Studio Build Tools;
- normal user account;
- enough free disk space for Minecraft, server workspace, backups, imports/exports, and test worlds;
- Modrinth App installed only when Client Setup testing begins;
- Minecraft Java Edition 1.21.4 available for real client testing.

Before installation, confirm LazyBuilder is not relying on a developer shell, source checkout, global Maven/Gradle, or manually prepared JARs.

**PASS:** the test PC can begin with the installer package only.

---

## Phase 1 — installer

Test the canonical file:

```text
LazyBuilder-Setup-Local.exe
```

Check:

- installer opens normally;
- installation works without developer tools;
- install location is appropriate for a current-user app;
- Windows uninstall registration exists;
- app launches after install;
- no global `PATH`, `JAVA_HOME`, Node, Rust, Maven, Gradle, or Python configuration is changed;
- normal LazyBuilder user data is outside the application installation directory;
- reinstall/repair does not destroy existing workspace data;
- uninstall behavior is understandable and does not unexpectedly delete server worlds/user data.

Also test one update-over-existing-install case after the first clean-install pass succeeds.

**PASS:** a normal user can install/open/update LazyBuilder without a terminal or source code.

---

## Phase 2 — Launcher first run

Open LazyBuilder normally from the installed application.

Check:

- Launcher opens without requiring Java to be globally installed;
- main surfaces render correctly;
- no blank/frozen window;
- paths shown to the user are valid;
- missing prerequisites are reported clearly;
- closing and reopening preserves only the state intended to persist;
- no stale workspace is silently treated as current authority;
- errors remain visible and actionable.

Exercise the primary Launcher areas:

```text
Server Manager
Plugin Manager
Client Setup
```

**PASS:** Launcher is usable as the normal entrypoint and does not require developer intervention.

---

## Phase 3 — managed Java and Paper server

From Launcher, exercise the server path rather than manually installing Java for LazyBuilder.

Check:

- Java 21 is provisioned when server functionality needs it;
- managed runtime is placed under LazyBuilder-owned application data;
- Java validation succeeds;
- Paper 1.21.4 can be provisioned;
- server workspace can be created/opened/adopted according to current UI;
- server starts;
- server reaches ready state;
- stop works cleanly;
- restart works;
- Launcher state matches actual process state;
- duplicate start attempts are handled safely;
- abnormal process exit is surfaced correctly;
- after Launcher restart, process/workspace state is reconciled correctly.

**PASS:** a user can reach a healthy Paper 1.21.4 server entirely through LazyBuilder.

---

## Phase 4 — Plugin Manager

Use a real test server workspace.

Verify inventory and lifecycle behavior for representative third-party Paper plugins:

- installed plugin detection;
- install flow;
- update flow where supported;
- compatibility/dependency reporting;
- duplicate handling;
- disable/enable behavior;
- safe JAR removal;
- plugin data preservation;
- restart-required behavior;
- rollback/recovery behavior where implemented;
- Launcher restart does not lose authoritative plugin state.

Do not treat GitHub CI packaging as proof of this long-lived user-server behavior.

**PASS:** Plugin Manager can manage representative plugins without corrupting server/plugin data.

---

## Phase 5 — World Manager Paper functions

Use the installed World Manager plugin through the normal product surfaces.

Verify at minimum:

- managed world list;
- create world;
- world settings read/write;
- teleport into a managed world;
- archive;
- restore;
- duplicate;
- backup;
- native Java export;
- upload/import;
- delete;
- persistence after Paper restart;
- persistence after Launcher restart;
- world IDs and settings still resolve after restart.

Then exercise failure/recovery cases:

- cancel/interrupt a large copy where practical;
- restart after a staged workspace exists;
- interrupted transfer partial cleanup;
- completed-upload ownership after restart;
- interrupted export/backup temporary cleanup;
- converter runtime recovery where practical.

Remote Paper Runtime Proof already covers the basic lifecycle mechanically. Local PC testing is for installed-machine behavior, real disk/filesystem behavior, real user interaction, and defects that CI cannot reproduce faithfully.

**PASS:** all accepted world operations work through the installed product and survive restart/recovery correctly.

---

## Phase 6 — Utilities Manager Paper functions

With a real player connected, test the actual implemented Utilities Manager features under its current categories:

```text
World Safety
Movement
Build Helpers
```

Verify behavior in gameplay, permissions where applicable, server restart persistence where applicable, and absence of unwanted interaction with World Manager.

**PASS:** server utilities behave correctly with a real player and do not create hidden dependencies between managers.

---

## Phase 7 — Modrinth / Client Setup

Install/use Modrinth App as the external Minecraft profile authority.

Create or use a representative Minecraft 1.21.4 Fabric profile and launch it at least once before compatibility verification when required by current detection logic.

Test:

- Modrinth profile discovery;
- manual profile selection if automatic discovery is insufficient;
- Minecraft 1.21.4 validation;
- Fabric validation;
- selected profile persistence;
- LazyBuilder client status;
- Sync Client;
- install/update/repair of LazyBuilder-owned client JARs;
- unrelated mods remain untouched;
- repeated Sync Client is idempotent;
- profile path errors are visible;
- profile switching behaves correctly.

Intended V1 required Fabric product scope:

```text
Map Manager
Utility Manager
```

Performance Manager remains deferred even if transitional packaging currently includes its JAR.

**PASS:** LazyBuilder can safely synchronize its own client components into a real Modrinth profile without becoming a second Minecraft launcher or general mod manager.

---

## Phase 8 — Map Manager Fabric functions

Launch Minecraft through Modrinth using the synchronized profile.

Check the actual in-client Map Manager surface and input behavior:

- UI opens/closes correctly;
- input does not conflict unexpectedly with Minecraft;
- managed-world list appears from the server;
- current world presentation is correct;
- lifecycle/settings presentation is correct;
- navigation/teleport actions work with a real player;
- Import/Export UI works;
- transfer progress/error states are understandable;
- Map Export Area behavior works where implemented;
- reconnect does not leave stale client state.

**PASS:** Map Manager works inside a real Minecraft client against the real Paper server.

---

## Phase 9 — Utility Manager Fabric functions

Exercise the currently implemented passive client conveniences, including where applicable:

- chat/session convenience;
- disconnect/reconnect presentation;
- borderless-window presentation;
- reload notification;
- screenshot naming;
- local preferences.

Verify settings persistence and confirm it has no hidden implementation dependency on Map Manager or Performance Manager.

**PASS:** Utility Manager works independently and remains non-destructive to unrelated client state.

---

## Phase 10 — Paper ↔ Fabric interoperability

Test the full installed stack together:

```text
Launcher
→ Paper 1.21.4
→ World Manager / Utilities Manager
→ Modrinth Fabric 1.21.4
→ Map Manager / Utility Manager
→ real player
```

Check:

- protocol connection after joining server;
- permissions;
- world list synchronization;
- settings actions;
- teleport/navigation;
- import/export transfer;
- disconnect;
- reconnect;
- server restart while client is present;
- client restart while server stays running;
- no stale pending UI/task state after recovery.

**PASS:** components operate as one product while retaining their documented ownership boundaries.

---

## Phase 11 — restart, recovery, update

Run a deliberate restart matrix:

```text
A. Launcher restart only
B. Paper restart only
C. Minecraft client restart only
D. Launcher + Paper restart
E. full PC reboot
```

After each case verify:

- workspace remains intact;
- worlds remain intact;
- registry/settings remain intact;
- plugin state remains understandable;
- selected client profile remains correct;
- LazyBuilder-owned client JAR state remains correct;
- no transient work/temp ownership state causes a false conflict;
- app/server/client can continue without manual filesystem repair.

Then test one normal LazyBuilder installer update over the existing installation and repeat the essential smoke path.

**PASS:** normal restart/update paths preserve user state and recover transient runtime state safely.

---

## Phase 12 — large-world, disk-pressure and conversion tests

Do this only after Phases 1–11 are stable.

Use representative worlds rather than synthetic architecture changes.

Suggested progression:

```text
~100–500 MB
~2 GB
~10 GB if the test PC has enough safe free space
larger only when there is a concrete production need
```

For each representative size exercise the relevant operations:

- Duplicate;
- Backup;
- Export;
- Import;
- transfer where applicable;
- restart/recovery after a deliberately interrupted operation where safe.

Record:

```text
world size
operation
elapsed time
free disk before
affected storage root
peak/observed disk growth
result
cleanup result after failure/restart
error text if any
```

Also test storage-pressure behavior on a controlled test volume or otherwise safe environment. Do not intentionally fill the OS drive to a dangerous level.

For conversion, use representative Java↔Bedrock worlds and evaluate output quality, not only process exit code.

**PASS:** large-world operations fail early when capacity is insufficient, complete without orphaned transactional data when capacity is sufficient, and produce acceptable conversion results for representative content.

---

# Defect recording

For every failure, record this minimum block before changing source:

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

Capture screenshots/logs only when they add evidence. Do not fix multiple unrelated owners in one change.

Priority classification:

```text
P0  data loss / destructive corruption / unsafe install
P1  installer, Launcher, server, plugin or required client path unusable
P2  accepted feature broken but workaround exists
P3  polish / non-blocking UX issue
```

---

# Local PC STOP rule

Do not promote `Local` to `main` merely because remote CI is green.

The Local PC phase is ready for final audit/promotion discussion only when:

```text
installer clean install/update works
AND Launcher first-run works
AND managed Java/Paper works
AND Plugin Manager representative flow works
AND World Manager accepted flow works
AND Utilities Manager real-player flow works
AND Client Setup works against real Modrinth profile
AND Map Manager works in real Minecraft
AND Utility Manager works in real Minecraft
AND disconnect/reconnect/restart matrix is acceptable
AND representative large-world/storage/conversion tests are acceptable
AND no unresolved P0/P1 defect remains
```

After that, perform one final repository audit/cleanup. Only then decide explicitly whether the validated `Local` revision should be promoted to `main`.
