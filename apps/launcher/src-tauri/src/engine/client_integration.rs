use crate::engine::persistence;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::sync::Mutex;
use std::collections::HashSet;
use std::env;
use std::fs;
use std::fs::OpenOptions;
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use sysinfo::System;

pub const TARGET_MINECRAFT_VERSION: &str = "1.21.4";
const MAX_LOG_SCAN_BYTES: u64 = 2 * 1024 * 1024;
const CLIENT_SYNC_JOURNAL: &str = "sync-rollback.json";
const CLIENT_SYNC_JOURNAL_LABEL: &str = "Client Setup sync rollback journal";
static CLIENT_SYNC_GATE: Mutex<()> = Mutex::new(());

#[derive(Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct ClientSyncFile {
    name: String,
    sha256: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields, rename_all = "camelCase")]
struct ClientSyncJournal {
    version: u8,
    committed: bool,
    previous: Vec<ClientSyncFile>,
    installed: Vec<ClientSyncFile>,
}

// V1 ships the three first-party LazyBuilder client managers as one coherent client suite.
const MODS: [ClientModSpec; 3] = [
    ClientModSpec {
        id: "map-manager",
        display_name: "Map Manager",
        file_name: "lazybuilder-map-manager-0.1.0-SNAPSHOT.jar",
        file_prefix: "lazybuilder-map-manager-",
    },
    ClientModSpec {
        id: "utility-manager",
        display_name: "Utility Manager",
        file_name: "lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar",
        file_prefix: "lazybuilder-utility-manager-",
    },
    ClientModSpec {
        id: "performance-manager",
        display_name: "Performance Manager",
        file_name: "lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar",
        file_prefix: "lazybuilder-performance-manager-",
    },
];

