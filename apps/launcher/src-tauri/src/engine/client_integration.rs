use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashSet;
use std::env;
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};

pub const TARGET_MINECRAFT_VERSION: &str = "1.21.4";

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
    pub game_version: Option<String>,
    pub loader: Option<String>,
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
    pub mods: Vec<ClientModStatus>,
    pub ready: bool,
    pub message: String,
}

#[derive(Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ClientIntegrationConfig {
    selected_profile_path: Option<String>,
}

pub fn status(resource_dir: Option<&Path>) -> Result<ClientIntegrationStatus, String> {
    let config = load_config()?;
    let selected_path = config.selected_profile_path.as_deref();
    let mut profiles = discover_profiles(selected_path)?;
    profiles.sort_by(|a, b| a.name.to_ascii_lowercase().cmp(&b.name.to_ascii_lowercase()));

    let selected_profile = profiles.iter().find(|profile| profile.selected).cloned();
    let modrinth_detected = !profile_roots().is_empty();
    let mods = match selected_profile.as_ref() {
        Some(profile) if profile.compatible => component_statuses(Path::new(&profile.path), resource_dir)?,
        _ => MODS
            .iter()
            .map(|spec| ClientModStatus {
                id: spec.id.into(),
                display_name: spec.display_name.into(),
                state: "Not checked".into(),
                installed_files: Vec::new(),
                target_file: spec.file_name.into(),
                bundled: resolve_source(resource_dir, *spec).is_ok(),
            })
            .collect(),
    };

    let ready = selected_profile.as_ref().is_some_and(|profile| profile.compatible)
        && mods.iter().all(|entry| entry.state == "Installed");

    let message = if !modrinth_detected {
        "Modrinth App profiles were not detected on this PC.".into()
    } else if selected_profile.is_none() {
        "Choose the Modrinth profile you use for LazyBuilder.".into()
    } else if !selected_profile.as_ref().is_some_and(|profile| profile.compatible) {
        format!("LazyBuilder requires Minecraft {TARGET_MINECRAFT_VERSION} with Fabric.")
    } else if ready {
        "LazyBuilder client is ready in the selected Modrinth profile.".into()
    } else {
        "Sync the selected profile to install or repair LazyBuilder client components.".into()
    };

    Ok(ClientIntegrationStatus {
        modrinth_detected,
        profiles,
        selected_profile,
        mods,
        ready,
        message,
    })
}

pub fn select_profile(profile_path: &str) -> Result<(), String> {
    let requested = canonical_profile_path(Path::new(profile_path))?;
    let profiles = discover_profiles(None)?;
    let selected = profiles
        .iter()
        .find(|profile| Path::new(&profile.path) == requested)
        .ok_or_else(|| "The selected folder is not a detected Modrinth profile.".to_string())?;
    if !selected.compatible {
        return Err(format!(
            "This profile is not compatible with LazyBuilder. Required: Minecraft {TARGET_MINECRAFT_VERSION} + Fabric."
        ));
    }
    save_config(&ClientIntegrationConfig {
        selected_profile_path: Some(requested.to_string_lossy().to_string()),
    })
}

