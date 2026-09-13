use crate::engine::paths;
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::{SystemTime, UNIX_EPOCH};
use zip::ZipArchive;

const ALLOWED_CATEGORIES: [&str; 6] = [
    "World Management",
    "Build Tools",
    "Server Utilities",
    "Performance",
    "Dependencies",
    "Other",
];

#[derive(Default)]
pub struct PluginManagerState {
    mutation_gate: Mutex<()>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PluginSummary {
    pub id: String,
    pub display_name: String,
    pub version: String,
    pub category: String,
    pub state: String,
    pub problem_detail: Option<String>,
    pub candidate_files: Option<Vec<String>>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PluginInstallResult {
    pub success: bool,
    pub plugin_id: String,
    pub message: Option<String>,
    pub restart_required: bool,
}

#[derive(Clone)]
struct PluginMetadata {
    id: String,
    name: String,
    version: String,
    api_version: String,
    dependencies: Vec<String>,
}

#[derive(Deserialize)]
struct PluginYaml {
    name: String,
    version: serde_yaml::Value,
    #[serde(rename = "api-version", default)]
    api_version: Option<serde_yaml::Value>,
    #[serde(default)]
    depend: Vec<String>,
    #[serde(default)]
    dependencies: Option<serde_yaml::Value>,
}

#[derive(Clone)]
struct ScannedPlugin {
    path: PathBuf,
    metadata: Option<PluginMetadata>,
    enabled: bool,
    error: Option<String>,
}

impl PluginManagerState {
    pub fn list_plugins(&self) -> Result<Vec<PluginSummary>, String> {
        let _guard = self.mutation_gate.lock().map_err(|_| "plugin manager lock poisoned".to_string())?;
        let workspace = paths::workspace_root()?;
        ensure_plugin_manager_layout(&workspace)?;
        list_plugins_inner(&workspace)
    }

    pub fn install(&self, jar_path: &str) -> Result<PluginInstallResult, String> {
        let _guard = self.mutation_gate.lock().map_err(|_| "plugin manager lock poisoned".to_string())?;
        let workspace = paths::workspace_root()?;
        ensure_plugin_manager_layout(&workspace)?;
        let source = PathBuf::from(jar_path);
        let incoming = match read_metadata(&source).and_then(|metadata| {
            validate_target_compatibility(&metadata)?;
            Ok(metadata)
        }) {
            Ok(metadata) => metadata,
            Err(error) => return Ok(failed_result(problem_id_from_path(&source), error)),
        };

        let existing = find_all_by_id(&workspace, &incoming.id)?;
        if let Some(current) = existing.first() {
            return Ok(failed_result(
                incoming.id,
                format!(
                    "Plugin is already installed (current {}, selected {}). Use Update instead.",
                    current.metadata.as_ref().map(|value| value.version.as_str()).unwrap_or("Unknown"),
                    incoming.version
                ),
            ));
        }

        if let Some(problem) = dependency_problem(&workspace, &incoming, None)? {
            return Ok(failed_result(incoming.id, problem));
        }

        let plugins_dir = plugins_directory(&workspace);
        fs::create_dir_all(&plugins_dir).map_err(|error| error.to_string())?;
        let destination = plugins_dir.join(safe_jar_name(&incoming.name, &incoming.version));
        if destination.exists() {
            return Ok(failed_result(incoming.id, "Target plugin filename already exists."));
        }
        copy_atomic(&source, &destination)?;
        Ok(success_result(incoming.id, "Plugin installed. Restart required."))
    }

    pub fn update(&self, plugin_id: &str, jar_path: &str) -> Result<PluginInstallResult, String> {
        let _guard = self.mutation_gate.lock().map_err(|_| "plugin manager lock poisoned".to_string())?;
        let workspace = paths::workspace_root()?;
        ensure_plugin_manager_layout(&workspace)?;
        let canonical_id = normalize_id(plugin_id);
        let source = PathBuf::from(jar_path);
        let incoming = match read_metadata(&source).and_then(|metadata| {
            validate_target_compatibility(&metadata)?;
            Ok(metadata)
        }) {
            Ok(metadata) => metadata,
            Err(error) => return Ok(failed_result(canonical_id, error)),
        };

        if incoming.id != canonical_id {
            return Ok(failed_result(canonical_id, "Selected JAR belongs to a different plugin."));
        }

        let existing = find_all_by_id(&workspace, &canonical_id)?;
        if existing.is_empty() {
            return Ok(failed_result(canonical_id, "Plugin is not installed."));
        }
        if existing.len() > 1 {
            return Ok(failed_result(
                canonical_id,
                "Duplicate plugin versions must be resolved before updating. Keep one JAR from the duplicate list, then retry.",
            ));
        }
        if let Some(problem) = dependency_problem(&workspace, &incoming, Some(&canonical_id))? {
            return Ok(failed_result(canonical_id, problem));
        }

        let current = &existing[0];
        let current_metadata = current.metadata.as_ref().expect("validated scan");
        let backup_dir = backup_directory(&workspace);
        fs::create_dir_all(&backup_dir).map_err(|error| error.to_string())?;
        let backup_name = format!(
            "{}-{}.jar",
            current.path.file_stem().and_then(|value| value.to_str()).unwrap_or("plugin"),
            timestamp_suffix()
        );
        let backup = backup_dir.join(backup_name);
        fs::copy(&current.path, &backup).map_err(|error| error.to_string())?;

        let target_dir = if current.enabled { plugins_directory(&workspace) } else { disabled_directory(&workspace) };
        fs::create_dir_all(&target_dir).map_err(|error| error.to_string())?;
        let destination = target_dir.join(safe_jar_name(&incoming.name, &incoming.version));
        let current_canonical = canonical_path(&current.path)?;
        let same_destination = canonical_candidate(&destination)? == current_canonical;

        if !same_destination && destination.exists() {
            return Ok(failed_result(canonical_id, "Target plugin filename already exists."));
        }

        let staged = destination.with_extension("jar.incoming");
        stage_copy(&source, &staged)?;

        if same_destination {
            if let Err(error) = replace_file(&staged, &destination) {
                let _ = restore_from_backup(&backup, &current.path);
                return Err(format!("Plugin update could not replace the active JAR: {error}"));
            }
        } else {
            if let Err(error) = fs::rename(&staged, &destination) {
                let _ = fs::remove_file(&staged);
                return Err(format!("Plugin update could not publish the new JAR: {error}"));
            }
            if let Err(error) = fs::remove_file(&current.path) {
                let _ = fs::remove_file(&destination);
                return Err(format!("Plugin update could not retire the previous JAR: {error}"));
            }
        }

        Ok(success_result(
            canonical_id,
            format!("Updated {} → {}. Restart required.", current_metadata.version, incoming.version),
        ))
    }

    pub fn set_enabled(&self, plugin_id: &str, enabled: bool) -> Result<(), String> {
        let _guard = self.mutation_gate.lock().map_err(|_| "plugin manager lock poisoned".to_string())?;
        let workspace = paths::workspace_root()?;
        ensure_plugin_manager_layout(&workspace)?;
        let canonical_id = normalize_id(plugin_id);
        let existing = find_all_by_id(&workspace, &canonical_id)?;
        if existing.is_empty() {
            return Err("Plugin is not installed.".into());
        }
        if existing.len() > 1 {
            return Err("Duplicate plugin versions must be resolved first.".into());
        }

        let current = &existing[0];
        if current.enabled == enabled {
            return Ok(());
        }
        let metadata = current.metadata.as_ref().expect("validated scan");
        if enabled {
            if let Some(problem) = dependency_problem(&workspace, metadata, Some(&canonical_id))? {
                return Err(problem);
            }
        }

        let target_dir = if enabled { plugins_directory(&workspace) } else { disabled_directory(&workspace) };
        fs::create_dir_all(&target_dir).map_err(|error| error.to_string())?;
        let file_name = current.path.file_name().ok_or_else(|| "Plugin filename is invalid.".to_string())?;
        let destination = target_dir.join(file_name);
        if destination.exists() {
            return Err("Target plugin file already exists.".into());
        }
        fs::rename(&current.path, destination).map_err(|error| error.to_string())
    }

    pub fn remove(&self, plugin_id: &str, remove_data: bool) -> Result<(), String> {
        let _guard = self.mutation_gate.lock().map_err(|_| "plugin manager lock poisoned".to_string())?;
        let workspace = paths::workspace_root()?;
        ensure_plugin_manager_layout(&workspace)?;
        let canonical_id = normalize_id(plugin_id);
        let existing = find_all_by_id(&workspace, &canonical_id)?;
        if existing.is_empty() {
            return Ok(());
        }
        if existing.len() > 1 {
            return Err("Duplicate plugin versions must be resolved before removal.".into());
        }

        let current = &existing[0];
        let metadata = current.metadata.as_ref().expect("validated scan");
        let backup_dir = backup_directory(&workspace);
        fs::create_dir_all(&backup_dir).map_err(|error| error.to_string())?;
        let stamp = timestamp_suffix();
        let jar_base = current
            .path
            .file_stem()
            .and_then(|value| value.to_str())
            .unwrap_or("plugin");
        let jar_backup = backup_dir.join(format!("{jar_base}-removed-{stamp}.jar"));
        if jar_backup.exists() {
            return Err("Plugin removal backup destination already exists.".into());
        }

        let data_move = if remove_data {
            let plugins_root = canonical_path(&plugins_directory(&workspace))?;
            let candidate = plugins_directory(&workspace).join(&metadata.name);
            let data_path = canonical_candidate(&candidate)?;
            if !data_path.starts_with(&plugins_root) {
                return Err("Refusing to remove plugin data outside the plugins directory.".into());
            }
            if data_path.exists() && !data_path.is_dir() {
                return Err("Plugin data path exists but is not a directory.".into());
            }
            if data_path.is_dir() {
                let quarantine = backup_dir.join(format!(
                    "{}-data-removed-{stamp}",
                    normalize_id(&metadata.name)
                ));
                if quarantine.exists() {
                    return Err("Plugin data quarantine destination already exists.".into());
                }
                Some((data_path, quarantine))
            } else {
                None
            }
        } else {
            None
        };

        fs::copy(&current.path, &jar_backup).map_err(|error| {
            format!("Could not create plugin removal backup: {error}")
        })?;

        if let Some((data_path, quarantine)) = &data_move {
            if let Err(error) = fs::rename(data_path, quarantine) {
                let _ = fs::remove_file(&jar_backup);
                return Err(format!("Could not quarantine plugin data before removal: {error}"));
            }
        }

        if let Err(error) = fs::remove_file(&current.path) {
            if let Some((data_path, quarantine)) = &data_move {
                if quarantine.exists() && !data_path.exists() {
                    let _ = fs::rename(quarantine, data_path);
                }
            }
            let _ = fs::remove_file(&jar_backup);
            return Err(format!("Could not remove plugin JAR: {error}"));
        }

        Ok(())
    }

    pub fn resolve_duplicates(&self, plugin_id: &str, keep_jar_file_name: &str) -> Result<PluginInstallResult, String> {
        let _guard = self.mutation_gate.lock().map_err(|_| "plugin manager lock poisoned".to_string())?;
        let workspace = paths::workspace_root()?;
        ensure_plugin_manager_layout(&workspace)?;
        let canonical_id = normalize_id(plugin_id);
        let requested = Path::new(keep_jar_file_name)
            .file_name()
            .and_then(|value| value.to_str())
            .ok_or_else(|| "Invalid plugin filename.".to_string())?;
        if requested != keep_jar_file_name {
            return Ok(failed_result(canonical_id, "Invalid plugin filename."));
        }

        let existing = find_all_by_id(&workspace, &canonical_id)?;
        if existing.len() < 2 {
            return Ok(failed_result(canonical_id, "No duplicate plugin JARs were found."));
        }
        let keep_index = existing.iter().position(|item| {
            item.path.file_name().and_then(|value| value.to_str()).map(|value| value.eq_ignore_ascii_case(requested)).unwrap_or(false)
        });
        let Some(keep_index) = keep_index else {
            return Ok(failed_result(canonical_id, "Selected JAR is not one of the duplicate candidates."));
        };

        let backup_dir = backup_directory(&workspace);
        fs::create_dir_all(&backup_dir).map_err(|error| error.to_string())?;
        let stamp = timestamp_suffix();
        let mut removals = Vec::new();

        for (index, item) in existing.iter().enumerate() {
            if index == keep_index {
                continue;
            }
            let base = item.path.file_stem().and_then(|value| value.to_str()).unwrap_or("plugin");
            let backup = backup_dir.join(format!("{base}-duplicate-{stamp}-{index}.jar"));
            if backup.exists() {
                return Err("Duplicate resolution backup destination already exists.".into());
            }
            fs::copy(&item.path, &backup).map_err(|error| error.to_string())?;
            if !files_equal(&item.path, &backup)? {
                let _ = fs::remove_file(&backup);
                return Err("Duplicate resolution backup verification failed.".into());
            }
            removals.push((item.path.clone(), backup));
        }

        let mut removed = Vec::new();
        for (source, backup) in &removals {
            if let Err(error) = fs::remove_file(source) {
                let mut rollback_errors = Vec::new();
                for (restored_source, restored_backup) in removed.into_iter().rev() {
                    if let Err(restore_error) = restore_from_backup(&restored_backup, &restored_source) {
                        rollback_errors.push(restore_error);
                    }
                }
                if rollback_errors.is_empty() {
                    return Err(format!("Duplicate resolution failed and previous files were restored: {error}"));
                }
                return Err(format!(
                    "Duplicate resolution failed: {error}; rollback also reported: {}",
                    rollback_errors.join("; ")
                ));
            }
            removed.push((source.clone(), backup.clone()));
        }

        Ok(success_result(
            canonical_id,
            format!("Duplicate JARs resolved. Keeping {requested}. Restart required."),
        ))
    }

    pub fn set_category(&self, plugin_id: &str, category: &str) -> Result<(), String> {
        let _guard = self.mutation_gate.lock().map_err(|_| "plugin manager lock poisoned".to_string())?;
        let workspace = paths::workspace_root()?;
        ensure_plugin_manager_layout(&workspace)?;
        let canonical_id = normalize_id(plugin_id);
        let canonical_category = ALLOWED_CATEGORIES
            .iter()
            .find(|value| value.eq_ignore_ascii_case(category))
            .ok_or_else(|| "Unknown plugin category.".to_string())?;
        let path = category_registry_path(&workspace);
        let mut overrides = load_category_overrides(&path);
        overrides.insert(canonical_id, (*canonical_category).to_string());
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|error| error.to_string())?;
        }
        let temporary = path.with_extension("json.tmp");
        let json = serde_json::to_string_pretty(&overrides).map_err(|error| error.to_string())?;
        fs::write(&temporary, json).map_err(|error| error.to_string())?;
        replace_file(&temporary, &path)
    }
}

