# Product

## Purpose

LazyBuilder simplifies the Minecraft Java builder-server toolchain by replacing difficult or legacy operational workflows with small, explicit, maintainable features.

## Current Product Flow

```text
Builder opens LazyBuilder client UI
→ World Manager lists canonical server worlds
→ user chooses one bounded operation
→ client sends one typed request
→ LazyBuilder server validates permission/state
→ existing application service performs the operation
→ canonical result returns to the client UI
```

The client provides interaction and presentation. The server plugin remains authoritative for world state and destructive actions.

## Current First Product Area

World Manager is the first confirmed rebuild target. Its durable feature contract is owned by `../02-world-management/README.md`.

The canonical client navigation/operation flow is owned by `../03-client-ui/world-manager-flow.md`. The general World Manager browser/screen is the next client-facing implementation slice; current Fabric source already implements the transfer and Xaero/map portions of that flow.

## Product Constraints

- Minecraft Java / Paper 1.21.4 target.
- Prefer native Paper/Bukkit APIs over NMS.
- Simple builder workflow takes precedence over preserving legacy command structures.
- Advanced settings are separated from default workflows.
- Third-party integrations are reused only where they clearly outperform rebuilding the same capability.
- One user action maps to one canonical application path; do not create command/UI/file-manager duplicates for the same operation.
