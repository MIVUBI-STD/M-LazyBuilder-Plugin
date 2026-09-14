use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashSet;
use std::env;
use std::fs;
use std::fs::OpenOptions;
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};

pub const TARGET_MINECRAFT_VERSION: &str = "1.21.4";
const MAX_LOG_SCAN_BYTES: u64 = 2 * 1024 * 1024;

const MODS: [ClientModSpec; 3] = [
    ClientModSpec {
        id: "map-manager",
        display_name: "Map Manager",
        file_name: "lazybuilder-map-manager-0.1.0-SNAPSHOT.jar",
        file_prefix: "lazybuilder-map-manager-",
        dev_relative: "mods/map-manager/build/libs/lazybuilder-map-manager-0.1.0-SNAPSHOT.jar",
    },
    ClientModSpec {
        id: "utility-manager",
        display_name: "Utility Manager",
        file_name: "lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar",
        file_prefix: "lazybuilder-utility-manager-",
        dev_relative: "mods/utility-manager/build/libs/lazybuilder-utility-manager-0.1.0-SNAPSHOT.jar",
    },
    ClientModSpec {
        id: "performance-manager",
        display_name: "Performance Manager",
        file_name: "lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar",
        file_prefix: "lazybuilder-performance-manager-",
        dev_relative: "mods/performance-manager/build/libs/lazybuilder-performance-manager-0.1.0-SNAPSHOT.jar",
    },
];

#[derive(Clone, Copy)]
struct ClientModSpec {
    id: &'static str,
    display_name: &'static str,
    file_name: &'static str,
    file_prefix: &'static str,
    dev_relative: &'static str,
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
        "The previously selected Modrinth profile is no longer available. Select the profile again.".into()
    } else if !modrinth_detected {
        "Modrinth profiles were not detected automatically. Select the exact Modrinth profile you use for LazyBuilder.".into()
    } else if profiles.is_empty() {
        "No Modrinth profiles were found. Select the exact profile folder manually.".into()
    } else if selected_profile.is_none() {
        "Choose a detected profile or select the exact Modrinth profile folder manually.".into()
    } else if selected_profile
        .as_ref()
        .is_some_and(|profile| profile.game_version.is_none() || profile.loader.is_none())
    {
        "Open this profile once from Modrinth, then refresh Client Setup so LazyBuilder can verify Minecraft and Fabric from the last launch.".into()
    } else if !selected_profile.as_ref().is_some_and(|profile| profile.compatible) {
        format!("This profile is not compatible. LazyBuilder requires Minecraft {TARGET_MINECRAFT_VERSION} with Fabric.")
    } else if ready {
        "LazyBuilder client is ready in the selected Modrinth profile.".into()
    } else {
        "Sync the selected profile to install or repair LazyBuilder client components.".into()
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
        if profile.game_version.is_none() || profile.loader.is_none() {
            return Err("Open this profile once from Modrinth, then refresh Client Setup before syncing.".into());
        }
        return Err(format!(
            "This profile is not compatible with LazyBuilder. Required: Minecraft {TARGET_MINECRAFT_VERSION} + Fabric."
        ));
    }

    let mods_dir = profile_path.join("mods");
    ensure_mods_directory_writable(&mods_dir)?;

    let sources: Vec<(ClientModSpec, PathBuf)> = MODS
        .iter()
        .map(|spec| resolve_source(resource_dir, *spec).map(|path| (*spec, path)))
        .collect::<Result<_, _>>()?;

    // Stage and verify every new file before removing any installed LazyBuilder file.
    for (spec, source) in &sources {
        let incoming = mods_dir.join(format!("{}.incoming", spec.file_name));
        if incoming.exists() {
            fs::remove_file(&incoming).map_err(|error| error.to_string())?;
        }
        fs::copy(source, &incoming).map_err(|error| {
            format!("Could not stage {} for the selected Modrinth profile: {error}", spec.display_name)
        })?;
        if !files_equal(source, &incoming)? {
            let _ = fs::remove_file(&incoming);
            return Err(format!("Staged {} failed verification.", spec.display_name));
        }
    }

    for (spec, _) in &sources {
        remove_owned_mod_files(&mods_dir, *spec)?;
        let incoming = mods_dir.join(format!("{}.incoming", spec.file_name));
        let target = mods_dir.join(spec.file_name);
        fs::rename(&incoming, &target).map_err(|error| {
            format!("Could not publish {} to the selected Modrinth profile: {error}", spec.display_name)
        })?;
    }

    Ok(())
}