#[derive(Clone, Copy)]
struct ClientModSpec {
    id: &'static str,
    display_name: &'static str,
    file_name: &'static str,
    file_prefix: &'static str,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClientProfileSummary {
    pub name: String,
    pub path: String,
    pub modrinth_root: String,
    pub mods_path: String,
    pub game_version: Option<String>,
    pub loader: Option<String>,
    pub verification: String,
    pub compatible: bool,
    pub selected: bool,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClientModStatus {
    pub id: String,
    pub display_name: String,
    pub state: String,
    pub installed_files: Vec<String>,
    pub target_file: String,
    pub bundled: bool,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ClientIntegrationStatus {
    pub modrinth_detected: bool,
    pub profiles: Vec<ClientProfileSummary>,
    pub selected_profile: Option<ClientProfileSummary>,
    pub selected_profile_missing: bool,
    pub mods: Vec<ClientModStatus>,
    pub ready: bool,
    pub message: String,
}

#[derive(Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ClientIntegrationConfig {
    selected_profile_path: Option<String>,
    custom_profiles_root: Option<String>,
}

pub fn status(resource_dir: Option<&Path>) -> Result<ClientIntegrationStatus, String> {
    let config = load_config()?;
    let roots = profile_roots(config.custom_profiles_root.as_deref());
    let selected_path = config.selected_profile_path.as_deref();
    let mut profiles = discover_profiles(selected_path, &roots)?;
    profiles.sort_by(|a, b| a.name.to_ascii_lowercase().cmp(&b.name.to_ascii_lowercase()));

    let selected_profile = profiles.iter().find(|profile| profile.selected).cloned();
    let selected_profile_missing = selected_path.is_some() && selected_profile.is_none();
    let modrinth_detected = !roots.is_empty() || selected_profile.is_some();
    let mods = match selected_profile.as_ref() {
        Some(profile) if profile.compatible => component_statuses(Path::new(&profile.path), resource_dir)?,
        _ => unchecked_component_statuses(resource_dir),
    };
    let ready = selected_profile.as_ref().is_some_and(|profile| profile.compatible)
        && mods.iter().all(|entry| entry.state == "Installed");

    let message = if selected_profile_missing {
        "The selected Modrinth profile is no longer available. Select it again.".into()
    } else if !modrinth_detected {
        "Modrinth profiles were not detected automatically. Select the exact profile you use for LazyBuilder.".into()
    } else if profiles.is_empty() {
        "No Modrinth profiles were found. Select the exact Modrinth profile folder manually.".into()
    } else if selected_profile.is_none() {
        "Choose a detected profile or select the exact Modrinth profile folder manually.".into()
    } else if let Some(profile) = selected_profile.as_ref().filter(|profile| !profile.compatible) {
        incompatible_profile_message(profile)
    } else if ready {
        "LazyBuilder client is ready in the selected Modrinth profile.".into()
    } else {
        "Sync the selected profile to install or repair the required LazyBuilder client components.".into()
    };

    Ok(ClientIntegrationStatus {
        modrinth_detected,
        profiles,
        selected_profile,
        selected_profile_missing,
        mods,
        ready,
        message,
    })
}

pub fn select_profile(profile_path: &str) -> Result<(), String> {
    let mut config = load_config()?;
    let roots = profile_roots(config.custom_profiles_root.as_deref());
    let requested = canonical_profile_in_roots(Path::new(profile_path), &roots)?;
    inspect_profile(&requested, Some(&requested))?
        .ok_or_else(|| "The selected folder is not a Modrinth profile.".to_string())?;
    config.selected_profile_path = Some(requested.to_string_lossy().to_string());
    save_config(&config)
}

pub fn select_manual_profile(profile_path: &Path) -> Result<(), String> {
    let requested = canonical_manual_profile_path(profile_path)?;
    inspect_profile(&requested, Some(&requested))?
        .ok_or_else(|| "The selected folder is not a Modrinth profile.".to_string())?;
    let profiles_root = requested
        .parent()
        .ok_or_else(|| "Selected Modrinth profile has no profiles directory.".to_string())?
        .canonicalize()
        .map_err(|error| format!("Could not resolve Modrinth profiles directory: {error}"))?;
    save_config(&ClientIntegrationConfig {
        selected_profile_path: Some(requested.to_string_lossy().to_string()),
        custom_profiles_root: Some(profiles_root.to_string_lossy().to_string()),
    })
}

pub fn sync(resource_dir: Option<&Path>) -> Result<(), String> {
    let config = load_config()?;
    let roots = profile_roots(config.custom_profiles_root.as_deref());
    let selected = config
        .selected_profile_path
        .ok_or_else(|| "Choose a Modrinth profile before syncing the client.".to_string())?;
    let profile_path = canonical_profile_in_roots(Path::new(&selected), &roots)
        .map_err(|_| "The selected Modrinth profile is no longer available. Select it again.".to_string())?;
    let profile = inspect_profile(&profile_path, Some(&profile_path))?
        .ok_or_else(|| "The selected Modrinth profile is no longer available.".to_string())?;
    if !profile.compatible {
        return Err(incompatible_profile_message(&profile));
    }

    ensure_profile_not_running(&profile_path)?;
    let mods_dir = profile_path.join("mods");
    ensure_mods_directory_writable(&mods_dir)?;
    let sources = MODS
        .iter()
        .map(|spec| resolve_source(resource_dir, *spec).map(|path| (*spec, path)))
        .collect::<Result<Vec<_>, _>>()?;
    transactional_sync(&mods_dir, &sources)
}

/// Per-profile rollback evidence is owned by the existing Client Setup transaction,
/// not an external mod installer or a durable alternative Modrinth registry.
fn sync_file_digest(path: &Path) -> Result<String, String> {
    persistence::safe_path::ensure_regular_file(path, "Client Sync JAR")?;
    let mut input = fs::File::open(path).map_err(|error| error.to_string())?;
    let mut hasher = Sha256::new();
    let mut buffer = [0u8; 64 * 1024];
    loop {
        let count = input.read(&mut buffer).map_err(|error| error.to_string())?;
        if count == 0 { break; }
        hasher.update(&buffer[..count]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

fn sync_file_record(path: &Path) -> Result<ClientSyncFile, String> {
    let name = path.file_name().and_then(|value| value.to_str())
        .ok_or_else(|| "Client Sync JAR filename is not portable.".to_string())?;
    if !MODS.iter().any(|spec| {
        name.to_ascii_lowercase().starts_with(spec.file_prefix)
            && name.to_ascii_lowercase().ends_with(".jar")
    }) {
        return Err("Client Sync rollback entry is outside LazyBuilder-owned client prefixes.".into());
    }
    Ok(ClientSyncFile { name: name.into(), sha256: sync_file_digest(path)? })
}

fn require_valid_sync_journal(journal: &ClientSyncJournal) -> Result<(), String> {
    if journal.version != 1 || journal.installed.len() != MODS.len()
        || journal.previous.len() > 96 {
        return Err("Client Sync rollback journal has unsupported or incomplete inventory.".into());
    }
    let mut names = HashSet::new();
    for record in journal.previous.iter().chain(&journal.installed) {
        let lower = record.name.to_ascii_lowercase();
        if lower != record.name.to_lowercase()
            || record.name.contains('/') || record.name.contains('\\')
            || !MODS.iter().any(|spec| lower.starts_with(spec.file_prefix) && lower.ends_with(".jar"))
            || record.sha256.len() != 64
            || !record.sha256.bytes().all(|value| value.is_ascii_hexdigit() && !value.is_ascii_uppercase()) {
            return Err("Client Sync rollback journal contains an unsafe filename or digest.".into());
        }
    }
    for record in &journal.previous {
        if !names.insert(record.name.to_ascii_lowercase()) {
            return Err("Client Sync rollback journal contains duplicate previous files.".into());
        }
    }
    for spec in MODS {
        if journal.installed.iter().filter(|record| record.name == spec.file_name).count() != 1 {
            return Err(format!("Client Sync rollback journal is missing target for {}.", spec.display_name));
        }
    }
    Ok(())
}

fn check_sync_backup_directory(path: &Path) -> Result<(), String> {
    let meta = fs::symlink_metadata(path).map_err(|error| error.to_string())?;
    if !meta.is_dir() || meta.file_type().is_symlink() {
        return Err("Client Sync rollback area is not a safe directory.".into());
    }
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        if meta.file_attributes() & 0x400 != 0 {
            return Err("Client Sync rollback area is a Windows reparse point.".into());
        }
    }
    Ok(())
}

fn client_sync_journal_path(backup_dir: &Path) -> PathBuf {
    backup_dir.join(CLIENT_SYNC_JOURNAL)
}

fn write_sync_journal(backup_dir: &Path, journal: &ClientSyncJournal) -> Result<(), String> {
    require_valid_sync_journal(journal)?;
    persistence::write_json_atomically(
        &client_sync_journal_path(backup_dir), journal, CLIENT_SYNC_JOURNAL_LABEL
    )
}

/// An older manifest-less backup has unknown completeness, so it remains
/// untouched. Complete journals are reconciled explicitly before the next sync.
fn reconcile_client_sync_backups(mods_dir: &Path) -> Result<(), String> {
    let mut backups = Vec::new();
    for entry in fs::read_dir(mods_dir).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        if entry.file_name().to_string_lossy().starts_with(".lazybuilder-sync-backup-") {
            backups.push(entry.path());
        }
    }
    // There is only one transaction owner. Never apply one backup before
    // discovering that another abandoned transaction makes ordering ambiguous.
    if backups.len() > 1 {
        return Err("Multiple Client Sync rollback areas are ambiguous; all were preserved.".into());
    }
    for backup_dir in backups {
        let name = backup_dir.file_name().ok_or_else(|| "Client Sync rollback path is invalid.".to_string())?;
        check_sync_backup_directory(&backup_dir)?;
        let manifest_path = client_sync_journal_path(&backup_dir);
        if !persistence::metadata_entry_exists(&manifest_path, CLIENT_SYNC_JOURNAL_LABEL)?
            && !persistence::metadata_entry_exists(
                &manifest_path.with_extension("json.tmp"), CLIENT_SYNC_JOURNAL_LABEL
            )?
            && !persistence::metadata_entry_exists(
                &manifest_path.with_extension("json.previous"), CLIENT_SYNC_JOURNAL_LABEL
            )? {
            return Err(format!(
                "Client Sync cannot safely recover legacy or incomplete backup {}. Backup was preserved.",
                name.to_string_lossy()
            ));
        }
        persistence::recover_atomic_file(&manifest_path, CLIENT_SYNC_JOURNAL_LABEL)?;
        let journal: ClientSyncJournal = persistence::read_json(
            &manifest_path, CLIENT_SYNC_JOURNAL_LABEL
        )?;
        require_valid_sync_journal(&journal)?;

        // Verify all original bytes before changing the current Modrinth profile.
        for original in &journal.previous {
            let saved = backup_dir.join(&original.name);
            if sync_file_digest(&saved)? != original.sha256 {
                return Err(format!(
                    "Client Sync rollback backup {} is incomplete or changed; recovery was blocked.",
                    original.name
                ));
            }
        }
        // Never delete a user-modified or unrelated JAR, even when it has a
        // LazyBuilder prefix. A known transaction can own only its recorded bytes.
        let mut current = Vec::new();
        for spec in MODS {
            for path in owned_mod_paths(mods_dir, spec)? {
                let entry = sync_file_record(&path)?;
                let valid = journal.previous.iter().chain(&journal.installed)
                    .any(|known| known.name.eq_ignore_ascii_case(&entry.name)
                        && known.sha256 == entry.sha256);
                if !valid {
                    return Err(format!(
                        "Client Sync found unrecognized component bytes in {}. Rollback data was preserved.",
                        entry.name
                    ));
                }
                current.push(path);
            }
        }
        if journal.committed {
            // A published suite is committed only when exactly its three
            // expected targets remain. A missing/modified target fails closed.
            if current.len() != MODS.len()
                || journal.installed.iter().any(|expected| {
                    !current.iter().any(|path| {
                        path.file_name().and_then(|value| value.to_str()) == Some(expected.name.as_str())
                            && sync_file_digest(path).ok().as_deref() == Some(expected.sha256.as_str())
                    })
                }) {
                return Err("Committed Client Sync files no longer match the journal; recovery evidence was preserved.".into());
            }
            fs::remove_dir_all(&backup_dir).map_err(|error| error.to_string())?;
            continue;
        }

        // Stage all original files before retiring any installed component.
        // Re-entrant recovery may replace a leftover .restore-* file only after
        // verifying it is a regular file in this transaction's own backup dir.
        for original in &journal.previous {
            let stage = backup_dir.join(format!(".restore-{}", original.name));
            persistence::safe_path::remove_regular_file_if_present(&stage, "Client Sync recovery stage")?;
            fs::copy(backup_dir.join(&original.name), &stage)
                .map_err(|error| format!("Could not stage Client Sync recovery: {error}"))?;
            if sync_file_digest(&stage)? != original.sha256 {
                return Err("Client Sync recovery stage checksum mismatch; previous files were preserved.".into());
            }
        }
        for path in current {
            fs::remove_file(&path).map_err(|error| format!(
                "Could not retire interrupted Client Sync component {}: {error}", path.display()
            ))?;
        }
        for original in &journal.previous {
            fs::rename(
                backup_dir.join(format!(".restore-{}", original.name)),
                mods_dir.join(&original.name)
            ).map_err(|error| format!("Could not restore {}: {error}", original.name))?;
        }
        fs::remove_dir_all(&backup_dir).map_err(|error| error.to_string())?;
    }
    Ok(())
}

fn transactional_sync(mods_dir: &Path, sources: &[(ClientModSpec, PathBuf)]) -> Result<(), String> {
    let _sync_lease = CLIENT_SYNC_GATE.lock()
        .map_err(|_| "Client Sync operation lock is poisoned.".to_string())?;
    reconcile_client_sync_backups(mods_dir)?;

    let backup_dir = mods_dir.join(format!(".lazybuilder-sync-backup-{}", std::process::id()));
    fs::create_dir(&backup_dir)
        .map_err(|error| format!("Could not reserve Client Sync rollback area: {error}"))?;
    let cleanup_incoming = || {
        for spec in MODS {
            let _ = fs::remove_file(mods_dir.join(format!("{}.incoming", spec.file_name)));
        }
    };
    let cleanup = || {
        cleanup_incoming();
        let _ = fs::remove_dir_all(&backup_dir);
    };

    let prepared = (|| -> Result<ClientSyncJournal, String> {
        let mut installed = Vec::with_capacity(MODS.len());
        for (spec, source) in sources {
            let incoming = mods_dir.join(format!("{}.incoming", spec.file_name));
            if incoming.exists() {
                persistence::safe_path::remove_regular_file_if_present(&incoming, "Client Sync incoming JAR")?;
            }
            fs::copy(source, &incoming)
                .map_err(|error| format!("Could not stage {}: {error}", spec.display_name))?;
            if !files_equal(source, &incoming)? {
                return Err(format!("Staged {} failed verification.", spec.display_name));
            }
            // The staging filename ends in .jar.incoming; record the actual
            // target JAR identity while hashing its verified staged bytes.
            installed.push(ClientSyncFile {
                name: spec.file_name.into(),
                sha256: sync_file_digest(&incoming)?,
            });
        }
        let mut previous = Vec::new();
        for spec in MODS {
            for old in owned_mod_paths(mods_dir, spec)? {
                let name = old.file_name()
                    .ok_or_else(|| "Installed LazyBuilder mod has no filename.".to_string())?;
                let saved = backup_dir.join(name);
                fs::copy(&old, &saved)
                    .map_err(|error| format!("Could not create rollback backup for {}: {error}", old.display()))?;
                if !files_equal(&old, &saved)? {
                    return Err(format!("Rollback backup verification failed for {}", old.display()));
                }
                let record = sync_file_record(&saved)?;
                // Flush prepared JAR bytes before publishing transaction evidence.
                fs::OpenOptions::new().read(true).write(true).open(&saved)
                    .and_then(|file| file.sync_all())
                    .map_err(|error| format!("Could not flush Client Sync backup: {error}"))?;
                previous.push(record);
            }
        }
        let journal = ClientSyncJournal { version: 1, committed: false, previous, installed };
        write_sync_journal(&backup_dir, &journal)?;
        Ok(journal)
    })();

    let mut journal = match prepared {
        Ok(journal) => journal,
        Err(error) => {
            cleanup();
            return Err(error);
        }
    };
    // The complete and verified pre-image is now journaled. Only this block
    // may mutate installed LazyBuilder JARs.
    let publish = (|| -> Result<(), String> {
        for (spec, _) in sources {
            remove_owned_mod_files(mods_dir, *spec)?;
            let incoming = mods_dir.join(format!("{}.incoming", spec.file_name));
            fs::rename(&incoming, mods_dir.join(spec.file_name))
                .map_err(|error| format!("Could not publish {}: {error}", spec.display_name))?;
        }
        journal.committed = true;
        write_sync_journal(&backup_dir, &journal)?;
        Ok(())
    })();
    if let Err(error) = publish {
        cleanup_incoming();
        // The same checks and atomic per-file restore path serve immediate
        // rollback and restart recovery. Never copy directly over a live JAR.
        return match reconcile_client_sync_backups(mods_dir) {
            Ok(()) => Err(format!(
                "Client Sync failed and its journal was reconciled safely: {error}"
            )),
            Err(recovery) => Err(format!(
                "Client Sync failed: {error}; recovery is incomplete: {recovery}. Original backups were preserved."
            )),
        };
    }
    cleanup();
    Ok(())
}

fn ensure_profile_not_running(profile_path: &Path) -> Result<(), String> {
    let needle = profile_path.to_string_lossy().to_ascii_lowercase().replace('/', "\\");
    let mut system = System::new_all();
    system.refresh_all();
    for process in system.processes().values() {
        let name = process.name().to_ascii_lowercase();
        if !name.contains("java") {
            continue;
        }
        let command = process.cmd().join(" ").to_ascii_lowercase().replace('/', "\\");
        if command.contains(&needle) {
            return Err("Minecraft is currently using the selected Modrinth profile. Close the game before syncing LazyBuilder client components.".into());
        }
    }
    Ok(())
}

fn unchecked_component_statuses(resource_dir: Option<&Path>) -> Vec<ClientModStatus> {
    MODS.iter()
        .map(|spec| ClientModStatus {
            id: spec.id.into(),
            display_name: spec.display_name.into(),
            state: "Not checked".into(),
            installed_files: Vec::new(),
            target_file: spec.file_name.into(),
            bundled: resolve_source(resource_dir, *spec).is_ok(),
        })
        .collect()
}

fn component_statuses(profile_path: &Path, resource_dir: Option<&Path>) -> Result<Vec<ClientModStatus>, String> {
    let mods_dir = profile_path.join("mods");
    MODS.iter()
        .map(|spec| {
            let installed_files = owned_mod_files(&mods_dir, *spec)?;
            let source = resolve_source(resource_dir, *spec).ok();
            let target = mods_dir.join(spec.file_name);
            let state = if installed_files.len() > 1 {
                "Duplicate"
            } else if installed_files.is_empty() {
                "Missing"
            } else if let Some(source) = source.as_ref() {
                if target.is_file() && files_equal(source, &target)? { "Installed" } else { "Update required" }
            } else if target.is_file() {
                "Installed (bundle unavailable)"
            } else {
                "Update required"
            };
            Ok(ClientModStatus {
                id: spec.id.into(),
                display_name: spec.display_name.into(),
                state: state.into(),
                installed_files,
                target_file: spec.file_name.into(),
                bundled: source.is_some(),
            })
        })
        .collect()
}

fn discover_profiles(selected_path: Option<&str>, roots: &[PathBuf]) -> Result<Vec<ClientProfileSummary>, String> {
    let selected = selected_path.and_then(|value| canonical_existing_directory(Path::new(value)).ok());
    let mut profiles = Vec::new();
    let mut seen = HashSet::new();

    for root in roots {
        let entries = match fs::read_dir(root) {
            Ok(entries) => entries,
            Err(_) => continue,
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_dir() {
                continue;
            }
            let canonical = match canonical_profile_in_roots(&path, roots) {
                Ok(path) => path,
                Err(_) => continue,
            };
            if !seen.insert(canonical.clone()) {
                continue;
            }
            if let Some(profile) = inspect_profile(&canonical, selected.as_deref())? {
                profiles.push(profile);
            }
        }
    }

    if let Some(selected) = selected {
        if seen.insert(selected.clone()) && is_modrinth_profile_path(&selected) {
            if let Some(profile) = inspect_profile(&selected, Some(&selected))? {
                profiles.push(profile);
            }
        }
    }
    Ok(profiles)
}

fn inspect_profile(path: &Path, selected: Option<&Path>) -> Result<Option<ClientProfileSummary>, String> {
    if !path.is_dir() || !is_modrinth_profile_path(path) {
        return Ok(None);
    }
    let name = path
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("Modrinth profile")
        .to_string();
    let runtime = runtime_identity_from_latest_log(path)?;
    let (game_version, loader, verification) = match runtime {
        Some((version, loader)) => (Some(version), Some(loader), "Last launch".to_string()),
        None => (None, None, "Unverified".to_string()),
    };
    let compatible = game_version.as_deref() == Some(TARGET_MINECRAFT_VERSION)
        && loader.as_deref() == Some("fabric");
    let profiles_root = path.parent().unwrap_or(path);
    let modrinth_root = profiles_root.parent().unwrap_or(profiles_root);

    Ok(Some(ClientProfileSummary {
        name,
        path: path.to_string_lossy().to_string(),
        modrinth_root: modrinth_root.to_string_lossy().to_string(),
        mods_path: path.join("mods").to_string_lossy().to_string(),
        game_version,
        loader,
        verification,
        compatible,
        selected: selected.is_some_and(|value| value == path),
    }))
}

fn runtime_identity_from_latest_log(profile_path: &Path) -> Result<Option<(String, String)>, String> {
    let log_path = profile_path.join("logs").join("latest.log");
    if !log_path.is_file() {
        return Ok(None);
    }
    let mut file = fs::File::open(&log_path)
        .map_err(|error| format!("Could not inspect {}: {error}", log_path.display()))?;
    let length = file.metadata().map_err(|error| error.to_string())?.len();
    if length > MAX_LOG_SCAN_BYTES {
        file.seek(SeekFrom::End(-(MAX_LOG_SCAN_BYTES as i64)))
            .map_err(|error| error.to_string())?;
    }
    let mut bytes = Vec::new();
    file.read_to_end(&mut bytes).map_err(|error| error.to_string())?;
    let text = String::from_utf8_lossy(&bytes);
    for line in text.lines().rev() {
        if let Some(identity) = runtime_identity_from_log_line(line) {
            return Ok(Some(identity));
        }
    }
    Ok(None)
}

/// Recognize only explicit loader/version evidence from the latest launch.
/// A modpack filename or third-party metadata never authorizes Client Setup.
fn runtime_identity_from_log_line(line: &str) -> Option<(String, String)> {
    let (_, after_minecraft) = line.split_once("Loading Minecraft ")?;
    for (marker, loader) in [
        (" with Fabric Loader ", "fabric"),
        (" with NeoForge ", "neoforge"),
    ] {
        if let Some((version, loader_version)) = after_minecraft.split_once(marker) {
            if !version.trim().is_empty() && !loader_version.trim().is_empty() {
                return Some((version.trim().to_string(), loader.into()));
            }
        }
    }
    None
}

fn incompatible_profile_message(profile: &ClientProfileSummary) -> String {
    match (profile.game_version.as_deref(), profile.loader.as_deref()) {
        (Some(version), Some(loader)) => format!(
            "Selected profile last ran Minecraft {version} with {loader}. LazyBuilder requires Minecraft {TARGET_MINECRAFT_VERSION} + Fabric. Select a compatible Modrinth Fabric profile before syncing."
        ),
        _ => format!(
            "LazyBuilder could not verify Minecraft {TARGET_MINECRAFT_VERSION} + Fabric from this profile's latest launch. Launch the intended Fabric profile once in Modrinth, then refresh Client Setup."
        ),
    }
}

fn profile_roots(custom_profiles_root: Option<&str>) -> Vec<PathBuf> {
    let mut roots = Vec::new();
    if let Some(explicit) = env::var_os("MODRINTH_PROFILES_DIR") {
        push_existing_root(&mut roots, PathBuf::from(explicit));
    }
    if let Some(appdata) = env::var_os("APPDATA") {
        let appdata = PathBuf::from(appdata);
        push_existing_root(&mut roots, appdata.join("ModrinthApp").join("profiles"));
        push_existing_root(&mut roots, appdata.join("com.modrinth.ModrinthApp").join("profiles"));
        push_existing_root(&mut roots, appdata.join("com.modrinth.theseus").join("profiles"));
    }
    if let Some(local) = env::var_os("LOCALAPPDATA") {
        push_existing_root(&mut roots, PathBuf::from(local).join("ModrinthApp").join("profiles"));
    }
    if let Some(custom) = custom_profiles_root {
        push_existing_root(&mut roots, PathBuf::from(custom));
    }
    roots
}

fn push_existing_root(roots: &mut Vec<PathBuf>, candidate: PathBuf) {
    if candidate.is_dir() {
        if let Ok(canonical) = candidate.canonicalize() {
            if !roots.contains(&canonical) {
                roots.push(canonical);
            }
        }
    }
}

fn canonical_existing_directory(path: &Path) -> Result<PathBuf, String> {
    let canonical = path
        .canonicalize()
        .map_err(|error| format!("Could not resolve Modrinth profile path: {error}"))?;
    if !canonical.is_dir() {
        return Err("Modrinth profile path is not a directory.".into());
    }
    Ok(canonical)
}

fn canonical_profile_in_roots(path: &Path, roots: &[PathBuf]) -> Result<PathBuf, String> {
    let canonical = canonical_existing_directory(path)?;
    let allowed = roots.iter().any(|root| canonical.parent() == Some(root.as_path()));
    if !allowed || !is_modrinth_profile_path(&canonical) {
        return Err("Selected folder is not a profile under a detected Modrinth profiles directory.".into());
    }
    Ok(canonical)
}

fn canonical_manual_profile_path(path: &Path) -> Result<PathBuf, String> {
    let canonical = canonical_existing_directory(path)?;
    if !is_modrinth_profile_path(&canonical) {
        if canonical
            .file_name()
            .and_then(|value| value.to_str())
            .is_some_and(|value| value.eq_ignore_ascii_case("profiles"))
        {
            return Err("Select the exact Modrinth profile inside the profiles folder, not the profiles folder itself.".into());
        }
        return Err("Select an exact Modrinth profile folder whose parent folder is named 'profiles'.".into());
    }
    Ok(canonical)
}

fn is_modrinth_profile_path(path: &Path) -> bool {
    path.is_dir()
        && path
            .parent()
            .and_then(|parent| parent.file_name())
            .and_then(|value| value.to_str())
            .is_some_and(|value| value.eq_ignore_ascii_case("profiles"))
}

fn ensure_mods_directory_writable(mods_dir: &Path) -> Result<(), String> {
    fs::create_dir_all(mods_dir)
        .map_err(|error| format!("Could not create the selected profile mods directory: {error}"))?;
    let probe = mods_dir.join(format!(".lazybuilder-write-test-{}", std::process::id()));
    let mut file = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&probe)
        .map_err(|error| format!("The selected Modrinth profile mods directory is not writable: {error}"))?;
    file.write_all(b"lazybuilder")
        .map_err(|error| format!("The selected Modrinth profile mods directory is not writable: {error}"))?;
    drop(file);
    fs::remove_file(&probe)
        .map_err(|error| format!("Could not clean the Client Setup write test: {error}"))?;
    Ok(())
}

fn owned_mod_paths(mods_dir: &Path, spec: ClientModSpec) -> Result<Vec<PathBuf>, String> {
    if !mods_dir.is_dir() {
        return Ok(Vec::new());
    }
    let mut files = Vec::new();
    for entry in fs::read_dir(mods_dir).map_err(|error| error.to_string())? {
        let path = entry.map_err(|error| error.to_string())?.path();
        let Some(name) = path.file_name().and_then(|value| value.to_str()) else {
            continue;
        };
        let lower = name.to_ascii_lowercase();
        if path.is_file() && lower.starts_with(spec.file_prefix) && lower.ends_with(".jar") {
            files.push(path);
        }
    }
    files.sort();
    Ok(files)
}

fn owned_mod_files(mods_dir: &Path, spec: ClientModSpec) -> Result<Vec<String>, String> {
    Ok(owned_mod_paths(mods_dir, spec)?
        .into_iter()
        .filter_map(|path| path.file_name().and_then(|value| value.to_str()).map(ToOwned::to_owned))
        .collect())
}

fn remove_owned_mod_files(mods_dir: &Path, spec: ClientModSpec) -> Result<(), String> {
    for path in owned_mod_paths(mods_dir, spec)? {
        fs::remove_file(&path)
            .map_err(|error| format!("Could not replace LazyBuilder-owned client mod {}: {error}", path.display()))?;
    }
    Ok(())
}

fn resolve_source(resource_dir: Option<&Path>, spec: ClientModSpec) -> Result<PathBuf, String> {
    let resource_dir = resource_dir
        .ok_or_else(|| format!("Bundled {} is unavailable in this Launcher build.", spec.display_name))?;
    let bundled = resource_dir
        .join("resources")
        .join("client-mods")
        .join(spec.file_name);
    if bundled.is_file() {
        return Ok(bundled);
    }
    Err(format!(
        "Bundled {} is unavailable. Runtime-ready Launcher builds must include the required LazyBuilder client components.",
        spec.display_name
    ))
}

fn files_equal(left: &Path, right: &Path) -> Result<bool, String> {
    let left_meta = fs::metadata(left)
        .map_err(|error| format!("Could not inspect {}: {error}", left.display()))?;
    let right_meta = fs::metadata(right)
        .map_err(|error| format!("Could not inspect {}: {error}", right.display()))?;
    if left_meta.len() != right_meta.len() {
        return Ok(false);
    }

    let mut left_file = fs::File::open(left)
        .map_err(|error| format!("Could not read {}: {error}", left.display()))?;
    let mut right_file = fs::File::open(right)
        .map_err(|error| format!("Could not read {}: {error}", right.display()))?;
    let mut left_buffer = [0u8; 8192];
    let mut right_buffer = [0u8; 8192];

    loop {
        let left_read = left_file.read(&mut left_buffer)
            .map_err(|error| format!("Could not read {}: {error}", left.display()))?;
        let right_read = right_file.read(&mut right_buffer)
            .map_err(|error| format!("Could not read {}: {error}", right.display()))?;
        if left_read != right_read {
            return Ok(false);
        }
        if left_read == 0 {
            return Ok(true);
        }
        if left_buffer[..left_read] != right_buffer[..right_read] {
            return Ok(false);
        }
    }
}

fn app_data_root() -> Result<PathBuf, String> {
    env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| env::var_os("APPDATA").map(PathBuf::from))
        .map(|path| path.join("LazyBuilder"))
        .ok_or_else(|| "Windows application data directory is unavailable.".to_string())
}

