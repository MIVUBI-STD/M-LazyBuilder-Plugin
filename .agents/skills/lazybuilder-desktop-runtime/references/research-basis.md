# Launcher Engineering Research Basis

This file is provenance for `lazybuilder-desktop-runtime`, not an execution authority. Read it only when validating why a non-obvious launcher pattern exists or when refreshing the external research basis.

## External sources consulted

### Tauri application engineering

Source:

`https://github.com/kingsidharth/skills/blob/39fd028113a38f141b4b883f73537fd41e858289/tauri/SKILL.md`

Adopted conceptually:

```text
Rust/Tauri core vs WebView presentation boundary
typed IPC
minimum capability scope
runtime/business authority outside frontend
thin primary Skill + task-specific references
```

Not adopted: new-project/mobile/Next.js/plugin-catalog guidance.

### General desktop engineering

Source:

`https://github.com/majiayu000/claude-skill-registry/blob/b285fe7e23615da51788e755c0e005d3d3d8d0ed/skills/development/software-desktop/SKILL.md`

Adopted conceptually:

```text
desktop lifecycle wider than UI
local state + recovery are first-class
installer/signing/updater are architecture concerns
self-update needs authenticity verification
```

Not adopted: framework-selection or non-Windows platform guidance.

### Tauri packaging/distribution

Source:

`https://github.com/agents-inc/skills/blob/3a51ef571e996b18294bf776d53dbdad26de0617/src/skills/desktop-packaging-tauri/SKILL.md`

Adopted conceptually:

```text
NSIS as release boundary
one product/installer identity
verified updater artifacts
private keys outside repository
package proof distinct from real OS acceptance
```

### Production launcher repositories

Detailed reusable patterns are summarized in `real-launcher-patterns.md` from:

```text
PrismLauncher/PrismLauncher
gorilla-devs/GDLauncher-Carbon
HydroRoll-Team/DropOut
ATLauncher/ATLauncher
```

The useful synthesis is limited to:

```text
one operation lifecycle
one backend readiness authority
one startup/reconciliation path
one updater state machine
bounded observability
focused filesystem/lifecycle regression tests
```

No source/assets/product-specific APIs are copied.

## Skill-composition conclusion

Research supports the current structure:

```text
one semantic Skill: lazybuilder-desktop-runtime
+ selective task references
```

not separate Skills for Tauri, Rust, Windows packaging, updates, testing, or launcher framework mechanics.

The execution model remains:

```text
Svelte presentation
→ typed Tauri bridge
→ bounded Rust command
→ application/domain owner
→ platform/provider adapter
```

and long-running/recoverable work remains:

```text
one owner
→ explicit lifecycle
→ queryable state
→ safe retry/cancel semantics
→ restart reconciliation
→ matching proof
```

## Refresh rule

Update this provenance only when new external research materially changes an adopted rule. Do not turn it into a chronological research log or duplicate the detailed guidance already present in `SKILL.md` and the other references.