fn list_plugins_inner(workspace: &Path) -> Result<Vec<PluginSummary>, String> {
    let mut scanned = scan_dir(&plugins_directory(workspace), true)?;
    scanned.extend(scan_dir(&disabled_directory(workspace), false)?);
    scanned.extend(scan_dir(&legacy_disabled_directory(workspace), false)?);

    let valid_ids: HashSet<String> = scanned
        .iter()
        .filter_map(|item| item.metadata.as_ref().map(|meta| meta.id.clone()))
        .collect();
    let enabled_ids: HashSet<String> = scanned
        .iter()
        .filter(|item| item.enabled)
        .filter_map(|item| item.metadata.as_ref().map(|meta| meta.id.clone()))
        .collect();
    let overrides = load_category_overrides(&category_registry_path(workspace));

    let mut groups: BTreeMap<String, Vec<&ScannedPlugin>> = BTreeMap::new();
    for item in scanned.iter().filter(|item| item.metadata.is_some()) {
        let id = item.metadata.as_ref().expect("filtered metadata").id.clone();
        groups.entry(id).or_default().push(item);
    }

    let mut result = Vec::new();
    for (id, items) in groups {
        let primary = items.iter().find(|item| item.enabled).copied().unwrap_or(items[0]);
        let metadata = primary.metadata.as_ref().expect("validated scan");
        let mut state = if primary.enabled { "Enabled" } else { "Disabled" }.to_string();
        let mut problem = None;
        let mut candidate_files = None;

        if items.len() > 1 {
            state = "Problem".into();
            let files = items
                .iter()
                .map(|item| item.path.file_name().and_then(|value| value.to_str()).unwrap_or("unknown").to_string())
                .collect::<Vec<_>>();
            problem = Some(format!("Duplicate plugin JARs detected: {}", files.join(", ")));
            candidate_files = Some(files);
        } else if primary.enabled {
            let missing: Vec<_> = metadata.dependencies.iter().filter(|dep| !valid_ids.contains(*dep)).cloned().collect();
            let disabled: Vec<_> = metadata
                .dependencies
                .iter()
                .filter(|dep| valid_ids.contains(*dep) && !enabled_ids.contains(*dep))
                .cloned()
                .collect();
            if !missing.is_empty() {
                state = "Problem".into();
                problem = Some(format!("Missing required dependencies: {}", missing.join(", ")));
            } else if !disabled.is_empty() {
                state = "Problem".into();
                problem = Some(format!("Required dependencies are disabled: {}", disabled.join(", ")));
            }
        }

        result.push(PluginSummary {
            id: id.clone(),
            display_name: metadata.name.clone(),
            version: metadata.version.clone(),
            category: resolve_category(&overrides, &id, &metadata.name),
            state,
            problem_detail: problem,
            candidate_files,
        });
    }

    for item in scanned.into_iter().filter(|item| item.metadata.is_none()) {
        let display = item.path.file_stem().and_then(|value| value.to_str()).unwrap_or("Invalid plugin").to_string();
        result.push(PluginSummary {
            id: problem_id_from_path(&item.path),
            display_name: display,
            version: "Unknown".into(),
            category: "Other".into(),
            state: "Problem".into(),
            problem_detail: item.error,
            candidate_files: None,
        });
    }

    result.sort_by(|left, right| {
        left.category
            .to_lowercase()
            .cmp(&right.category.to_lowercase())
            .then(left.display_name.to_lowercase().cmp(&right.display_name.to_lowercase()))
    });
    Ok(result)
}

