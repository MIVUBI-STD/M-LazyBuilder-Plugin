use crate::engine::paths;
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashSet};
use std::fs::{self, File};
use std::io::Read;
use std::path::{Path, PathBuf};
use zip::ZipArchive;

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PluginSummary {
    pub id: String,
    pub display_name: String,
    pub version: String,
    pub category: String,
    pub state: String,
    pub problem_detail: Option<String>,
}

#[derive(Clone)]
struct PluginMetadata {
    id: String,
    name: String,
    version: String,
    dependencies: Vec<String>,
}

#[derive(Deserialize)]
struct PluginYaml {
    name: String,
    version: serde_yaml::Value,
    #[serde(default)]
    depend: Vec<String>,
}

#[derive(Clone)]
struct ScannedPlugin {
    path: PathBuf,
    metadata: Option<PluginMetadata>,
    enabled: bool,
    error: Option<String>,
}

pub fn list_plugins() -> Result<Vec<PluginSummary>, String> {
    let workspace = paths::workspace_root()?;
    let mut scanned = scan_dir(&workspace.join("server").join("plugins"), true)?;
    scanned.extend(scan_dir(&workspace.join("server").join("plugins-disabled"), false)?);

    let valid_ids: HashSet<String> = scanned.iter()
        .filter_map(|item| item.metadata.as_ref().map(|meta| meta.id.clone()))
        .collect();
    let enabled_ids: HashSet<String> = scanned.iter()
        .filter(|item| item.enabled)
        .filter_map(|item| item.metadata.as_ref().map(|meta| meta.id.clone()))
        .collect();

    let mut groups: BTreeMap<String, Vec<&ScannedPlugin>> = BTreeMap::new();
    for item in scanned.iter().filter(|item| item.metadata.is_some()) {
        let id = item.metadata.as_ref().unwrap().id.clone();
        groups.entry(id).or_default().push(item);
    }

    let mut result = Vec::new();
    for (id, items) in groups {
        let primary = items.iter().find(|item| item.enabled).copied().unwrap_or(items[0]);
        let metadata = primary.metadata.as_ref().unwrap();
        let mut state = if primary.enabled { "Enabled" } else { "Disabled" }.to_string();
        let mut problem = None;

        if items.len() > 1 {
            state = "Problem".into();
            problem = Some(format!("Duplicate plugin JARs detected: {}", items.iter()
                .map(|item| item.path.file_name().and_then(|value| value.to_str()).unwrap_or("unknown"))
                .collect::<Vec<_>>().join(", ")));
        } else if primary.enabled {
            let missing: Vec<_> = metadata.dependencies.iter().filter(|dep| !valid_ids.contains(*dep)).cloned().collect();
            let disabled: Vec<_> = metadata.dependencies.iter().filter(|dep| valid_ids.contains(*dep) && !enabled_ids.contains(*dep)).cloned().collect();
            if !missing.is_empty() {
                state = "Problem".into();
                problem = Some(format!("Missing required dependencies: {}", missing.join(", ")));
            } else if !disabled.is_empty() {
                state = "Problem".into();
                problem = Some(format!("Required dependencies are disabled: {}", disabled.join(", ")));
            }
        }

        result.push(PluginSummary {
            id,
            display_name: metadata.name.clone(),
            version: metadata.version.clone(),
            category: category_for(&metadata.id, &metadata.name).into(),
            state,
            problem_detail: problem,
        });
    }

    for item in scanned.into_iter().filter(|item| item.metadata.is_none()) {
        let display = item.path.file_stem().and_then(|value| value.to_str()).unwrap_or("Invalid plugin").to_string();
        result.push(PluginSummary {
            id: format!("invalid-{}", normalize_id(&display)),
            display_name: display,
            version: "Unknown".into(),
            category: "Other".into(),
            state: "Problem".into(),
            problem_detail: item.error,
        });
    }

    result.sort_by(|left, right| left.category.cmp(&right.category).then(left.display_name.cmp(&right.display_name)));
    Ok(result)
}

fn scan_dir(directory: &Path, enabled: bool) -> Result<Vec<ScannedPlugin>, String> {
    if !directory.is_dir() { return Ok(Vec::new()); }
    let mut result = Vec::new();
    for entry in fs::read_dir(directory).map_err(|error| error.to_string())? {
        let path = entry.map_err(|error| error.to_string())?.path();
        if path.extension().and_then(|value| value.to_str()).map(|value| value.eq_ignore_ascii_case("jar")) != Some(true) { continue; }
        match read_metadata(&path) {
            Ok(metadata) => result.push(ScannedPlugin { path, metadata: Some(metadata), enabled, error: None }),
            Err(error) => result.push(ScannedPlugin { path, metadata: None, enabled, error: Some(error) }),
        }
    }
    Ok(result)
}

fn read_metadata(path: &Path) -> Result<PluginMetadata, String> {
    let file = File::open(path).map_err(|error| error.to_string())?;
    let mut archive = ZipArchive::new(file).map_err(|error| format!("Invalid plugin JAR: {error}"))?;
    let mut entry = archive.by_name("plugin.yml")
        .or_else(|_| archive.by_name("paper-plugin.yml"))
        .map_err(|_| "JAR does not contain plugin.yml or paper-plugin.yml".to_string())?;
    let mut text = String::new();
    entry.read_to_string(&mut text).map_err(|error| error.to_string())?;
    let parsed: PluginYaml = serde_yaml::from_str(&text).map_err(|error| format!("Invalid plugin metadata: {error}"))?;
    let version = match parsed.version {
        serde_yaml::Value::String(value) => value,
        value => serde_yaml::to_string(&value).unwrap_or_else(|_| "Unknown".into()).trim().to_string(),
    };
    let id = normalize_id(&parsed.name);
    let dependencies = parsed.depend.into_iter().map(|value| normalize_id(&value)).collect();
    Ok(PluginMetadata { id, name: parsed.name, version, dependencies })
}

fn normalize_id(value: &str) -> String {
    let normalized: String = value.trim().to_lowercase().chars()
        .filter(|ch| ch.is_ascii_alphanumeric() || *ch == '-' || *ch == '_')
        .map(|ch| if ch == '_' { '-' } else { ch })
        .collect();
    if normalized.is_empty() { "plugin".into() } else { normalized }
}

fn category_for(id: &str, name: &str) -> &'static str {
    match id {
        "world-manager" => "World Management",
        "utilities-manager" => "Server Utilities",
        "axiom" | "axiompaper" | "fastasyncworldedit" | "fawe" | "fastasyncvoxelsniper" | "ezedits" | "metabrushes" => "Build Tools",
        _ => match normalize_id(name).as_str() {
            "world-manager" => "World Management",
            "utilities-manager" => "Server Utilities",
            _ => "Other",
        }
    }
}
