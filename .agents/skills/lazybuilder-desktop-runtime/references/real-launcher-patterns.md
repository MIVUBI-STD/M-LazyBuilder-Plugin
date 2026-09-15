# Production Launcher Source Patterns

Use this reference only when a Launcher design needs comparison against mature production-source patterns. It is not product authority and not a template to copy. LazyBuilder source/docs/Skill remain canonical.

## Selected external references

The set is intentionally small and complementary:

```text
PrismLauncher/PrismLauncher
→ reusable task lifecycle, startup centralization, settings, bounded logs

gorilla-devs/GDLauncher-Carbon
→ updater state machine, update lock, queryable state + events, controlled test feed

HydroRoll-Team/DropOut
→ Tauri/Rust authority, backend readiness, bounded library probing, managed-download semantics

ATLauncher/ATLauncher
→ practical regression-test discipline for filesystem/launcher behavior
```

Use only the pattern that answers the current question. Do not rebuild LazyBuilder to resemble another launcher.

## Pattern map

### Long-running execution

Production pattern:

```text
one operation identity
→ explicit lifecycle
→ queryable snapshot
→ semantic progress
→ safe cancel/retry
→ restart reconciliation
```

LazyBuilder adoption:

- domain services remain semantic owners;
- shared operation state may coordinate execution/presentation but must not become a duplicate domain database;
- a reopened UI can query current state without replaying old events;
- complex work may expose named steps rather than fake percentages.

Avoid one feature-specific task manager per capability.

### Application startup

Production pattern:

```text
one startup coordinator
→ environment/logging
→ settings migration
→ transaction/recovery reconciliation
→ process reconciliation
→ bounded provider initialization
→ UI ready
```

LazyBuilder adoption:

- startup side effects should not be scattered across unrelated commands/components;
- irreversible cleanup waits until recovery metadata is loaded;
- local usable state should not unnecessarily wait on optional network work.

### Readiness / health

Production pattern:

```text
one backend readiness result
→ many presentation consumers
```

LazyBuilder adoption:

- Rust computes canonical server readiness/health;
- Server Library, Overview, Start/Repair eligibility and future surfaces consume the same result;
- large libraries use bounded/progressive health probing instead of probing everything synchronously.

### Self-update

Production pattern:

```text
one updater state machine
+ one updater lock
+ queryable current state
+ progress/error details
+ explicit release channel policy
```

LazyBuilder adoption:

```text
Idle
Checking
Available
Downloading
Verifying
ReadyToInstall
Installing
RestartRequired
NoUpdate
Failed
```

Keep channels minimal (`Stable`, `Preview` only when actually maintained). Production must not permit arbitrary update-feed replacement; test feeds belong to explicit test/E2E boundaries.

### Managed downloads

Production pattern:

```text
staging / partial download
→ progress
→ checksum/signature verification
→ safe cancellation when supported
→ atomic promotion
```

Apply to LazyBuilder-owned managed runtime/update downloads, not as a reason to absorb arbitrary plugin/mod download ownership.

### Observability

Production pattern:

```text
bounded rotating logs
structured error categories
bounded operation history
support export
version/build/channel context
```

Diagnostics must remain sanitized and must not become another state database.

### Regression testing

Useful baseline:

```text
changed lifecycle/state behavior → focused regression test
filesystem behavior             → isolated temp root
negative path guards            → explicit failure tests
interrupted/recovery state       → explicit reconciliation tests
```

## Selection table

```text
execution lifecycle / progress / cancel / retry
→ operation pattern

server status / start eligibility / repair recommendation
→ readiness pattern

startup/recovery ordering
→ startup coordinator pattern

launcher self-update
→ updater state-machine pattern

managed runtime/update download
→ staged verified-download pattern

supportability
→ bounded observability pattern
```

If the task is only presentation, hand off to `lazybuilder-ui` instead of importing a launcher architecture pattern.

## Rejected patterns by default

Do not adopt merely because another launcher has them:

```text
account / skin / store / social systems
mod marketplace architecture
ads/telemetry architecture
system tray residency without a real workflow
cross-platform abstraction before current need
feature-specific task managers
frontend-owned durable launcher state
production-configurable arbitrary updater feed
source/assets/branding copied from another project
```

## Source-use rule

Study semantics, not implementation shape. Do not copy Qt/Electron/Tauri code, assets, branded UI, or project-specific APIs. External implementation is evidence that a pattern works at scale; LazyBuilder still implements it according to its own owners, stack, and licensing boundary.

## Stop rule

Once one external pattern clearly supports the existing LazyBuilder owner and proof plan, stop researching. Additional launcher comparisons are noise unless they can change the decision.