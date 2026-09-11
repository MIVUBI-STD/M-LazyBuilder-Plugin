# Product

## Purpose

LazyBuilder simplifies the Minecraft Java builder-server toolchain by replacing difficult or legacy operational workflows with small, explicit, maintainable features.

## Current Product Flow

```text
Builder opens LazyBuilder client UI
→ selects world/map operation
→ client sends bounded request
→ LazyBuilder server validates authority and state
→ Paper API performs world/server mutation
→ result returns to client UI
```

The client provides interaction and presentation. The server plugin remains authoritative for world state and destructive actions.

## Current First Product Area

World Manager is the first confirmed rebuild target. Its durable feature contract is owned by `../02-world-management/README.md`.

## Product Constraints

- Minecraft Java / Paper 1.21.4 target.
- Prefer native Paper/Bukkit APIs over NMS.
- Simple builder workflow takes precedence over preserving legacy command structures.
- Advanced settings are separated from default workflows.
- Third-party integrations are reused only where they clearly outperform rebuilding the same capability.