fn scan_dir(directory: &Path, enabled: bool) -> Result<Vec<ScannedPlugin>, String> {
    if !directory.is_dir() {
        return Ok(Vec::new());
    }
    let mut result = Vec::new();
    for entry in fs::read_dir(directory).map_err(|error| error.to_string())? {
        let path = entry.map_err(|error| error.to_string())?.path();
        if path.extension().and_then(|value| value.to_str()).map(|value| value.eq_ignore_ascii_case("jar")) != Some(true) {
            continue;
        }
        match read_metadata(&path) {
            Ok(metadata) => result.push(ScannedPlugin { path, metadata: Some(metadata), enabled, error: None }),
            Err(error) => result.push(ScannedPlugin { path, metadata: None, enabled, error: Some(error) }),
        }
    }
    Ok(result)
}

fn read_metadata(path: &Path) -> Result<PluginMetadata, String> {
    if !path.is_file() {
        return Err(format!("Plugin JAR was not found: {}", path.display()));
    }
    if path.extension().and_then(|value| value.to_str()).map(|value| value.eq_ignore_ascii_case("jar")) != Some(true) {
        return Err("Selected file is not a JAR.".into());
    }

    let file = File::open(path).map_err(|error| error.to_string())?;
    let mut archive = ZipArchive::new(file).map_err(|error| format!("Invalid plugin JAR: {error}"))?;
    let metadata_entry_name = if archive.file_names().any(|name| name == "plugin.yml") {
        "plugin.yml"
    } else if archive.file_names().any(|name| name == "paper-plugin.yml") {
        "paper-plugin.yml"
    } else {
        return Err("JAR does not contain plugin.yml or paper-plugin.yml".to_string());
    };
    let mut entry = archive.by_name(metadata_entry_name).map_err(|error| format!("Failed to read plugin metadata: {error}"))?;
    let mut text = String::new();
    entry.read_to_string(&mut text).map_err(|error| error.to_string())?;
    let parsed: PluginYaml = serde_yaml::from_str(&text).map_err(|error| format!("Invalid plugin metadata: {error}"))?;

    let version = yaml_scalar_to_string(&parsed.version);
    let api_version = parsed.api_version.as_ref().map(yaml_scalar_to_string).unwrap_or_default();
    let id = normalize_id(&parsed.name);
    let mut dependencies = parsed.depend.into_iter().map(|value| normalize_id(&value)).collect::<Vec<_>>();
    dependencies.extend(parse_paper_dependencies(parsed.dependencies.as_ref()));
    dependencies.sort();
    dependencies.dedup();

    Ok(PluginMetadata { id, name: parsed.name, version, api_version, dependencies })
}

