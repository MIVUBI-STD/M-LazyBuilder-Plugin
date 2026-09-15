# Windows Distribution and Update Reference

Use for LazyBuilder executable identity, NSIS installer behavior, desktop/start-menu shortcuts, app signing, self-update, updater artifacts, release CI, and Windows-specific packaged behavior.

## Product Identity

One canonical product identity must flow through:

```text
Tauri productName
bundle identifier
executable metadata
installer metadata
icon.ico and generated icon assets
Start Menu shortcut
optional Desktop shortcut
taskbar / Alt+Tab identity
Installed Apps / uninstall entry
updater target identity
```

Do not maintain separate manually edited icons/names when generation from one source can prevent drift.

## Installer Policy

LazyBuilder uses Tauri + NSIS for the canonical Windows installer.

Professional default:

```text
Start Menu shortcut        always
Desktop shortcut           optional, default selected
Launch after install       optional finish-page action
Auto-start with Windows    OFF unless explicitly added as a product requirement
Permanent system tray      NO unless background residency becomes a real requirement
```

Avoid unnecessary installer choices that expose implementation details or developer tools.

## App Data vs Server Data

Keep distinct:

```text
APPLICATION DATA
%LOCALAPPDATA%\LazyBuilder\
settings / registry / updater metadata / diagnostics metadata / temp

SERVER WORKSPACES
user-selected locations
worlds / plugins / Paper runtime / server configuration
```

Uninstalling Launcher must not silently delete user server workspaces.

## Self-Update Architecture

Launcher self-update is not Paper/runtime update.

Recommended flow:

```text
check release metadata
→ verify channel/compatibility
→ download signed update artifact
→ verify updater signature/integrity
→ snapshot/migrate app metadata when schema risk requires it
→ stage update
→ request explicit restart/install
→ installer/updater applies replacement
→ restart Launcher
→ run post-update migration/reconciliation
→ mark update healthy
```

Use Tauri updater semantics rather than creating a second custom executable-replacement system unless an unavoidable requirement is proven.

## Update Channels

Keep channels minimal:

```text
Stable   default for users
Preview  optional only when we actually maintain and test it
```

Do not expose nightly/dev channels to normal users merely because CI builds exist.

Channel selection belongs to persistent Launcher settings and must affect both release lookup and user-facing version information.

## Signature / Key Safety

Updater artifacts require authenticity verification.

Rules:

- private signing/updater keys live in CI secrets or secure signing infrastructure, never repository files;
- public verification material may ship in the application;
- installer/update verification failure blocks activation;
- never offer a UI switch to disable authenticity verification;
- record safe diagnostic reason without leaking secret material.

## Windows Code Signing

Unsigned builds are acceptable for internal/local development proof but not an ideal public release posture.

Before public distribution, define a Windows Authenticode strategy and timestamping policy. Signing identity must match product/release ownership and CI should verify the produced installer/executable signature.

Do not confuse Tauri updater signature verification with Windows Authenticode: they protect different boundaries and a mature public release may use both.

## NSIS Customization

Prefer Tauri's canonical NSIS path and bounded hooks/templates when needed.

Typical reasons to customize:

```text
Desktop shortcut option
finish-page launch behavior
upgrade migration behavior
installer branding
pre/post install checks that Tauri config cannot express
```

Do not replace the entire installer pipeline for cosmetic control.

## WebView2

LazyBuilder depends on Windows WebView2 through Tauri.

Installer/package policy must explicitly define the minimum supported Windows versions and WebView2 installation strategy. Treat missing/corrupt WebView2 as an actionable Launcher prerequisite problem, not a generic blank-window failure.

## Release Artifacts

A canonical release build should be able to prove provenance:

```text
commit SHA
product version
channel
installer artifact
artifact digest
updater artifact/signature when enabled
diagnostic executable only when intentionally distributed
```

Avoid multiple differently-built installers for the same version/channel.

## CI Gates

For Launcher-changing commits, use layered gates:

```text
frontend typecheck/build
→ icon/resource generation
→ cargo check
→ cargo test
→ Tauri production build
→ NSIS installer build
→ canonical package assembly
→ installer smoke test
→ artifact upload/digest
```

Signing/updater publication is a release gate, not necessarily required for every development commit.

## Installer Smoke Test

Automated smoke should verify what is practical without pretending to prove human-visible Windows behavior.

Can prove:

```text
installer exists/non-empty
expected naming/version
unattended install can complete when supported
expected executable/resources installed
basic executable startup contract
uninstall metadata/command presence when inspectable
```

Requires real Windows acceptance proof:

```text
Desktop shortcut checkbox UX
Start Menu discoverability
taskbar/Alt+Tab icon rendering
Windows Search identity
upgrade from prior installed version
SmartScreen/signing reputation behavior
finish-page launch experience
uninstall user-data behavior
```

## Update Failure / Rollback

Design before enabling automatic update:

```text
download failure            → current app untouched
signature failure           → current app untouched, surface actionable error
install interrupted         → installer/updater recovery path documented
settings migration fails    → preserve previous-valid app metadata and enter repair/recovery
new build unhealthy         → enough prior metadata/version evidence exists to diagnose/roll back according to release policy
```

Do not claim rollback exists unless an actual previous-version restoration path is implemented and tested.

## Release Checklist

```text
[ ] product version consistent across package/Rust/Tauri metadata
[ ] bundle identifier stable
[ ] canonical icon generated and bundled
[ ] Start Menu identity correct
[ ] Desktop shortcut policy correct
[ ] updater channel/endpoint/signature configuration valid when enabled
[ ] private keys absent from repository/artifacts/logs
[ ] installer artifact has digest/provenance
[ ] clean install smoke passes
[ ] upgrade scenario tested before release
[ ] uninstall preserves server workspaces
[ ] real Windows visual/interaction acceptance scheduled for release candidate
```