pub fn sync(resource_dir: Option<&Path>) -> Result<(), String> {
    let config = load_config()?;
    let selected = config
        .selected_profile_path
        .ok_or_else(|| "Choose a Modrinth profile before syncing the client.".to_string())?;
    let profile_path = canonical_profile_path(Path::new(&selected))?;
    let profile = inspect_profile(&profile_path, Some(&profile_path))?
        .ok_or_else(|| "The selected Modrinth profile is no longer available.".to_string())?;
    if !profile.compatible {
        return Err(format!(
            "This profile is not compatible with LazyBuilder. Required: Minecraft {TARGET_MINECRAFT_VERSION} + Fabric."
        ));
    }

    let mods_dir = profile_path.join("mods");
    fs::create_dir_all(&mods_dir)
        .map_err(|error| format!("Could not create the Modrinth profile mods directory: {error}"))?;

    let sources: Vec<(ClientModSpec, PathBuf)> = MODS
        .iter()
        .map(|spec| resolve_source(resource_dir, *spec).map(|path| (*spec, path)))
        .collect::<Result<_, _>>()?;

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

fn discover_profiles(selected_path: Option<&str>) -> Result<Vec<ClientProfileSummary>, String> {
    let selected = selected_path.and_then(|value| canonical_profile_path(Path::new(value)).ok());
    let mut profiles = Vec::new();
    let mut seen = HashSet::new();
    for root in profile_roots() {
        let entries = match fs::read_dir(&root) {
            Ok(entries) => entries,
            Err(_) => continue,
        };
        for entry in entries.flatten() {
            let path = entry.path();
            if !path.is_dir() {
                continue;
            }
            let canonical = match canonical_profile_path(&path) {
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
    Ok(profiles)
}

fn inspect_profile(path: &Path, selected: Option<&Path>) -> Result<Option<ClientProfileSummary>, String> {
    if !path.is_dir() {
        return Ok(None);
    }
    let metadata_path = path.join("profile.json");
    if !metadata_path.is_file() {
        return Ok(None);
    }
    let text = fs::read_to_string(&metadata_path)
        .map_err(|error| format!("Could not read {}: {error}", metadata_path.display()))?;
    let json: Value = serde_json::from_str(&text)
        .map_err(|error| format!("Could not parse {}: {error}", metadata_path.display()))?;

    let name = find_json_string(&json, &["name", "displayname", "display_name"])
        .or_else(|| path.file_name().and_then(|value| value.to_str()).map(ToOwned::to_owned))
        .unwrap_or_else(|| "Modrinth profile".into());
    let game_version = find_json_string(
        &json,
        &["gameversion", "game_version", "minecraftversion", "minecraft_version"],
    );
    let loader = find_json_string(&json, &["loader", "loadertype", "loader_type", "modloader", "mod_loader"]);
    let compatible = game_version.as_deref() == Some(TARGET_MINECRAFT_VERSION)
        && loader.as_deref().is_some_and(|value| value.to_ascii_lowercase().contains("fabric"));

    Ok(Some(ClientProfileSummary {
        name,
        path: path.to_string_lossy().to_string(),
        game_version,
        loader,
        compatible,
        selected: selected.is_some_and(|selected| selected == path),
    }))
}

fn find_json_string(value: &Value, keys: &[&str]) -> Option<String> {
    match value {
        Value::Object(map) => {
            for (key, value) in map {
                let normalized = key.to_ascii_lowercase().replace(['-', ' '], "_");
                let compact = normalized.replace('_', "");
                if keys.iter().any(|candidate| {
                    let candidate_normalized = candidate.to_ascii_lowercase().replace(['-', ' '], "_");
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

fn profile_roots() -> Vec<PathBuf> {
    let mut roots = Vec::new();
    if let Some(explicit) = env::var_os("MODRINTH_PROFILES_DIR") {
        push_existing_root(&mut roots, PathBuf::from(explicit));
    }
    if let Some(appdata) = env::var_os("APPDATA") {
        let appdata = PathBuf::from(appdata);
        push_existing_root(&mut roots, appdata.join("ModrinthApp").join("profiles"));
        push_existing_root(&mut roots, appdata.join("com.modrinth.theseus").join("profiles"));
    }
    if let Some(local) = env::var_os("LOCALAPPDATA") {
        push_existing_root(&mut roots, PathBuf::from(local).join("ModrinthApp").join("profiles"));
    }
    roots
}

fn push_existing_root(roots: &mut Vec<PathBuf>, candidate: PathBuf) {
    if let Ok(canonical) = candidate.canonicalize() {
        if candidate.is_dir() && !roots.contains(&canonical) {
            roots.push(canonical);
        }
    }
}

fn canonical_profile_path(path: &Path) -> Result<PathBuf, String> {
    let canonical = path
        .canonicalize()
        .map_err(|error| format!("Could not resolve Modrinth profile path: {error}"))?;
    if !canonical.is_dir() {
        return Err("Modrinth profile path is not a directory.".into());
    }
    let allowed = profile_roots().iter().any(|root| canonical.starts_with(root));
    if !allowed {
        return Err("Selected profile is outside the detected Modrinth profiles directory.".into());
    }
    Ok(canonical)
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

    #[test]
    fn finds_nested_modrinth_profile_metadata() {
        let value: Value = serde_json::json!({
            "name": "Builder 1.21.4",
            "metadata": {
                "game_version": "1.21.4",
                "loader": "fabric"
            }
        });
        assert_eq!(find_json_string(&value, &["gameversion", "game_version"]).as_deref(), Some("1.21.4"));
        assert_eq!(find_json_string(&value, &["loader"]).as_deref(), Some("fabric"));
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