fn parse_paper_dependencies(value: Option<&serde_yaml::Value>) -> Vec<String> {
    match value {
        Some(serde_yaml::Value::Sequence(values)) => values
            .iter()
            .filter_map(|value| value.as_str())
            .map(normalize_id)
            .collect(),
        Some(serde_yaml::Value::Mapping(values)) => values
            .keys()
            .filter_map(|value| value.as_str())
            .map(normalize_id)
            .collect(),
        _ => Vec::new(),
    }
}

fn yaml_scalar_to_string(value: &serde_yaml::Value) -> String {
    match value {
        serde_yaml::Value::String(value) => value.clone(),
        serde_yaml::Value::Number(value) => value.to_string(),
        serde_yaml::Value::Bool(value) => value.to_string(),
        other => serde_yaml::to_string(other).unwrap_or_else(|_| "Unknown".into()).trim().to_string(),
    }
}

fn validate_target_compatibility(metadata: &PluginMetadata) -> Result<(), String> {
    if metadata.api_version.trim().is_empty() {
        return Ok(());
    }
    let mut parts = metadata.api_version.split('.');
    let major = parts.next().and_then(|value| value.parse::<u32>().ok());
    let minor = parts.next().and_then(|value| value.parse::<u32>().ok()).unwrap_or(0);
    if let Some(major) = major {
        if major > 1 || (major == 1 && minor > 21) {
            return Err(format!(
                "Plugin targets API {}, newer than LazyBuilder's Paper 1.21.4 baseline.",
                metadata.api_version
            ));
        }
    }
    Ok(())
}

