use crate::engine::persistence;
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::env;
use std::fs;
use std::fs::OpenOptions;
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use sysinfo::System;

pub const TARGET_MINECRAFT_VERSION: &str = "1.21.4";
const MAX_LOG_SCAN_BYTES: u64 = 2 * 1024 * 1024;

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
    } else if selected_profile.as_ref().is_some_and(|profile| !profile.compatible) {
        format!("LazyBuilder could not verify Minecraft {TARGET_MINECRAFT_VERSION} + Fabric from this profile's latest launch. Launch the profile once in Modrinth, then refresh.")
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
        return Err(format!(
            "LazyBuilder requires Minecraft {TARGET_MINECRAFT_VERSION} + Fabric. Launch this profile once in Modrinth, then refresh Client Setup before syncing."
        ));
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

fn transactional_sync(mods_dir: &Path, sources: &[(ClientModSpec, PathBuf)]) -> Result<(), String> {
    let backup_dir = mods_dir.join(format!(".lazybuilder-sync-backup-{}", std::process::id()));
    if backup_dir.exists() {
        fs::remove_dir_all(&backup_dir).map_err(|error| error.to_string())?;
    }
    fs::create_dir_all(&backup_dir)
        .map_err(|error| format!("Could not create Client Sync rollback area: {error}"))?;

    let cleanup = |mods_dir: &Path, backup_dir: &Path| {
        for spec in MODS {
            let _ = fs::remove_file(mods_dir.join(format!("{}.incoming", spec.file_name)));
        }
        let _ = fs::remove_dir_all(backup_dir);
    };

    for (spec, source) in sources {
        let incoming = mods_dir.join(format!("{}.incoming", spec.file_name));
        let _ = fs::remove_file(&incoming);
        if let Err(error) = fs::copy(source, &incoming) {
            cleanup(mods_dir, &backup_dir);
            return Err(format!("Could not stage {}: {error}", spec.display_name));
        }
        if !files_equal(source, &incoming)? {
            cleanup(mods_dir, &backup_dir);
            return Err(format!("Staged {} failed verification.", spec.display_name));
        }
    }

    let mut backups = Vec::<(PathBuf, PathBuf)>::new();
    for spec in MODS {
        for installed in owned_mod_paths(mods_dir, spec)? {
            let file_name = installed
                .file_name()
                .ok_or_else(|| "Installed LazyBuilder mod has no filename.".to_string())?;
            let backup = backup_dir.join(file_name);
            fs::copy(&installed, &backup)
                .map_err(|error| format!("Could not create rollback backup for {}: {error}", installed.display()))?;
            if !files_equal(&installed, &backup)? {
                cleanup(mods_dir, &backup_dir);
                return Err(format!("Rollback backup verification failed for {}", installed.display()));
            }
            backups.push((installed, backup));
        }
    }

    let publish = (|| -> Result<(), String> {
        for (spec, _) in sources {
            remove_owned_mod_files(mods_dir, *spec)?;
            let incoming = mods_dir.join(format!("{}.incoming", spec.file_name));
            let target = mods_dir.join(spec.file_name);
            fs::rename(&incoming, &target)
                .map_err(|error| format!("Could not publish {}: {error}", spec.display_name))?;
        }
        Ok(())
    })();

    if let Err(error) = publish {
        let mut rollback_errors = Vec::new();
        for spec in MODS {
            if let Err(rollback_error) = remove_owned_mod_files(mods_dir, spec) {
                rollback_errors.push(rollback_error);
            }
        }
        for (target, backup) in &backups {
            if let Err(rollback_error) = fs::copy(backup, target) {
                rollback_errors.push(format!("Could not restore {}: {rollback_error}", target.display()));
            }
        }
        cleanup(mods_dir, &backup_dir);
        return if rollback_errors.is_empty() {
            Err(format!("Client Sync failed and previous LazyBuilder mods were restored: {error}"))
        } else {
            Err(format!("Client Sync failed: {error}; rollback also reported: {}", rollback_errors.join("; ")))
        };
    }

    cleanup(mods_dir, &backup_dir);
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
        let Some((_, after_minecraft)) = line.split_once("Loading Minecraft ") else {
            continue;
        };
        let Some((version, after_version)) = after_minecraft.split_once(" with Fabric Loader ") else {
            continue;
        };
        if !version.trim().is_empty() && !after_version.trim().is_empty() {
            return Ok(Some((version.trim().to_string(), "fabric".into())));
        }
    }
    Ok(None)
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
