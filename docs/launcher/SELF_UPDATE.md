# LazyBuilder Launcher Self Update

This document defines the single release/signing authority for Launcher self-update.

## Current implementation boundary

Remote GitHub work completed:

- release-only Tauri overlay (`src-tauri/tauri.release.conf.json`);
- immutable signed Launcher release workflow;
- stable update-channel publication;
- updater manifest builder and validator;
- version synchronization verifier;
- canonical version bump tool;
- SHA-256 release evidence;
- private-key repository guards.

Still intentionally blocked on trusted signing bootstrap:

- canonical updater public key;
- Tauri runtime updater plugin/configuration;
- `launcher-update` runtime operation;
- automatic update checks and update-channel UI.

Do not add a fake updater or unsigned download fallback to bypass this boundary.

## Security model

LazyBuilder uses Tauri v2 updater artifacts and signatures. Update signature verification must never be bypassed.

- The updater public key is safe to embed in the Launcher runtime.
- The updater private key must never be committed, uploaded as an artifact, or copied into user data.
- The private key exists only in the release operator's secure storage and GitHub Actions secret `TAURI_SIGNING_PRIVATE_KEY`.
- The optional key password exists only in `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`.
- Release builds use `src-tauri/tauri.release.conf.json`; normal developer builds do not require signing material.
- `.gitignore` rejects generic `*.key` and `*.pem` files, and the release verifier also rejects known private-key filenames/literal assignments.

## One-time signing bootstrap

Generate the updater signing key pair on a trusted machine with the pinned Launcher toolchain:

```powershell
cd apps/launcher
npx tauri signer generate -w "$HOME\.tauri\lazybuilder-updater.key"
```

Keep `lazybuilder-updater.key` outside the repository and back it up securely. Store its private-key content in GitHub Actions secret `TAURI_SIGNING_PRIVATE_KEY` and its password in `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`.

The generated public key must be reviewed and committed as the canonical Launcher updater public key before runtime update installation is enabled.

### Key bootstrap checklist

```text
[ ] key pair generated on a trusted machine
[ ] private key backed up in at least one protected offline location
[ ] TAURI_SIGNING_PRIVATE_KEY configured in GitHub Actions
[ ] TAURI_SIGNING_PRIVATE_KEY_PASSWORD configured in GitHub Actions
[ ] public key copied separately and reviewed
[ ] public key committed to the runtime updater configuration
[ ] runtime updater source audit completed
[ ] signed release/install/update tested on Windows before enabling auto-check
```

Never regenerate the key merely because a workstation changes. Existing installed clients trust the embedded public key.

## Version authority

Before changing the Launcher version, use the canonical repository tool from the repository root:

```powershell
python scripts/set_launcher_version.py 0.2.0
```

It updates all version owners that must remain synchronized:

- `apps/launcher/package.json`;
- `apps/launcher/package-lock.json` and its root package entry;
- `apps/launcher/src-tauri/Cargo.toml`;
- the `lazybuilder` package entry in `Cargo.lock`;
- `apps/launcher/src-tauri/tauri.conf.json`.

Then verify:

```powershell
python scripts/verify_launcher_release_contract.py --version 0.2.0
```

Do not manually bump only one file.

## Release contract

`Launcher Release` is manual and only runs from `main`.

The workflow:

1. checks every canonical Launcher version owner;
2. verifies release overlay and repository key-safety rules;
3. runs full Launcher source verification;
4. rejects an existing immutable version tag;
5. builds the NSIS installer with `createUpdaterArtifacts=true`;
6. requires Tauri to emit a non-empty matching `.sig` file;
7. creates and validates `latest.json` against the canonical repository release URL;
8. writes SHA-256 checksums for installer/signature;
9. publishes immutable release tag `launcher-vMAJOR.MINOR.PATCH`;
10. publishes stable channel manifest to `launcher-update-channel/stable/latest.json`;
11. downloads that published manifest through GitHub API and validates it again.

Stable updater endpoint:

```text
https://raw.githubusercontent.com/halokaryamedia-source/LazyBuilder-Plugin/launcher-update-channel/stable/latest.json
```

The channel branch contains distribution metadata only; it is not a source-development authority.

## Release failure and recovery

### Failure before immutable release creation

No release is visible to users. Fix the cause and rerun the workflow for the same source version.

### Immutable release exists but stable manifest publication failed

Do **not** rebuild/replace the version artifacts. The signed release is immutable. Diagnose the channel publication step and republish the exact `latest.json` generated for that release.

Before republishing, verify:

```powershell
python scripts/verify_launcher_update_manifest.py update-channel/stable/latest.json --version X.Y.Z
```

### Stable manifest points to a bad application release

Do not overwrite the existing release/tag or silently downgrade. Fix the application, bump to a new version, sign it with the same key, and publish that new version to the stable channel.

### Signing private key is lost

Stop release publication. Do not create an unsigned fallback. A new signing identity requires an explicit migration plan because already-installed clients trust the previous public key.

## Version policy

A release request must exactly match the Launcher source version. Never publish a different updater version by changing only workflow input.

Release tags are immutable. If a release is bad, publish a new version. Do not replace a signed installer under an existing version tag.

## Runtime authority (U2)

After the canonical public key is available, runtime updater implementation must:

- use only the embedded canonical updater public key;
- use the canonical stable channel endpoint;
- check availability without mutating server workspaces;
- register download/install work in `OperationRegistry` as `launcher-update`;
- expose real download progress when available;
- never advertise cancellation after install/commit boundary;
- refuse installation while unsafe Launcher/server operations are active;
- install through the Tauri updater and relaunch only after successful signature verification/install;
- keep Paper/runtime update logic completely separate from Launcher self-update.

`autoCheckUpdates` and `updateChannel` remain hidden from normal UI until this runtime authority is active. A stored preference must not imply functionality that does not yet exist.