fn find_all_by_id(workspace: &Path, canonical_id: &str) -> Result<Vec<ScannedPlugin>, String> {
    let mut scanned = scan_dir(&plugins_directory(workspace), true)?;
    scanned.extend(scan_dir(&disabled_directory(workspace), false)?);
    scanned.extend(scan_dir(&legacy_disabled_directory(workspace), false)?);
    Ok(scanned
        .into_iter()
        .filter(|item| item.metadata.as_ref().map(|metadata| metadata.id.eq_ignore_ascii_case(canonical_id)).unwrap_or(false))
        .collect())
}

fn dependency_problem(workspace: &Path, metadata: &PluginMetadata, self_id: Option<&str>) -> Result<Option<String>, String> {
    for dependency in &metadata.dependencies {
        if self_id.map(|value| dependency.eq_ignore_ascii_case(value)).unwrap_or(false) {
            continue;
        }
        let candidates = find_all_by_id(workspace, dependency)?;
        if candidates.is_empty() {
            return Ok(Some(format!("Missing required dependency: {dependency}.")));
        }
        if candidates.len() > 1 {
            return Ok(Some(format!("Required dependency {dependency} has duplicate JARs.")));
        }
        if !candidates[0].enabled {
            return Ok(Some(format!("Required dependency {dependency} is disabled.")));
        }
    }
    Ok(None)
}