fn unchecked_component_statuses(resource_dir: Option<&Path>) -> Vec<ClientModStatus> {
    MODS
        .iter()
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
                if target.is_file() && files_equal(source, &target)? {
                    "Installed"
                } else {
                    "Update required"
                }
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

    // A persisted custom profile remains visible even when its root is not one of the
    // platform defaults. This also makes rename/move failures explicit instead of silently
    // switching to another profile with the same display name.
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

    // Current Modrinth versions keep instance metadata in app.db. LazyBuilder intentionally
    // does not couple to that private schema. Runtime evidence from latest.log wins; older
    // profile.json metadata is used only as a fallback.
    let folder_name = path
        .file_name()
        .and_then(|value| value.to_str())
        .unwrap_or("Modrinth profile")
        .to_string();
    let mut name = folder_name;
    let mut game_version = None;
    let mut loader = None;
    let mut verification = "Unverified".to_string();

    let legacy_metadata_path = path.join("profile.json");
    if legacy_metadata_path.is_file() {
        if let Ok(text) = fs::read_to_string(&legacy_metadata_path) {
            if let Ok(json) = serde_json::from_str::<Value>(&text) {
                name = find_json_string(&json, &["name", "displayname", "display_name"]).unwrap_or(name);
                game_version = find_json_string(
                    &json,
                    &["gameversion", "game_version", "minecraftversion", "minecraft_version"],
                );
                loader = find_json_string(
                    &json,
                    &["loader", "loadertype", "loader_type", "modloader", "mod_loader"],
                );
                if game_version.is_some() || loader.is_some() {
                    verification = "Legacy metadata".into();
                }
            }
        }
    }

    if let Some((runtime_version, runtime_loader)) = runtime_identity_from_latest_log(path)? {
        game_version = Some(runtime_version);
        loader = Some(runtime_loader);
        verification = "Last launch".into();
    }

    let compatible = game_version.as_deref() == Some(TARGET_MINECRAFT_VERSION)
        && loader
            .as_deref()
            .is_some_and(|value| value.to_ascii_lowercase().contains("fabric"));

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
        selected: selected.is_some_and(|selected| selected == path),
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
        if version.trim().is_empty() || after_version.trim().is_empty() {
            continue;
        }
        return Ok(Some((version.trim().to_string(), "fabric".into())));
    }
    Ok(None)
}

fn find_json_string(value: &Value, keys: &[&str]) -> Option<String> {
    match value {
        Value::Object(map) => {
            for (key, value) in map {
                let normalized = normalize_key(key);
                let compact = normalized.replace('_', "");
                if keys.iter().any(|candidate| {
                    let candidate_normalized = normalize_key(candidate);
                    normalized == candidate_normalized || compact == candidate_normalized.replace('_', "")
                }) {
                    if let Some(text) = value.as_str() {
                        return Some(text.to_string());
                    }
                }
            }
            map.values().find_map(|value| find_json_string(value, keys))
        }
        Value::Array(values) => values.iter().find_map(|value| find_json_string(value, keys)),
        _ => None,
    }
}

