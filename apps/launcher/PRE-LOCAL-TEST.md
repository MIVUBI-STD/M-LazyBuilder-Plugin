# LazyBuilder Launcher — Pre-Local Test Gate

This checklist is the runtime handoff gate for the desktop Launcher, its bundled Paper core, and the LazyBuilder Fabric client components.

## Source gate

Before moving to the local PC, the current `Local` HEAD must pass the exact-head `Verify` workflow:

```text
consistency
utilities
paper
fabric
launcher-check
tauri-desktop
```

Launcher checks include:

```text
npm ci
npm run typecheck
npm run build:frontend
npm run prepare:icons
cargo check --locked --manifest-path src-tauri/Cargo.toml
cargo test --locked --manifest-path src-tauri/Cargo.toml
```

Remote CI is source/build/package proof only. It is not proof of installed Windows, Modrinth, Paper, or Minecraft behavior.

## Runtime-ready package gate

A normal runtime-ready package must contain matching tested artifacts from the same source revision:

```text
src-tauri/resources/core/
├── World-Manager-0.1.0-SNAPSHOT.jar
└── Utilities-Manager-0.1.0-SNAPSHOT.jar

src-tauri/resources/client-mods/
├── lazybuilder-map-manager-0.1.0-SNAPSHOT.jar
├── lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar
└── lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar
```

`BUILD-LAUNCHER.cmd` owns the complete local runtime-ready path:

```text
mvn verify
→ build Map Manager with Gradle 8.12
→ build Utility Manager with Gradle 8.12
→ build Performance Manager with Gradle 8.12
→ stage tested Paper + Fabric JARs
→ npm ci
→ Svelte typecheck/build
→ cargo check/test
→ Tauri + NSIS package
```

The build must stop if any required runtime artifact is missing. Compile-only mode must not be used for runtime validation.

## First local install

Run:

```text
BUILD-LAUNCHER.cmd
```

Install only:

```text
dist/LazyBuilder/LazyBuilder-Setup.exe
```

Expected baseline:

- one LazyBuilder window only;
- no Launcher console window;
- no server auto-start;
- Java validation does not open Command Prompt;
- starting Paper does not open Command Prompt;
- server logs remain accessible from LazyBuilder.

## Server library and process safety

Test in this order:

1. Create/select Server A and prepare it.
2. Start Paper and confirm `Running`.
3. Stop Paper and confirm `Offline`.
4. Start again and click Stop while state is still `Starting`; confirm it returns safely to `Offline`.
5. Simulate an abnormal Launcher exit while Paper remains alive.
6. Reopen LazyBuilder and confirm a second workspace cannot be opened/created/adopted while the detached Paper process remains alive.
7. Recover/stop the detached process.
8. Temporarily disconnect or rename the storage location of a registered inactive server, reopen LazyBuilder, and confirm the server remains in the library instead of being silently deleted.
9. Restore that location and confirm the same library entry opens again.

## Existing-server adoption safety

Test with a disposable Paper server:

1. Start the server outside LazyBuilder and attempt adoption.
2. Confirm LazyBuilder blocks adoption while a Java/Paper process is using the selected root.
3. Stop the external server.
4. Repeat adoption and confirm the review/rollback-safe migration flow proceeds.
5. Confirm unrecognized root files remain untouched.

## Modrinth Client Setup

Client Setup is global, not server-scoped. Test both a default Modrinth location and a custom data location.

1. Open `Client` before opening any LazyBuilder server.
2. Confirm known Modrinth profiles are auto-detected when available.
3. Use `Select profile…` for a custom Modrinth location.
4. Select the exact `.../profiles/<profile>` folder; confirm LazyBuilder derives `<profile>/mods` itself.
5. Confirm the exact canonical profile path is remembered after restarting LazyBuilder.
6. Confirm Minecraft 1.21.4 + Fabric is shown as `Last launch`, `Legacy metadata`, or `Unverified` rather than being presented as current Modrinth database state.
7. If unverified, launch that profile once from Modrinth, return to LazyBuilder, click Refresh, and confirm compatibility is detected.
8. While Minecraft is actively using the selected profile, click `Sync Client`; confirm sync is blocked.
9. Close Minecraft and run `Sync Client`.
10. Confirm exactly these three current JARs are present:

```text
lazybuilder-map-manager-*.jar
lazybuilder-utility-manager-*.jar
lazybuilder-performance-manager-*.jar
```

11. Confirm unrelated Modrinth mods are byte-for-byte untouched.
12. Add an old/duplicate LazyBuilder JAR and sync again; confirm only LazyBuilder-owned prefixes are cleaned.
13. Rename or move the selected profile; confirm LazyBuilder reports the saved profile as unavailable and does not recreate the old path.
14. Re-select the new location and confirm setup recovers.

## Client Sync rollback proof

Use a disposable profile copy. Induce a publish failure after staging if practical (for example by restricting one LazyBuilder target during the operation). Confirm:

```text
sync fails
→ previous three LazyBuilder components are restored
→ no partial mixed LazyBuilder version set remains
→ third-party mods remain untouched
→ temporary .incoming / rollback artifacts are cleaned
```

## Plugin Manager smoke test

With Paper offline:

- install one third-party plugin;
- update it;
- disable/enable it;
- exercise duplicate resolution with disposable copies;
- remove its JAR and confirm plugin data is preserved;
- confirm World-Manager and Utilities-Manager cannot be removed through Plugin Manager.

Repeat one mutation attempt while Paper is running and confirm it is blocked.

## Clean update test

After the first install, close LazyBuilder and use:

```text
UPDATE-LAUNCHER.cmd
```

Confirm:

- Paper core and all three Fabric components are rebuilt/tested/staged before packaging;
- update is blocked while LazyBuilder is running;
- installed application is replaced in place;
- server workspaces and selected Modrinth profile remain intact;
- temporary update output does not accumulate.

## Pass condition

Local testing is clean only when:

```text
exact-head Verify green
+ runtime-ready package contains matching Paper + Fabric artifacts
+ server library survives unavailable paths
+ Starting server can be stopped
+ one managed Paper instance only
+ adoption refuses a live external server
+ global Modrinth Client Setup works for default + custom paths
+ Client Sync is transactional and leaves third-party mods untouched
+ installed update preserves user data
+ no stale temporary runtime/update artifacts remain
```