fn ensure_plugin_manager_layout(workspace: &Path) -> Result<(), String> {
    let disabled = disabled_directory(workspace);
    let backups = backup_directory(workspace);
    let registry = category_registry_path(workspace);
    fs::create_dir_all(&disabled).map_err(|error| error.to_string())?;
    fs::create_dir_all(&backups).map_err(|error| error.to_string())?;
    if let Some(parent) = registry.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }

    let legacy_registry = legacy_category_registry_path(workspace);
    if !registry.exists() && legacy_registry.is_file() {
        fs::copy(&legacy_registry, &registry).map_err(|error| error.to_string())?;
    }

    let legacy_disabled = legacy_disabled_directory(workspace);
    if legacy_disabled.is_dir() {
        for entry in fs::read_dir(&legacy_disabled).map_err(|error| error.to_string())? {
            let source = entry.map_err(|error| error.to_string())?.path();
            if source.extension().and_then(|value| value.to_str()).map(|value| value.eq_ignore_ascii_case("jar")) != Some(true) {
                continue;
            }
            let Some(file_name) = source.file_name() else { continue; };
            let destination = disabled.join(file_name);
            if destination.exists() {
                continue;
            }
            if fs::rename(&source, &destination).is_err() {
                fs::copy(&source, &destination).map_err(|error| error.to_string())?;
                fs::remove_file(&source).map_err(|error| error.to_string())?;
            }
        }
    }
    Ok(())
}

