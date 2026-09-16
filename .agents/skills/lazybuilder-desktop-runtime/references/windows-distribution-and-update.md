# Windows Distribution and Update Reference

Use for LazyBuilder executable identity, NSIS installer behavior, release artifacts, signing, update-channel publication, and any future in-app self-update runtime.

## Temporal status

This reference mixes stable distribution rules with current implementation facts.

```text
STABLE_RULE
→ one product identity
→ signing/private-key safety
→ application-data vs server-workspace ownership
→ publication/runtime separation
→ proof boundaries

IMPLEMENTATION_SNAPSHOT
→ current NSIS/Tauri/WebView2 configuration
→ current release workflow shape
→ current dependency presence/absence
→ current channel support
→ current bundle identifiers and installer policy
```

Before making a current-state claim or changing packaging/update behavior, verify the relevant snapshot against the exact Tauri config, Launcher dependencies, and release workflow on the current branch. Newer source overrides this reference; update this file when the snapshot materially changes. Do not keep an old dependency absence, workflow name, channel assumption, or installer option alive as a compatibility rule.

## Current implementation boundary

Current repository behavior already includes:

```text
Tauri + NSIS Windows packaging
currentUser install mode
WebView2 download-bootstrapper strategy
canonical product identity/icons
manual stable Launcher release workflow on main
signed NSIS updater artifact generation
stable update manifest publication
release SHA-256 checksums
versioned immutable GitHub release assets
```

Current source does **not** yet include `tauri-plugin-updater` in the Launcher Rust dependencies. Therefore:

```text
release/update-channel publication = implemented
in-app self-update runtime          = not current implementation unless source later adds it
```

Do not describe the Launcher as having working automatic self-update merely because signed update artifacts/channel metadata are published.

## Product identity

One canonical identity must remain aligned across:

```text
Tauri productName
bundle identifier
publisher/executable metadata
installer metadata
canonical icon source/generated assets
Start Menu / taskbar / Alt+Tab identity
Installed Apps / uninstall identity
future updater target identity
```

Current Tauri identity is LazyBuilder / `com.halokaryamedia.lazybuilder` with NSIS as the Windows bundle target.

## Installer policy

Current packaged policy is source-authoritative. Do not invent installer options that are not present in Tauri/NSIS config.

Current key facts include:

```text
NSIS target
currentUser install mode
English installer language
WebView2 download bootstrapper, silent
no automatic Windows auto-start requirement
```

Desktop shortcut, finish-page launch behavior, tray residency, or other installer UX belongs here only if source/product requirements actually introduce it.

## App data vs server data

Keep these separate:

```text
APPLICATION DATA
%LOCALAPPDATA%\LazyBuilder\
settings / registry / diagnostics / temp / update metadata

SERVER WORKSPACES
user-selected locations
worlds / plugins / Paper runtime / server configuration
```

Launcher uninstall/update must not silently delete user server workspaces.

## Current release publication flow

The current stable release workflow is explicit and manual on `main`:

```text
verify requested version against source
→ require signing secrets
→ verify Launcher source
→ reject duplicate version release
→ build signed NSIS updater artifact
→ resolve installer + .sig
→ build/verify stable latest.json
→ write SHA-256 checksums
→ publish immutable versioned GitHub release
→ publish stable channel manifest
→ verify published channel
```

The stable update channel is distribution metadata, not source authority.

Private signing material stays in GitHub secrets/secure infrastructure; never commit it.

## Update channels

Current Launcher settings accept:

```text
stable
preview
```

But a channel is only operational when matching publication/runtime support actually exists. Do not infer a maintained Preview feed merely because the settings schema accepts `preview`.

Keep channel semantics explicit and fail closed when the selected feed/runtime path is unsupported.

## Future in-app updater runtime

If/when in-app self-update is implemented, it must consume the existing signed release/channel boundary rather than create a second publication system.

Required shape:

```text
query selected channel metadata
→ validate compatibility/version
→ download signed artifact
→ verify authenticity/integrity
→ stage update
→ explicit restart/install boundary
→ post-update migration/reconciliation
→ report healthy/failure state
```

Use one updater state authority. Current release publication does not by itself satisfy this runtime contract.

## Signature and key safety

- private updater/signing keys stay outside repository/source/artifacts/logs;
- public verification material may ship where required;
- missing/invalid signatures fail closed;
- never add a production UI switch that disables authenticity verification;
- diagnostics may expose safe failure classification, never secret material.

Tauri updater signatures and Windows Authenticode protect different boundaries. Do not claim Windows code signing merely because a Tauri updater `.sig` exists.

## WebView2

Current package uses the Tauri Windows WebView2 download bootstrapper in silent mode.

Missing/corrupt WebView2 should surface as an actionable packaged-runtime prerequisite problem, not a generic blank-window failure.

## Release artifact contract

A release candidate should identify at minimum:

```text
source version
commit SHA
channel
installer filename
artifact SHA-256
updater signature when produced
manifest/version release identity
```

Avoid multiple differently built installers claiming the same version/channel.

## Proof boundaries

### Source/static proof

Can prove:

```text
version/config consistency
release workflow contract
manifest/signature/checksum generation logic
secret references are external
bundle identity/config
```

### Packaged Windows proof

Needed for:

```text
installer execution
installed executable/resources
upgrade behavior
WebView2 bootstrap behavior
shortcut/Installed Apps identity
uninstall behavior
```

### Future in-app updater proof

Only applicable after updater runtime exists:

```text
channel query
signature rejection
successful staged update
restart/install handoff
post-update migration/reconciliation
failure leaves current app usable
```

Do not report this proof lane before the runtime exists.

## Failure / rollback rule

```text
download/build/publication failure → existing installed app unaffected
signature/manifest failure         → activation/publication blocked
settings migration failure         → preserve previous-valid metadata and surface recovery
interrupted future in-app update   → require an implemented/tested recovery path before claiming rollback
```

Do not claim previous-version rollback unless an actual restoration mechanism exists and has been tested.

## Review checklist

```text
[ ] product/version/bundle identity align
[ ] installer policy matches current Tauri config
[ ] release workflow is explicit and main-only
[ ] signing secrets are external
[ ] signed artifact/manifest/checksum claims match produced files
[ ] stable vs preview capability is not overstated
[ ] publication is not confused with in-app updater implementation
[ ] server workspaces remain outside Launcher uninstall/update ownership
[ ] packaged Windows behavior is proven at the correct boundary
```