fn config_path() -> Result<PathBuf, String> {
    Ok(app_data_root()?.join("config").join("client-integration.json"))
}

fn legacy_config_path() -> Option<PathBuf> {
    env::var_os("APPDATA")
        .map(PathBuf::from)
        .map(|path| path.join("LazyBuilder").join("client-integration.json"))
}

const CLIENT_CONFIG_LABEL: &str = "Client Setup configuration";

fn load_config() -> Result<ClientIntegrationConfig, String> {
    let path = config_path()?;
    persistence::recover_atomic_file(&path, CLIENT_CONFIG_LABEL)?;
    if persistence::metadata_entry_exists(&path, CLIENT_CONFIG_LABEL)? {
        let config = read_config(&path)?;
        persistence::cleanup_recovery_files(&path, CLIENT_CONFIG_LABEL)?;
        cleanup_legacy_incoming(&path)?;
        return Ok(config);
    }

    let legacy_incoming = path.with_extension("json.incoming");
    if persistence::metadata_entry_exists(&legacy_incoming, "legacy Client Setup staging configuration")? {
        let config = persistence::read_json(
            &legacy_incoming,
            "legacy Client Setup staging configuration",
        )?;
        save_config(&config)?;
        persistence::safe_path::remove_regular_file_if_present(
            &legacy_incoming,
            "legacy Client Setup staging configuration",
        )?;
        return Ok(config);
    }
    if let Some(legacy) = legacy_config_path().filter(|candidate| candidate != &path) {
        if persistence::metadata_entry_exists(&legacy, "legacy Client Setup configuration")? {
            let config = persistence::read_json(&legacy, "legacy Client Setup configuration")?;
            save_config(&config)?;
            let _ = fs::remove_file(legacy);
            return Ok(config);
        }
    }
    Ok(ClientIntegrationConfig::default())
}