fn plugins_directory(workspace: &Path) -> PathBuf {
    workspace.join("server").join("plugins")
}

fn disabled_directory(workspace: &Path) -> PathBuf {
    workspace.join("tools").join("lazybuilder").join("disabled-plugins")
}

fn legacy_disabled_directory(workspace: &Path) -> PathBuf {
    workspace.join("server").join("plugins-disabled")
}

fn backup_directory(workspace: &Path) -> PathBuf {
    workspace.join("tools").join("lazybuilder").join("plugin-backups")
}

fn category_registry_path(workspace: &Path) -> PathBuf {
    workspace.join("tools").join("lazybuilder").join("config").join("plugin-registry.json")
}

fn legacy_category_registry_path(workspace: &Path) -> PathBuf {
    workspace.join("tools").join("lazybuilder").join("plugin-registry.json")
}

fn load_category_overrides(path: &Path) -> HashMap<String, String> {
    let Ok(text) = fs::read_to_string(path) else {
        return HashMap::new();
    };
    serde_json::from_str::<HashMap<String, String>>(&text).unwrap_or_default()
}

fn resolve_category(overrides: &HashMap<String, String>, id: &str, name: &str) -> String {
    if let Some(value) = overrides.get(id) {
        return value.clone();
    }
    if let Some(value) = known_category(id) {
        return value.into();
    }
    let display_id = normalize_id(name);
    if let Some(value) = overrides.get(&display_id) {
        return value.clone();
    }
    known_category(&display_id).unwrap_or("Other").into()
}

fn known_category(id: &str) -> Option<&'static str> {
    match id {
        "world-manager" => Some("World Management"),
        "utilities-manager" => Some("Server Utilities"),
        "axiom" | "axiompaper" | "fastasyncworldedit" | "fawe" | "fastasyncvoxelsniper" | "ezedits" | "metabrushes" => Some("Build Tools"),
        _ => None,
    }
}

fn normalize_id(value: &str) -> String {
    let normalized: String = value
        .trim()
        .to_lowercase()
        .chars()
        .filter(|ch| ch.is_ascii_alphanumeric() || *ch == '-' || *ch == '_')
        .map(|ch| if ch == '_' { '-' } else { ch })
        .collect();
    if normalized.is_empty() { "plugin".into() } else { normalized }
}

