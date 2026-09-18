# Product

## Purpose

LazyBuilder simplifies the Minecraft Java builder-server toolchain by replacing difficult or legacy operational workflows with small, explicit, maintainable features.

## Current Product Flow

```text
LazyBuilder Desktop
├── Server-Manager / Plugin-Manager
├── Client Setup → selected Modrinth profile
└── authenticated World-Manager desktop control

Minecraft client
→ first-party LazyBuilder Fabric Managers
→ World-Manager validates permission/state
→ canonical application service performs the operation
→ canonical result returns to the client UI
```

The desktop and Fabric client provide interaction and presentation. World-Manager remains authoritative for managed-world state and destructive world actions; Utilities-Manager independently owns small server-side builder convenience behavior.

## Current Product Areas

### World Manager

The first-party Fabric World Manager surface is source-implemented for:

```text
World list / refresh
Create Flat / Void
Teleport
automatic runtime load + idle unload
Archive / Restore
Duplicate
General Settings
Permanent Delete
Import upload → inspection → review → explicit Import
Native Java 1.21.4 whole-world Export
first-party chunk-aligned Export Area selection
```

Manual Load/Unload controls, `autoLoad`, and legacy Clone terminology are not part of the current product path. Managed worlds load when required by canonical operations and become eligible for idle unload when safe.

Map interaction and Export Area selection are first-party LazyBuilder surfaces. Xaero or other external map tools may remain interaction-quality references or optional user tools, but they are not runtime owners or required dependencies for LazyBuilder world/map operations. File bytes use the existing transfer path rather than a second upload/download subsystem.

The durable world feature contract is owned by `../02-world-management/README.md`. The canonical client navigation/operation flow is owned by `../03-client-ui/world-manager-flow.md`.

### Utilities Manager

Current locked source scope is deliberately small:

```text
World Safety
Movement
Build Helpers
```

Creation Tools and duplicate custom Spectator controls are not part of the current product scope. Utilities details are owned by `../../plugins/utilities-manager/README.md` and the architecture lock under `../04-system/`.

### Core Client Managers

The bundled V1 Fabric client suite remains exactly three Managers:

```text
Map Manager          world / map / transfer workflow
Utility Manager      passive non-build client convenience
Performance Manager  performance/resource coordination baseline
```

These three Managers are installed and repaired together by Client Setup. Their ownership remains independent.

### Builder Utilities extension

`mods/builder-utilities/` is a separate Axiom-first builder extension lane. It is not a fourth core Manager and is not part of the V1 Client Setup transaction.

```text
Axiom
→ familiar primary editor / interaction owner
→ Builder Utilities
→ only capabilities that materially extend the Axiom workflow
```

Builder Utilities owns its own bounded operation lifecycle, deterministic planning, cancellation, history/recovery, runtime metrics and extension-specific mutation logic. It must not duplicate Axiom's primary editor UX or create a second generic build platform.

FAWE, ezEdits and other established tools may be studied as implementation/problem references, but they are not permanent Builder Utilities runtime authorities.

`mods/terraform-manager/` and `plugins/terraform-manager/` are legacy/prototype terrain lanes under retirement evaluation. They are not current V1 product components and must not be expanded in parallel with Builder Utilities unless a distinct non-overlapping ownership requirement is proven.

### Desktop

`LazyBuilder.exe` owns Server-Manager, Plugin-Manager, and Client Setup desktop responsibilities. It presents World-Manager through the authenticated local control bridge and does not duplicate Paper business logic.

### Client Setup / Modrinth

Modrinth App remains the Minecraft launcher and profile/modpack owner. LazyBuilder does not create Minecraft instances, launch Minecraft, or manage third-party mods.

Client Setup has one bounded responsibility:

```text
auto-detect known Modrinth profile roots
→ user chooses a detected profile
   or manually selects the exact profile folder
→ persist the canonical absolute profile path
→ revalidate that exact path whenever Client Setup is opened or refreshed
→ derive <profile>/mods internally
→ verify Minecraft 1.21.4 + Fabric from profile-local evidence
→ inspect LazyBuilder-owned client components
→ Sync Client installs/updates/repairs only:
   lazybuilder-map-manager-*.jar
   lazybuilder-utility-manager-*.jar
   lazybuilder-performance-manager-*.jar
```

Custom Modrinth data directories are supported through manual profile selection. The selected folder must be the exact profile under a `profiles` directory; users never select the `mods` directory directly. LazyBuilder remembers both the canonical selected profile and its `profiles` root so sibling profiles at that custom location can be discovered later. If the selected profile is moved or renamed, LazyBuilder reports it as missing and requires an explicit new selection rather than creating or guessing a replacement path.

Other files in the selected profile `mods/` directory are never modified. Before Sync Client writes anything, the selected profile is revalidated, compatibility is rechecked, the derived mods directory must be writable, and all three bundled LazyBuilder client JARs must be available. There is no background watcher; status is refreshed when the Client Setup surface is opened/refreshed or Sync Client is requested.

Runtime-ready Launcher packages bundle the three tested Fabric JARs from the same source revision as the desktop package. Current Modrinth instance metadata is not treated as a LazyBuilder authority: LazyBuilder avoids coupling to Modrinth's private database schema and verifies the selected profile from profile-local runtime evidence, with legacy `profile.json` support only as a compatibility fallback. The UI labels whether compatibility came from the last launch, legacy metadata, or remains unverified.

## Product Constraints

- Minecraft Java / Paper 1.21.4 target.
- Prefer native Paper/Bukkit APIs over NMS.
- Simple builder workflow takes precedence over preserving legacy command structures.
- Advanced settings are separated from default workflows.
- Third-party integrations are reused only where they clearly outperform rebuilding the same capability.
- Modrinth remains authoritative for Minecraft profiles, loader installation, modpacks, general mods, and game launching.
- LazyBuilder Client Setup may mutate only LazyBuilder-owned Fabric JAR prefixes inside an explicitly selected compatible profile.
- Client Setup must never guess, recreate, or silently migrate a missing selected Modrinth profile path.
- One user action maps to one canonical application path; do not create command/UI/file-manager duplicates for the same operation.
- Runtime storage is intentionally minimal: create only directories with an active owner.
- Source/CI proof is not live-server proof; new feature scope stays closed until local/live validation reveals a concrete need.
