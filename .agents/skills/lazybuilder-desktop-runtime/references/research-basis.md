# Launcher Engineering Research Basis

This file records the external skill/repository patterns used to strengthen `lazybuilder-desktop-runtime`. It is provenance/context, not a second authority. LazyBuilder source/docs/Skill remain canonical for this repository.

## 1. Tauri application development skill

Source:

`https://github.com/kingsidharth/skills/blob/39fd028113a38f141b4b883f73537fd41e858289/tauri/SKILL.md`

Useful patterns adopted conceptually:

```text
Tauri Rust core vs WebView process model
frontend → typed IPC → Rust authority
minimum Tauri capability/permission scope
business/runtime logic stays out of web presentation
separate detailed references from thin primary SKILL.md
icons/build/distribution considered part of desktop engineering
```

Not copied as-is:

```text
mobile/iOS/Android guidance
new-project scaffolding guidance
Next.js-specific setup
generic Tauri plugin catalog
```

LazyBuilder is an existing Windows-first Tauri/Svelte product, so generic framework setup would add noise.

## 2. General desktop application engineering skill

Source:

`https://github.com/majiayu000/claude-skill-registry/blob/b285fe7e23615da51788e755c0e005d3d3d8d0ed/skills/development/software-desktop/SKILL.md`

Useful patterns adopted conceptually:

```text
desktop lifecycle is broader than UI
local/offline state is first-class
installer + signing + updater are application architecture concerns
platform behavior and crash/recovery belong in release quality
self-update needs authenticity verification
Windows distribution requires explicit installer/signing policy
```

Not adopted:

```text
framework-selection decision tree
Electron/Flutter/MAUI implementation guidance
macOS/Linux distribution procedures
```

LazyBuilder already chose Tauri and currently targets the Windows workflow first.

## 3. Tauri packaging/distribution skill

Source:

`https://github.com/agents-inc/skills/blob/3a51ef571e996b18294bf776d53dbdad26de0617/src/skills/desktop-packaging-tauri/SKILL.md`

Useful patterns adopted conceptually:

```text
NSIS treated as a product/release boundary
one product identifier/icon/installer identity
Tauri updater artifacts require signed verification
private updater/signing keys belong in secure CI secrets
packaging proof differs from real OS acceptance proof
release artifact provenance and CI gates matter
```

Not adopted blindly:

```text
cross-platform release matrix
macOS notarization specifics
Linux package formats
sidecar guidance unrelated to current LazyBuilder needs
```

## 4. Production launcher source references

Generic framework skills are useful, but production launcher behavior is better grounded by real launchers. The following repositories were selected because each contributes a different mature pattern.

### PrismLauncher

Source:

`https://github.com/PrismLauncher/PrismLauncher`

Useful source areas:

```text
launcher/tasks/Task.h
launcher/Application.cpp
launcher/settings/*
launcher/InstanceList.*
launcher/updater/*
```

Patterns adopted conceptually:

```text
one reusable task lifecycle for long-running work
explicit status/details/current/total/warnings/abortability
multi-step progress
centralized application startup/lifecycle
bounded launcher log rotation
central settings authority
single-instance application behavior
```

LazyBuilder does not adopt Qt classes or Prism's instance/game architecture. The value is the semantic separation between application lifecycle, reusable task execution, settings, and UI.

### GDLauncher Carbon

Source:

`https://github.com/gorilla-devs/GDLauncher-Carbon`

Useful source areas:

```text
apps/desktop/packages/main/autoUpdater.ts
apps/desktop/packages/preload/autoupdate.ts
apps/desktop/packages/mainWindow/src/utils/updater.tsx
```

Patterns adopted conceptually:

```text
updater represented as explicit finite state
single updater lock
release-channel policy
current updater state is queryable
state changes are broadcast to presentation
progress and structured errors are first-class
E2E can use a controlled test update feed
```

LazyBuilder will implement equivalent semantics in Rust/Tauri rather than Electron IPC.

### DropOut

Source:

`https://github.com/HydroRoll-Team/DropOut`

This is especially relevant because it is a Minecraft launcher using Tauri 2 with a Rust core and web UI.

Useful source areas:

```text
src-tauri/src/core/downloader.rs
src-tauri/src/main.rs
packages/ui/src/lib/launch-readiness.ts
packages/ui/src/client.ts
packages/ui/src/pages/instances/*
packages/docs/content/en/development/*
```

Patterns adopted conceptually:

```text
Rust owns side effects and launcher truth
backend-authoritative launch/readiness checks
same readiness contract reused by multiple UI surfaces
progressive/bounded checks for large instance libraries
resumable managed downloads
progress events with bytes/speed/ETA where meaningful
safe cancellation
checksum verification before promotion
```

LazyBuilder should apply these patterns to server readiness, managed runtime/update downloads, and Server Library health without copying DropOut's game/account feature set.

### ATLauncher

Source:

`https://github.com/ATLauncher/ATLauncher`

Useful source:

`TESTING.md`

Pattern adopted conceptually:

```text
changed behavior gets a regression test
filesystem tests use isolated temporary storage
```

LazyBuilder does not adopt ATLauncher's older Java architecture. The useful contribution is simple repeatable testing discipline for file-based launcher behavior.

Detailed synthesis and adoption rules live in:

`references/real-launcher-patterns.md`

## 5. Skill composition principle

Research across public skills and launcher repositories showed a recurring useful shape:

```text
thin semantic owner in SKILL.md
+ task-specific references/
+ explicit when-to-read routing
+ quality/proof gates
+ real production source patterns
```

LazyBuilder uses this pattern to avoid both extremes:

```text
one tiny Skill that lacks framework expertise
OR
many overlapping framework Skills that compete for ownership
```

The chosen design is one existing semantic owner:

`lazybuilder-desktop-runtime`

with launcher-specific references for architecture, operations/recovery, Windows distribution/update, quality gates, and production launcher patterns.

## LazyBuilder-Specific Synthesis

The resulting engineering model is:

```text
Svelte presentation
→ canonical typed Tauri bridge
→ bounded Tauri commands
→ Rust application/domain service
→ platform/provider adapter
```

Long-running work should follow:

```text
one operation authority
→ explicit lifecycle
→ queryable snapshot
→ typed progress events
→ safe cancel/retry rules
→ restart reconciliation
```

Readiness should follow:

```text
one Rust health/readiness authority
→ Server Library
→ Overview
→ Start/Repair eligibility
→ future launcher surfaces
```

Persistent/destructive work should follow:

```text
explicit ownership/state
→ staged mutation
→ verification
→ atomic publish when practical
→ restart reconciliation
→ bounded diagnostics
```

Application startup should converge toward:

```text
runtime environment
→ logging
→ settings migration
→ filesystem transaction recovery
→ process reconciliation
→ operation reconciliation
→ updater initialization
→ server registry
→ bounded health refresh
→ UI ready
```

This synthesis is intentionally narrower than the external source material and aligned to LazyBuilder's actual stack, existing owners, Windows-first packaging, and local Minecraft server-manager product scope.
