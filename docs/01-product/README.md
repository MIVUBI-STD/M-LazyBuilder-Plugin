# Product

## Purpose

LazyBuilder simplifies the Minecraft Java builder-server toolchain by replacing difficult or legacy operational workflows with small, explicit, maintainable features.

## Current Product Flow

```text
Builder opens LazyBuilder client UI
→ World Manager lists canonical server worlds
→ user chooses one bounded operation
→ client sends one typed request
→ World-Manager validates permission/state
→ canonical application service performs the operation
→ canonical result returns to the client UI
```

The client provides interaction and presentation. World-Manager remains authoritative for world state and destructive actions; Utilities-Manager independently owns small builder convenience behavior.

## Current Product Areas

### World Manager

The first-party Fabric World Manager surface is source-implemented for:

```text
World list / refresh
Create Flat / Void
Teleport
Load / Unload
Archive / Restore
Clone
General Settings
Permanent Delete
Import publication
Native Java 1.21.4 whole-world Export
```

Xaero remains contextual for `Teleport Here` and `Export Area`. File bytes use the existing transfer path rather than a second upload/download subsystem.

The durable world feature contract is owned by `../02-world-management/README.md`. The canonical client navigation/operation flow is owned by `../03-client-ui/world-manager-flow.md`.

### Utilities Manager

Current locked source scope is deliberately small:

```text
World Safety
Movement
Build Helpers
```

Creation Tools and duplicate custom Spectator controls are not part of the current product scope. Utilities details are owned by `../../modules/utilities-manager/README.md` and the architecture lock under `../04-system/`.

### Desktop

`LazyBuilder.exe` owns Server-Manager and Plugin-Manager desktop responsibilities and presents World-Manager through the authenticated local control bridge. It does not duplicate Paper business logic.

## Product Constraints

- Minecraft Java / Paper 1.21.4 target.
- Prefer native Paper/Bukkit APIs over NMS.
- Simple builder workflow takes precedence over preserving legacy command structures.
- Advanced settings are separated from default workflows.
- Third-party integrations are reused only where they clearly outperform rebuilding the same capability.
- One user action maps to one canonical application path; do not create command/UI/file-manager duplicates for the same operation.
- Runtime storage is intentionally minimal: create only directories with an active owner.
- Source/CI proof is not live-server proof; new feature scope stays closed until local/live validation reveals a concrete need.
