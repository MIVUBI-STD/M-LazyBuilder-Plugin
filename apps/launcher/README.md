# LazyBuilder Launcher

Canonical desktop application source for LazyBuilder.

## Location

```text
apps/launcher/
```

## Stack

```text
Tauri 2
Svelte 5
Vite
TypeScript
Tailwind CSS 4
Rust
```

## Ownership

```text
src/                    presentation + application state
src/app/bridge/         typed Tauri command boundary
src-tauri/src/commands/ thin Tauri command adapters
src-tauri/src/engine/   desktop-native runtime/domain logic
```

Minecraft world authority remains in `plugins/world-manager/`. The Launcher may call its authenticated loopback bridge, but it must not duplicate world lifecycle, transfer, conversion, or filesystem ownership.

## Client Setup / Modrinth

`Client` is a global Launcher surface; it does not belong to one server workspace. LazyBuilder does not replace Modrinth App and does not launch Minecraft itself. The desktop `client_integration` owner maintains the LazyBuilder Fabric components required by V1 in one explicitly selected Modrinth profile.

```text
Modrinth owns
- Minecraft installation/profile
- Fabric loader
- general mods/modpacks
- launching Minecraft

LazyBuilder V1 owns
- lazybuilder-map-manager-*.jar
- lazybuilder-utility-manager-*.jar
- lazybuilder-performance-manager-*.jar
- compatibility/status check for Minecraft 1.21.4 + Fabric
- install/update/duplicate cleanup for those three prefixes only
```

Client Setup scans known Modrinth profile locations when the global Client surface is opened/refreshed. Custom locations are selected by choosing the exact `.../profiles/<profile>` folder; LazyBuilder derives `<profile>/mods` itself. There is no background watcher.

The canonical Client Setup config is stored under `%LOCALAPPDATA%\LazyBuilder\config\client-integration.json` (with `%APPDATA%` only as an environment fallback). Existing legacy `%APPDATA%\LazyBuilder\client-integration.json` data is migrated automatically when found.

`Sync Client` is transactional across the required LazyBuilder components: new JARs are staged and verified first, current LazyBuilder-owned JARs are backed up, and a failed publish restores the previous set. Third-party mod files are never part of the transaction. Sync is blocked while a running Java/Minecraft process is using the selected profile.

## Server runtime safety

Server library entries are durable even when their storage path is temporarily unavailable; opening the unavailable entry reports the path problem instead of deleting it from the library. A server may be stopped while still in `Starting` state. Paper startup stdout/stderr is captured in the workspace LazyBuilder logs until Paper reaches `Done`, after which the normal Paper `latest.log` is the primary UI log.

Adopting a plain Paper server performs a best-effort live Java process check before any migration so files are not moved while that server is running.

Server start does not rewrite Paper gameplay/performance configuration and the Launcher does not run a background CPU-priority governor. Performance tuning must be justified by local/runtime evidence before becoming product behavior.

## Developer operations

Launcher development uses the repository-wide developer command surface. Do not introduce Launcher-specific root shortcuts or a second task runner.

From repository root:

```text
DEV.cmd setup
DEV.cmd check
DEV.cmd build
DEV.cmd test
DEV.cmd update
DEV.cmd finalize-local
```

`toolchain.json` is the canonical build-tool policy. The root orchestrator delegates Launcher build semantics to `apps/launcher/build-local.ps1`; that script is an implementation owner, not a second developer-facing command surface.

Normal runtime-ready build:

```text
DEV.cmd build
→ toolchain preflight
→ repository Maven wrapper verify
→ repository Gradle wrapper builds the required Fabric suite
→ stage + verify matching Paper/Fabric artifacts
→ npm ci
→ Svelte typecheck
→ cargo check/test with locked dependencies
→ Tauri + NSIS build
→ canonical Local package publication
```

Repository wrappers cache checksum-verified Maven/Gradle distributions under LazyBuilder-owned LocalAppData paths. Global Maven and Gradle are not developer requirements.

Runtime-ready output:

```text
dist/Local/
├── LazyBuilder-Setup-Local.exe
├── LazyBuilder-Diagnostics.exe
├── build-info.json
├── SHA256SUMS.txt
└── README.txt
```

CI may add exact-run provenance to the same package contract. Compile-only diagnostics, when explicitly requested through the internal build owner, are isolated under `dist/CompileOnly/` and must not be treated as acceptance candidates.

Use `DEV.cmd update` for repeated installed-app testing. Server workspaces, selected Modrinth profile, and normal LazyBuilder user data are preserved by the owning update path.

Required developer foundations are validated by `DEV.cmd check`; see `tooling/windows-toolchain/README.md` and `docs/04-system/development-operations.md` for the canonical operational contract.

## Bundled runtime resources

Runtime-ready builds require tested artifacts from the same source revision:

```text
src-tauri/resources/core/
├── World-Manager-0.1.0-SNAPSHOT.jar
└── Utilities-Manager-0.1.0-SNAPSHOT.jar

src-tauri/resources/client-mods/
├── lazybuilder-map-manager-0.1.0-SNAPSHOT.jar
├── lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar
└── lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar
```

Generated JARs are staged by local build/CI and are not committed.

Direct `build-local.ps1 -AllowMissingRuntime` use is reserved for explicit Launcher compile diagnostics. It is not the normal developer flow and cannot satisfy runtime/installer acceptance.

## Verification boundary

Current remote/local readiness is owned by `docs/05-operations/current-verification.md`. Do not maintain a Launcher-local acceptance authority in this directory.

Remote build/package proof does not replace target-machine behavior such as installed Windows environment, long-lived workspaces, real Modrinth/Fabric interaction, or other native acceptance boundaries.

## Protocol boundary

The Launcher expects desktop loopback protocol version `2`. This contract is separate from the Minecraft World Control V5 and Map Action V2 protocols.