fn normalize_key(value: &str) -> String {
    value
        .to_ascii_lowercase()
        .replace('-', "_")
        .replace(' ', "_")
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
    if !candidate.is_dir() {
        return;
    }
    if let Ok(canonical) = candidate.canonicalize() {
        if !roots.contains(&canonical) {
            roots.push(canonical);
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
        let is_profiles_root = canonical
            .file_name()
            .and_then(|value| value.to_str())
            .is_some_and(|value| value.eq_ignore_ascii_case("profiles"));
        if is_profiles_root {
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

fn owned_mod_files(mods_dir: &Path, spec: ClientModSpec) -> Result<Vec<String>, String> {
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
            files.push(name.to_string());
        }
    }
    files.sort();
    Ok(files)
}

fn remove_owned_mod_files(mods_dir: &Path, spec: ClientModSpec) -> Result<(), String> {
    if !mods_dir.is_dir() {
        return Ok(());
    }
    for entry in fs::read_dir(mods_dir).map_err(|error| error.to_string())? {
        let path = entry.map_err(|error| error.to_string())?.path();
        let Some(name) = path.file_name().and_then(|value| value.to_str()) else {
            continue;
        };
        let lower = name.to_ascii_lowercase();
        if path.is_file() && lower.starts_with(spec.file_prefix) && lower.ends_with(".jar") {
            fs::remove_file(&path).map_err(|error| {
                format!("Could not replace LazyBuilder-owned client mod {}: {error}", path.display())
            })?;
        }
    }
    Ok(())
}

fn resolve_source(resource_dir: Option<&Path>, spec: ClientModSpec) -> Result<PathBuf, String> {
    if let Some(resource_dir) = resource_dir {
        let bundled = resource_dir
            .join("resources")
            .join("client-mods")
            .join(spec.file_name);
        if bundled.is_file() {
            return Ok(bundled);
        }
    }
    let repo_root = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("../../..");
    let development = repo_root.join(spec.dev_relative);
    if development.is_file() {
        return Ok(development);
    }
    Err(format!(
        "Bundled {} is unavailable. Runtime-ready Launcher builds must include all three LazyBuilder client mods.",
        spec.display_name
    ))
}

fn config_path() -> Result<PathBuf, String> {
    let appdata = env::var_os("APPDATA").ok_or_else(|| "Windows APPDATA is unavailable.".to_string())?;
    Ok(PathBuf::from(appdata)
        .join("LazyBuilder")
        .join("client-integration.json"))
}

fn load_config() -> Result<ClientIntegrationConfig, String> {
    let path = config_path()?;
    if !path.is_file() {
        return Ok(ClientIntegrationConfig::default());
    }
    let text = fs::read_to_string(&path).map_err(|error| error.to_string())?;
    serde_json::from_str(&text).map_err(|error| format!("Could not read Client Setup configuration: {error}"))
}

fn save_config(config: &ClientIntegrationConfig) -> Result<(), String> {
    let path = config_path()?;
    let parent = path.parent().ok_or_else(|| "Client Setup config path has no parent.".to_string())?;
    fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    let payload = serde_json::to_string_pretty(config).map_err(|error| error.to_string())?;
    let incoming = path.with_extension("json.incoming");
    fs::write(&incoming, payload).map_err(|error| error.to_string())?;
    if path.exists() {
        fs::remove_file(&path).map_err(|error| error.to_string())?;
    }
    fs::rename(&incoming, &path).map_err(|error| error.to_string())
}

fn files_equal(left: &Path, right: &Path) -> Result<bool, String> {
    let left_meta = fs::metadata(left).map_err(|error| error.to_string())?;
    let right_meta = fs::metadata(right).map_err(|error| error.to_string())?;
    if left_meta.len() != right_meta.len() {
        return Ok(false);
    }
    let mut left_file = fs::File::open(left).map_err(|error| error.to_string())?;
    let mut right_file = fs::File::open(right).map_err(|error| error.to_string())?;
    let mut left_buffer = [0u8; 64 * 1024];
    let mut right_buffer = [0u8; 64 * 1024];
    loop {
        let left_count = left_file.read(&mut left_buffer).map_err(|error| error.to_string())?;
        let right_count = right_file.read(&mut right_buffer).map_err(|error| error.to_string())?;
        if left_count != right_count {
            return Ok(false);
        }
        if left_count == 0 {
            return Ok(true);
        }
        if left_buffer[..left_count] != right_buffer[..right_count] {
            return Ok(false);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    #[test]
    fn finds_nested_legacy_profile_metadata() {
        let value: Value = serde_json::json!({
            "name": "Builder 1.21.4",
            "metadata": { "game_version": "1.21.4", "loader": "fabric" }
        });
        assert_eq!(find_json_string(&value, &["gameversion", "game_version"]).as_deref(), Some("1.21.4"));
        assert_eq!(find_json_string(&value, &["loader"]).as_deref(), Some("fabric"));
    }

    #[test]
    fn detects_fabric_runtime_identity_from_latest_log() {
        let unique = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        let root = env::temp_dir().join(format!("lazybuilder-client-profile-{unique}"));
        let logs = root.join("logs");
        fs::create_dir_all(&logs).unwrap();
        fs::write(
            logs.join("latest.log"),
            "[main/INFO]: Loading Minecraft 1.21.4 with Fabric Loader 0.16.10\n",
        )
        .unwrap();
        let identity = runtime_identity_from_latest_log(&root).unwrap();
        assert_eq!(identity, Some(("1.21.4".into(), "fabric".into())));
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn manual_profile_must_be_inside_profiles_directory() {
        let unique = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        let root = env::temp_dir().join(format!("lazybuilder-manual-profile-{unique}"));
        let profile = root.join("profiles").join("Builder 1.21.4");
        fs::create_dir_all(&profile).unwrap();
        assert!(canonical_manual_profile_path(&profile).is_ok());
        assert!(canonical_manual_profile_path(&root).is_err());
        assert!(canonical_manual_profile_path(&root.join("profiles")).is_err());
        let _ = fs::remove_dir_all(root);
    }

    #[test]
    fn lazybuilder_owned_prefixes_are_narrow() {
        for spec in MODS {
            assert!(spec.file_prefix.starts_with("lazybuilder-"));
            assert!(spec.file_name.starts_with(spec.file_prefix));
            assert!(spec.file_name.ends_with(".jar"));
        }
    }
}