fn cleanup_legacy_incoming(path: &Path) -> Result<(), String> {
    persistence::safe_path::remove_regular_file_if_present(
        &path.with_extension("json.incoming"),
        "legacy Client Setup staging configuration",
    )
}

fn read_config(path: &Path) -> Result<ClientIntegrationConfig, String> {
    persistence::read_json(path, CLIENT_CONFIG_LABEL)
}

fn save_config(config: &ClientIntegrationConfig) -> Result<(), String> {
    let path = config_path()?;
    persistence::write_json_atomically(&path, config, CLIENT_CONFIG_LABEL)
}

#[cfg(test)]
mod client_profile_compatibility_tests {
    use super::*;

    fn sample_profile(version: Option<&str>, loader: Option<&str>) -> ClientProfileSummary {
        ClientProfileSummary {
            name: "Selected profile".into(),
            path: "profiles/selected".into(),
            modrinth_root: "profiles".into(),
            mods_path: "profiles/selected/mods".into(),
            game_version: version.map(str::to_string),
            loader: loader.map(str::to_string),
            verification: "Last launch".into(),
            compatible: false,
            selected: true,
        }
    }

    #[test]
    fn manifestless_backup_remains_untouched_and_blocks_sync() {
        let root = std::env::temp_dir().join(format!("lb-sync-legacy-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        let mods = root.join("mods");
        let backup = mods.join(".lazybuilder-sync-backup-legacy");
        fs::create_dir_all(&backup).unwrap();
        fs::write(backup.join("old.jar"), b"evidence").unwrap();
        let error = transactional_sync(&mods, &[]).unwrap_err();
        assert!(error.contains("incomplete backup"));
        assert_eq!(fs::read(backup.join("old.jar")).unwrap(), b"evidence");
        fs::remove_dir_all(root).unwrap();
    }

    fn make_sync_journal(mods: &Path, committed: bool) -> (PathBuf, ClientSyncJournal) {
        let backup = mods.join(".lazybuilder-sync-backup-recovery");
        fs::create_dir_all(&backup).unwrap();
        let previous_name = "lazybuilder-map-manager-old.jar";
        let previous_path = backup.join(previous_name);
        fs::write(&previous_path, b"original-map").unwrap();
        let mut journal = ClientSyncJournal {
            version: 1,
            committed,
            previous: vec![sync_file_record(&previous_path).unwrap()],
            installed: Vec::new(),
        };
        for spec in MODS {
            let path = mods.join(spec.file_name);
            fs::write(&path, spec.id.as_bytes()).unwrap();
            journal.installed.push(sync_file_record(&path).unwrap());
        }
        write_sync_journal(&backup, &journal).unwrap();
        (backup, journal)
    }

    #[test]
    fn interrupted_publication_restores_verified_original_and_keeps_other_mods() {
        let root = std::env::temp_dir().join(format!("lb-sync-recover-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        let mods = root.join("mods");
        fs::create_dir_all(&mods).unwrap();
        let (backup, _) = make_sync_journal(&mods, false);
        fs::write(mods.join("another-mod.jar"), b"user-data").unwrap();
        reconcile_client_sync_backups(&mods).unwrap();
        assert!(!backup.exists());
        assert_eq!(fs::read(mods.join("lazybuilder-map-manager-old.jar")).unwrap(), b"original-map");
        for spec in MODS { assert!(!mods.join(spec.file_name).exists()); }
        assert_eq!(fs::read(mods.join("another-mod.jar")).unwrap(), b"user-data");
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn altered_backup_is_not_used_for_restore() {
        let root = std::env::temp_dir().join(format!("lb-sync-corrupt-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        let mods = root.join("mods");
        fs::create_dir_all(&mods).unwrap();
        let (backup, _) = make_sync_journal(&mods, false);
        fs::write(backup.join("lazybuilder-map-manager-old.jar"), b"tampered").unwrap();
        assert!(reconcile_client_sync_backups(&mods).is_err());
        assert!(backup.exists());
        assert!(mods.join(MODS[0].file_name).exists());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn committed_transaction_cleans_only_matching_stale_backup() {
        let root = std::env::temp_dir().join(format!("lb-sync-committed-{}", std::process::id()));
        let _ = fs::remove_dir_all(&root);
        let mods = root.join("mods");
        fs::create_dir_all(&mods).unwrap();
        let (backup, _) = make_sync_journal(&mods, true);
        reconcile_client_sync_backups(&mods).unwrap();
        assert!(!backup.exists());
        for spec in MODS {
            assert_eq!(fs::read(mods.join(spec.file_name)).unwrap().as_slice(), spec.id.as_bytes());
        }
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn fabric_log_identity_remains_recognized() {
        let line = "[main/INFO]: Loading Minecraft 1.21.4 with Fabric Loader 0.16.0";
        assert_eq!(runtime_identity_from_log_line(line), Some(("1.21.4".into(), "fabric".into())));
    }

    #[test]
    fn explicit_neoforge_log_identity_cannot_be_mistaken_for_fabric() {
        let line = "[main/INFO]: Loading Minecraft 1.21.1 with NeoForge 21.1.252";
        assert_eq!(runtime_identity_from_log_line(line), Some(("1.21.1".into(), "neoforge".into())));
        let profile = sample_profile(Some("1.21.1"), Some("neoforge"));
        assert!(incompatible_profile_message(&profile).contains("Minecraft 1.21.1 with neoforge"));
        assert!(incompatible_profile_message(&profile).contains("Minecraft 1.21.4 + Fabric"));
    }

    #[test]
    fn unknown_or_unsupported_log_identity_stays_unverified() {
        assert_eq!(runtime_identity_from_log_line("Loaded NeoForge mods"), None);
        assert_eq!(runtime_identity_from_log_line("Loading Minecraft 1.21.1 with NeoForge "), None);
        let profile = sample_profile(None, None);
        assert!(incompatible_profile_message(&profile).contains("could not verify"));
    }

    #[test]
    fn known_incompatible_fabric_version_is_reported_not_treated_as_unverified() {
        let profile = sample_profile(Some("1.21.1"), Some("fabric"));
        assert!(incompatible_profile_message(&profile).contains("Minecraft 1.21.1 with fabric"));
    }
}