fn problem_id_from_path(path: &Path) -> String {
    let display = path.file_stem().and_then(|value| value.to_str()).unwrap_or("plugin");
    format!("invalid-{}", normalize_id(display))
}

fn safe_jar_name(name: &str, version: &str) -> String {
    fn safe(value: &str) -> String {
        value
            .chars()
            .map(|ch| if ch.is_ascii_alphanumeric() || matches!(ch, '-' | '_' | '.') { ch } else { '-' })
            .collect()
    }
    format!("{}-{}.jar", safe(name), safe(version))
}

fn stage_copy(source: &Path, staged: &Path) -> Result<(), String> {
    if !source.is_file() {
        return Err(format!("Source file was not found: {}", source.display()));
    }
    if let Some(parent) = staged.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    if staged.exists() {
        fs::remove_file(staged).map_err(|error| error.to_string())?;
    }

    let mut input = File::open(source).map_err(|error| error.to_string())?;
    let mut output = File::create(staged).map_err(|error| error.to_string())?;
    std::io::copy(&mut input, &mut output).map_err(|error| error.to_string())?;
    output.flush().map_err(|error| error.to_string())?;
    drop(output);
    if !files_equal(source, staged)? {
        let _ = fs::remove_file(staged);
        return Err("Staged plugin JAR verification failed.".into());
    }
    Ok(())
}

fn copy_atomic(source: &Path, destination: &Path) -> Result<(), String> {
    let temporary = destination.with_extension("jar.incoming");
    stage_copy(source, &temporary)?;
    replace_file(&temporary, destination)
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    if destination.exists() {
        let previous = destination.with_extension("swap.previous");
        let _ = fs::remove_file(&previous);
        fs::rename(destination, &previous).map_err(|error| error.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => {
                let _ = fs::remove_file(previous);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&previous, destination);
                Err(error.to_string())
            }
        }
    } else {
        fs::rename(source, destination).map_err(|error| error.to_string())
    }
}

fn restore_from_backup(backup: &Path, destination: &Path) -> Result<(), String> {
    if !backup.is_file() {
        return Err(format!("Plugin rollback backup is missing: {}", backup.display()));
    }
    if destination.exists() {
        fs::remove_file(destination).map_err(|error| error.to_string())?;
    }
    fs::copy(backup, destination).map_err(|error| error.to_string())?;
    Ok(())
}

fn files_equal(left: &Path, right: &Path) -> Result<bool, String> {
    let left_meta = fs::metadata(left).map_err(|error| error.to_string())?;
    let right_meta = fs::metadata(right).map_err(|error| error.to_string())?;
    if left_meta.len() != right_meta.len() {
        return Ok(false);
    }
    let mut left_file = File::open(left).map_err(|error| error.to_string())?;
    let mut right_file = File::open(right).map_err(|error| error.to_string())?;
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

fn canonical_path(path: &Path) -> Result<PathBuf, String> {
    path.canonicalize().map_err(|error| error.to_string())
}

fn canonical_candidate(path: &Path) -> Result<PathBuf, String> {
    if path.exists() {
        return canonical_path(path);
    }
    let parent = path.parent().ok_or_else(|| "Path has no parent.".to_string())?;
    let canonical_parent = canonical_path(parent)?;
    let name = path.file_name().ok_or_else(|| "Path has no filename.".to_string())?;
    Ok(canonical_parent.join(name))
}

fn timestamp_suffix() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or(0)
}

fn success_result(plugin_id: String, message: impl Into<String>) -> PluginInstallResult {
    PluginInstallResult { success: true, plugin_id, message: Some(message.into()), restart_required: true }
}

fn failed_result(plugin_id: String, message: impl Into<String>) -> PluginInstallResult {
    PluginInstallResult { success: false, plugin_id, message: Some(message.into()), restart_required: false }
}
