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

## 4. Skill composition principle

Research across public skills showed a recurring useful shape:

```text
thin semantic owner in SKILL.md
+ task-specific references/
+ explicit when-to-read routing
+ quality/proof gates
```

LazyBuilder uses this pattern to avoid both extremes:

```text
one tiny Skill that lacks framework expertise
OR
many overlapping framework Skills that compete for ownership
```

The chosen design is one existing semantic owner:

`lazybuilder-desktop-runtime`

with launcher-specific references for architecture, operations/recovery, Windows distribution/update, and quality gates.

## LazyBuilder-Specific Synthesis

The resulting engineering model is:

```text
Svelte presentation
→ canonical typed Tauri bridge
→ bounded Tauri commands
→ Rust application/domain service
→ platform/provider adapter
```

and:

```text
persistent/destructive operation
→ explicit ownership/state
→ staged mutation
→ verification
→ atomic publish when practical
→ restart reconciliation
→ bounded diagnostics
```

This synthesis is intentionally narrower than the external source material and aligned to LazyBuilder's actual stack, existing owners, Windows-first packaging, and local Minecraft server-manager product scope.
