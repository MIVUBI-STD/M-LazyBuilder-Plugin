use crate::engine::{paths, persistence};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashSet};
use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::Mutex;
use zip::ZipArchive;

const MAX_PLUGIN_METADATA_BYTES: u64 = 256 * 1024;

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

#[derive(Clone, Debug)]
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
        let _guard = self.lock()?;
        let workspace = workspace()?;
        list_plugins_inner(&workspace)
    }

    pub fn install(&self, jar_path: &str) -> Result<PluginInstallResult, String> {
        let _guard = self.lock()?;
        let workspace = workspace()?;
        let source = PathBuf::from(jar_path);
        let incoming = match validated_metadata(&source) {
            Ok(value) => value,
            Err(error) => return Ok(failed_result(problem_id_from_path(&source), error)),
        };
        ensure_third_party_id(&incoming.id)?;

        if let Some(current) = find_all_by_id(&workspace, &incoming.id)?.first() {
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

        let destination = plugins_directory(&workspace).join(safe_jar_name(&incoming.name, &incoming.version));
        if destination.exists() {
            return Ok(failed_result(incoming.id, "Target plugin filename already exists."));
        }
        copy_atomic(&source, &destination)?;
        Ok(success_result(incoming.id, "Plugin installed. Restart required."))
    }

    pub fn update(&self, plugin_id: &str, jar_path: &str) -> Result<PluginInstallResult, String> {
        let _guard = self.lock()?;
        let workspace = workspace()?;
        let canonical_id = normalize_id(plugin_id);
        ensure_third_party_id(&canonical_id)?;

        let source = PathBuf::from(jar_path);
        let incoming = match validated_metadata(&source) {
            Ok(value) => value,
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
            return Ok(failed_result(canonical_id, "Duplicate plugin versions must be resolved before updating."));
        }
        if let Some(problem) = dependency_problem(&workspace, &incoming, Some(&canonical_id))? {
            return Ok(failed_result(canonical_id, problem));
        }

        let current = &existing[0];
        let current_version = current.metadata.as_ref()
            .ok_or_else(|| format!(
                "Installed plugin metadata became unavailable during update: {}",
                current.path.display()
            ))?
            .version
            .clone();
        let target_dir = if current.enabled { plugins_directory(&workspace) } else { disabled_directory(&workspace) };
        fs::create_dir_all(&target_dir).map_err(|error| error.to_string())?;
        let destination = target_dir.join(safe_jar_name(&incoming.name, &incoming.version));
        let same_destination = canonical_candidate(&destination)? == canonical_path(&current.path)?;
        if !same_destination && destination.exists() {
            return Ok(failed_result(canonical_id, "Target plugin filename already exists."));
        }

        let staged = destination.with_extension("jar.incoming");
        stage_copy(&source, &staged)?;

        let backup = rollback_backup_path(&workspace, &canonical_id);
        if let Err(error) = publish_backup(&current.path, &backup) {
            let _ = fs::remove_file(&staged);
            return Err(error);
        }

        let update_result = if same_destination {
            replace_file(&staged, &destination)
        } else {
            publish_new_then_retire_old(&staged, &destination, &current.path)
        };
        if let Err(error) = update_result {
            let _ = fs::remove_file(&staged);
            if !same_destination {
                let _ = fs::remove_file(&destination);
            }
            let rollback = restore_from_backup(&backup, &current.path);
            return match rollback {
                Ok(()) => Err(format!("Plugin update failed and previous JAR was restored: {error}")),
                Err(rollback_error) => Err(format!(
                    "Plugin update failed: {error}; rollback also failed: {rollback_error}"
                )),
            };
        }

        Ok(success_result(
            canonical_id,
            format!("Updated {current_version} → {}. Restart required.", incoming.version),
        ))
    }

    pub fn set_enabled(&self, plugin_id: &str, enabled: bool) -> Result<(), String> {
        let _guard = self.lock()?;
        let workspace = workspace()?;
        let canonical_id = normalize_id(plugin_id);
        ensure_third_party_id(&canonical_id)?;

        let existing = require_single_plugin(&workspace, &canonical_id, "Duplicate plugin versions must be resolved first.")?;
        if existing.enabled == enabled {
            return Ok(());
        }

        if enabled {
            let metadata = existing.metadata.as_ref()
                .ok_or_else(|| format!(
                    "Installed plugin metadata became unavailable before enable: {}",
                    existing.path.display()
                ))?;
            if let Some(problem) = dependency_problem(&workspace, metadata, Some(&canonical_id))? {
                return Err(problem);
            }
        } else {
            ensure_no_enabled_dependents(&workspace, &canonical_id)?;
        }

        let target_dir = if enabled { plugins_directory(&workspace) } else { disabled_directory(&workspace) };
        fs::create_dir_all(&target_dir).map_err(|error| error.to_string())?;
        let file_name = existing.path.file_name().ok_or_else(|| "Plugin filename is invalid.".to_string())?;
        let destination = target_dir.join(file_name);
        if destination.exists() {
            return Err("Target plugin file already exists.".into());
        }
        move_file_verified(&existing.path, &destination)
    }

    pub fn remove(&self, plugin_id: &str) -> Result<(), String> {
        let _guard = self.lock()?;
        let workspace = workspace()?;
        let canonical_id = normalize_id(plugin_id);
        ensure_third_party_id(&canonical_id)?;

        let existing = find_all_by_id(&workspace, &canonical_id)?;
        if existing.is_empty() {
            return Ok(());
        }
        if existing.len() > 1 {
            return Err("Duplicate plugin versions must be resolved before removal.".into());
        }
        ensure_no_enabled_dependents(&workspace, &canonical_id)?;

        let current = &existing[0];
        let backup = rollback_backup_path(&workspace, &canonical_id);
        publish_backup(&current.path, &backup)?;
        if let Err(error) = fs::remove_file(&current.path) {
            let rollback = restore_from_backup(&backup, &current.path);
            return match rollback {
                Ok(()) => Err(format!("Could not remove plugin JAR; previous JAR was restored: {error}")),
                Err(rollback_error) => Err(format!(
                    "Could not remove plugin JAR: {error}; rollback also failed: {rollback_error}"
                )),
            };
        }
        Ok(())
    }

    pub fn remove_problem(&self, problem_id: &str, jar_file_name: &str) -> Result<(), String> {
        let _guard = self.lock()?;
        let workspace = workspace()?;
        let requested = validate_jar_file_name(jar_file_name)?;
        let expected_problem_id = problem_id_from_path(Path::new(&requested));
        if normalize_id(problem_id) != normalize_id(&expected_problem_id) {
            return Err("Problem plugin identity does not match the selected JAR.".into());
        }
        if looks_like_core_jar(&requested) {
            return Err("LazyBuilder core modules are repaired automatically and cannot be removed through Plugin Manager.".into());
        }

        let candidates = [
            plugins_directory(&workspace).join(&requested),
            disabled_directory(&workspace).join(&requested),
            legacy_disabled_directory(&workspace).join(&requested),
        ];
        let existing = candidates.into_iter().filter(|path| path.is_file()).collect::<Vec<_>>();
        if existing.is_empty() {
            return Ok(());
        }
        if existing.len() > 1 {
            return Err("Multiple problem JARs share this filename. Resolve the duplicate files before cleanup.".into());
        }

        let source = &existing[0];
        persistence::safe_path::ensure_regular_file(source, "problem plugin JAR")?;
        if read_metadata(source).is_ok() {
            return Err("Selected JAR now contains valid plugin metadata and is no longer eligible for problem cleanup.".into());
        }

        let rollback = backup_directory(&workspace).join(format!(".{}.problem.rollback.jar", normalize_id(problem_id)));
        publish_backup(source, &rollback)?;
        if let Err(error) = fs::remove_file(source) {
            let restore = restore_from_backup(&rollback, source);
            let _ = fs::remove_file(&rollback);
            return match restore {
                Ok(()) => Err(format!("Could not remove invalid plugin JAR; previous file was restored: {error}")),
                Err(rollback_error) => Err(format!(
                    "Could not remove invalid plugin JAR: {error}; rollback also failed: {rollback_error}"
                )),
            };
        }
        let _ = fs::remove_file(rollback);
        Ok(())
    }

    pub fn resolve_duplicates(
        &self,
        plugin_id: &str,
        keep_jar_file_name: &str,
    ) -> Result<PluginInstallResult, String> {
        let _guard = self.lock()?;
        let workspace = workspace()?;
        let canonical_id = normalize_id(plugin_id);
        ensure_third_party_id(&canonical_id)?;

        let requested = validate_jar_file_name(keep_jar_file_name)?;
        let existing = find_all_by_id(&workspace, &canonical_id)?;
        if existing.len() < 2 {
            return Ok(failed_result(canonical_id, "No duplicate plugin JARs were found."));
        }
        let Some(keep_index) = existing.iter().position(|item| {
            item.path.file_name().and_then(|value| value.to_str())
                .map(|value| value.eq_ignore_ascii_case(&requested)).unwrap_or(false)
        }) else {
            return Ok(failed_result(canonical_id, "Selected JAR is not one of the duplicate candidates."));
        };

        let backup_dir = backup_directory(&workspace);
        fs::create_dir_all(&backup_dir).map_err(|error| error.to_string())?;
        let mut removals = Vec::new();
        for (index, item) in existing.iter().enumerate() {
            if index == keep_index { continue; }
            let backup = backup_dir.join(format!(".{canonical_id}.duplicate-{index}.rollback.jar"));
            publish_backup(&item.path, &backup)?;
            removals.push((item.path.clone(), backup));
        }

        let mut removed: Vec<(PathBuf, PathBuf)> = Vec::new();
        for (source, backup) in &removals {
            if let Err(error) = fs::remove_file(source) {
                let mut rollback_errors = Vec::new();
                for (restore_path, restore_backup) in removed.into_iter().rev() {
                    if let Err(restore_error) = restore_from_backup(&restore_backup, &restore_path) {
                        rollback_errors.push(restore_error);
                    }
                }
                cleanup_operation_backups(&removals);
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
        cleanup_operation_backups(&removals);

        Ok(success_result(canonical_id, format!("Duplicate JARs resolved. Keeping {requested}. Restart required.")))
    }

    fn lock(&self) -> Result<std::sync::MutexGuard<'_, ()>, String> {
        self.mutation_gate.lock().map_err(|_| "plugin manager lock poisoned".to_string())
    }
}

fn workspace() -> Result<PathBuf, String> {
    let workspace = paths::workspace_root()?;
    ensure_plugin_manager_layout(&workspace)?;
    Ok(workspace)
}

fn list_plugins_inner(workspace: &Path) -> Result<Vec<PluginSummary>, String> {
    let scanned = scan_all(workspace)?;
    let valid_ids: HashSet<String> = scanned.iter()
        .filter_map(|item| item.metadata.as_ref().map(|meta| meta.id.clone()))
        .collect();
    let enabled_ids: HashSet<String> = scanned.iter()
        .filter(|item| item.enabled)
        .filter_map(|item| item.metadata.as_ref().map(|meta| meta.id.clone()))
        .collect();

    let mut groups: BTreeMap<String, Vec<&ScannedPlugin>> = BTreeMap::new();
    for item in &scanned {
        let Some(metadata) = item.metadata.as_ref() else {
            continue;
        };
        groups.entry(metadata.id.clone()).or_default().push(item);
    }

    let mut result = Vec::new();
    for (id, items) in groups {
        let Some(primary) = items.iter().find(|item| item.enabled).copied().or_else(|| items.first().copied()) else {
            continue;
        };
        let Some(metadata) = primary.metadata.as_ref() else {
            continue;
        };
        let mut state = if primary.enabled { "Enabled" } else { "Disabled" }.to_string();
        let mut problem = None;
        let mut candidate_files = None;

        if items.len() > 1 {
            state = "Problem".into();
            let files = items.iter().map(|item| file_name(&item.path)).collect::<Vec<_>>();
            problem = Some(format!("Duplicate plugin JARs detected: {}", files.join(", ")));
            candidate_files = Some(files);
        } else if primary.enabled {
            let missing = metadata.dependencies.iter()
                .filter(|dep| !valid_ids.contains(*dep)).cloned().collect::<Vec<_>>();
            let disabled = metadata.dependencies.iter()
                .filter(|dep| valid_ids.contains(*dep) && !enabled_ids.contains(*dep))
                .cloned().collect::<Vec<_>>();
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
            category: known_category(&id)
                .or_else(|| known_category(&normalize_id(&metadata.name))).unwrap_or("Other").into(),
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
            candidate_files: Some(vec![file_name(&item.path)]),
        });
    }

    result.sort_by(|left, right| {
        left.category.to_lowercase().cmp(&right.category.to_lowercase())
            .then(left.display_name.to_lowercase().cmp(&right.display_name.to_lowercase()))
    });
    Ok(result)
}

fn scan_all(workspace: &Path) -> Result<Vec<ScannedPlugin>, String> {
    let mut scanned = scan_dir(&plugins_directory(workspace), true)?;
    scanned.extend(scan_dir(&disabled_directory(workspace), false)?);
    scanned.extend(scan_dir(&legacy_disabled_directory(workspace), false)?);
    Ok(scanned)
}

fn scan_dir(directory: &Path, enabled: bool) -> Result<Vec<ScannedPlugin>, String> {
    if !directory.is_dir() { return Ok(Vec::new()); }
    let mut result = Vec::new();
    for entry in fs::read_dir(directory).map_err(|error| error.to_string())? {
        let path = entry.map_err(|error| error.to_string())?.path();
        if !is_jar(&path) { continue; }
        match read_metadata(&path) {
            Ok(metadata) => result.push(ScannedPlugin { path, metadata: Some(metadata), enabled, error: None }),
            Err(error) => result.push(ScannedPlugin { path, metadata: None, enabled, error: Some(error) }),
        }
    }
    Ok(result)
}

fn validated_metadata(path: &Path) -> Result<PluginMetadata, String> {
    let metadata = read_metadata(path)?;
    validate_target_compatibility(&metadata)?;
    Ok(metadata)
}

fn read_metadata(path: &Path) -> Result<PluginMetadata, String> {
    if !is_jar(path) { return Err("Selected file is not a JAR.".into()); }
    persistence::safe_path::ensure_regular_file(path, "plugin JAR")
        .map_err(|error| format!("Unsafe plugin JAR {}: {error}", path.display()))?;

    let file = File::open(path).map_err(|error| error.to_string())?;
    let mut archive = ZipArchive::new(file).map_err(|error| format!("Invalid plugin JAR: {error}"))?;
    let metadata_entry_name = if archive.file_names().any(|name| name == "plugin.yml") {
        "plugin.yml"
    } else if archive.file_names().any(|name| name == "paper-plugin.yml") {
        "paper-plugin.yml"
    } else {
        return Err("JAR does not contain plugin.yml or paper-plugin.yml".into());
    };
    let entry = archive.by_name(metadata_entry_name)
        .map_err(|error| format!("Failed to read plugin metadata: {error}"))?;
    if entry.size() > MAX_PLUGIN_METADATA_BYTES {
        return Err(format!(
            "Plugin metadata is unexpectedly large ({} bytes; limit {} bytes).",
            entry.size(),
            MAX_PLUGIN_METADATA_BYTES
        ));
    }
    let mut text = String::new();
    let mut bounded = entry.take(MAX_PLUGIN_METADATA_BYTES + 1);
    bounded.read_to_string(&mut text).map_err(|error| error.to_string())?;
    if text.len() as u64 > MAX_PLUGIN_METADATA_BYTES {
        return Err("Plugin metadata exceeded the inspection limit.".into());
    }
    let parsed: PluginYaml = serde_yaml::from_str(&text)
        .map_err(|error| format!("Invalid plugin metadata: {error}"))?;

    let version = yaml_scalar_to_string(&parsed.version);
    if version.trim().is_empty() {
        return Err("Plugin metadata version is blank.".into());
    }
    let api_version = parsed.api_version.as_ref().map(yaml_scalar_to_string).unwrap_or_default();
    let id = normalize_id(&parsed.name);
    let mut dependencies = parsed.depend.into_iter().map(|value| normalize_id(&value)).collect::<Vec<_>>();
    dependencies.extend(parse_paper_dependencies(parsed.dependencies.as_ref()));
    dependencies.retain(|value| !value.is_empty());
    dependencies.sort();
    dependencies.dedup();

    Ok(PluginMetadata { id, name: parsed.name, version, api_version, dependencies })
}

fn parse_paper_dependencies(value: Option<&serde_yaml::Value>) -> Vec<String> {
    match value {
        Some(serde_yaml::Value::Sequence(values)) => values.iter()
            .filter_map(|value| value.as_str()).map(normalize_id).collect(),
        Some(serde_yaml::Value::Mapping(values)) => {
            let has_scopes = values.keys().any(|key| {
                key.as_str().map(|name| matches!(name.to_ascii_lowercase().as_str(), "bootstrap" | "server")) == Some(true)
            });
            if has_scopes {
                let mut dependencies = Vec::new();
                for (scope, entries) in values {
                    let Some(scope_name) = scope.as_str() else { continue; };
                    if matches!(scope_name.to_ascii_lowercase().as_str(), "bootstrap" | "server") {
                        collect_dependency_mapping(entries, &mut dependencies);
                    } else if paper_dependency_required(entries) {
                        dependencies.push(normalize_id(scope_name));
                    }
                }
                dependencies
            } else {
                let mut dependencies = Vec::new();
                collect_dependency_mapping(value.expect("mapping value"), &mut dependencies);
                dependencies
            }
        }
        _ => Vec::new(),
    }
}

fn collect_dependency_mapping(value: &serde_yaml::Value, dependencies: &mut Vec<String>) {
    let serde_yaml::Value::Mapping(values) = value else { return; };
    for (key, config) in values {
        let Some(name) = key.as_str() else { continue; };
        if paper_dependency_required(config) {
            dependencies.push(normalize_id(name));
        }
    }
}

fn paper_dependency_required(value: &serde_yaml::Value) -> bool {
    let serde_yaml::Value::Mapping(map) = value else { return true; };
    for (key, value) in map {
        if key.as_str().map(|value| value.eq_ignore_ascii_case("required")) == Some(true) {
            return value.as_bool().unwrap_or(true);
        }
    }
    true
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
    if metadata.api_version.trim().is_empty() { return Ok(()); }
    let mut parts = metadata.api_version.split('.');
    let major = parts.next().and_then(|value| value.parse::<u32>().ok());
    let minor = parts.next().and_then(|value| value.parse::<u32>().ok()).unwrap_or(0);
    if let Some(major) = major {
        if major > 1 || (major == 1 && minor > 21) {
            return Err(format!(
                "Plugin targets API {}, newer than LazyBuilder's Paper 1.21.4 baseline.", metadata.api_version
            ));
        }
    }
    Ok(())
}

fn find_all_by_id(workspace: &Path, canonical_id: &str) -> Result<Vec<ScannedPlugin>, String> {
    Ok(scan_all(workspace)?.into_iter().filter(|item| {
        item.metadata.as_ref().map(|metadata| metadata.id.eq_ignore_ascii_case(canonical_id)).unwrap_or(false)
    }).collect())
}

fn require_single_plugin(workspace: &Path, canonical_id: &str, duplicate_message: &str) -> Result<ScannedPlugin, String> {
    let mut existing = find_all_by_id(workspace, canonical_id)?;
    if existing.is_empty() { return Err("Plugin is not installed.".into()); }
    if existing.len() > 1 { return Err(duplicate_message.into()); }
    Ok(existing.remove(0))
}

fn dependency_problem(
    workspace: &Path,
    metadata: &PluginMetadata,
    self_id: Option<&str>,
) -> Result<Option<String>, String> {
    for dependency in &metadata.dependencies {
        if self_id.map(|value| dependency.eq_ignore_ascii_case(value)).unwrap_or(false) { continue; }
        let candidates = find_all_by_id(workspace, dependency)?;
        if candidates.is_empty() { return Ok(Some(format!("Missing required dependency: {dependency}."))); }
        if candidates.len() > 1 { return Ok(Some(format!("Required dependency {dependency} has duplicate JARs."))); }
        if !candidates[0].enabled { return Ok(Some(format!("Required dependency {dependency} is disabled."))); }
    }
    Ok(None)
}

fn ensure_no_enabled_dependents(workspace: &Path, canonical_id: &str) -> Result<(), String> {
    let mut dependents = scan_all(workspace)?.into_iter()
        .filter(|item| item.enabled)
        .filter_map(|item| item.metadata)
        .filter(|metadata| metadata.id != canonical_id)
        .filter(|metadata| metadata.dependencies.iter().any(|dependency| dependency.eq_ignore_ascii_case(canonical_id)))
        .map(|metadata| metadata.name)
        .collect::<Vec<_>>();
    dependents.sort_by_key(|value| value.to_lowercase());
    dependents.dedup_by(|left, right| left.eq_ignore_ascii_case(right));
    if dependents.is_empty() { return Ok(()); }
    Err(format!(
        "Cannot disable or remove this plugin while required by enabled plugin{}: {}.",
        if dependents.len() == 1 { "" } else { "s" },
        dependents.join(", ")
    ))
}

fn ensure_third_party_id(id: &str) -> Result<(), String> {
    if matches!(normalize_id(id).as_str(), "world-manager" | "utilities-manager") {
        return Err("LazyBuilder core modules are maintained automatically and cannot be changed through Plugin Manager.".into());
    }
    Ok(())
}

fn ensure_plugin_manager_layout(workspace: &Path) -> Result<(), String> {
    let disabled = disabled_directory(workspace);
    let backups = backup_directory(workspace);
    paths::ensure_owned_directory(workspace, &disabled, "disabled plugin directory")?;
    paths::ensure_owned_directory(workspace, &backups, "plugin rollback directory")?;
    paths::ensure_existing_owned_directory(
        workspace,
        &plugins_directory(workspace),
        "Paper plugin directory",
    )?;

    let legacy_disabled = legacy_disabled_directory(workspace);
    if legacy_disabled.is_dir() {
        for entry in fs::read_dir(&legacy_disabled).map_err(|error| error.to_string())? {
            let source = entry.map_err(|error| error.to_string())?.path();
            if !is_jar(&source) { continue; }
            let Some(file_name) = source.file_name() else { continue; };
            let destination = disabled.join(file_name);
            if destination.exists() { continue; }
            move_file_verified(&source, &destination)?;
        }
    }
    Ok(())
}

fn plugins_directory(workspace: &Path) -> PathBuf { workspace.join("server").join("plugins") }
fn disabled_directory(workspace: &Path) -> PathBuf { workspace.join("tools").join("lazybuilder").join("disabled-plugins") }
fn legacy_disabled_directory(workspace: &Path) -> PathBuf { workspace.join("server").join("plugins-disabled") }
fn backup_directory(workspace: &Path) -> PathBuf { workspace.join("tools").join("lazybuilder").join("plugin-backups") }
fn rollback_backup_path(workspace: &Path, plugin_id: &str) -> PathBuf {
    backup_directory(workspace).join(format!("{}.previous.jar", normalize_id(plugin_id)))
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
    let normalized: String = value.trim().to_lowercase().chars()
        .filter(|ch| ch.is_ascii_alphanumeric() || *ch == '-' || *ch == '_')
        .map(|ch| if ch == '_' { '-' } else { ch }).collect();
    if normalized.is_empty() { "plugin".into() } else { normalized }
}

fn problem_id_from_path(path: &Path) -> String {
    let display = path.file_stem().and_then(|value| value.to_str()).unwrap_or("plugin");
    format!("invalid-{}", normalize_id(display))
}

fn safe_jar_name(name: &str, version: &str) -> String {
    fn safe(value: &str) -> String {
        value.chars().map(|ch| if ch.is_ascii_alphanumeric() || matches!(ch, '-' | '_' | '.') { ch } else { '-' }).collect()
    }
    format!("{}-{}.jar", safe(name), safe(version))
}

fn validate_jar_file_name(value: &str) -> Result<String, String> {
    let path = Path::new(value);
    let name = path.file_name().and_then(|value| value.to_str())
        .ok_or_else(|| "Invalid plugin filename.".to_string())?;
    if name != value || !name.to_ascii_lowercase().ends_with(".jar") {
        return Err("Plugin operation accepts one JAR filename only.".into());
    }
    Ok(name.to_string())
}

fn looks_like_core_jar(file_name: &str) -> bool {
    let value = file_name.to_ascii_lowercase();
    value.starts_with("world-manager-") || value.starts_with("utilities-manager-")
}

fn file_name(path: &Path) -> String {
    path.file_name().and_then(|value| value.to_str()).unwrap_or("unknown").to_string()
}

fn is_jar(path: &Path) -> bool {
    path.extension().and_then(|value| value.to_str()).map(|value| value.eq_ignore_ascii_case("jar")) == Some(true)
}

fn stage_copy(source: &Path, staged: &Path) -> Result<(), String> {
    if !source.is_file() { return Err(format!("Source file was not found: {}", source.display())); }
    if let Some(parent) = staged.parent() { fs::create_dir_all(parent).map_err(|error| error.to_string())?; }
    if staged.exists() { fs::remove_file(staged).map_err(|error| error.to_string())?; }

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

fn publish_backup(source: &Path, destination: &Path) -> Result<(), String> {
    if let Some(parent) = destination.parent() { fs::create_dir_all(parent).map_err(|error| error.to_string())?; }
    let staged = destination.with_extension("jar.incoming");
    stage_copy(source, &staged)?;
    replace_file(&staged, destination)
}

fn move_file_verified(source: &Path, destination: &Path) -> Result<(), String> {
    match fs::rename(source, destination) {
        Ok(()) => Ok(()),
        Err(rename_error) => {
            stage_copy(source, destination)?;
            if let Err(remove_error) = fs::remove_file(source) {
                let _ = fs::remove_file(destination);
                return Err(format!(
                    "Could not move plugin JAR: {rename_error}; copy fallback could not retire source: {remove_error}"
                ));
            }
            Ok(())
        }
    }
}

fn publish_new_then_retire_old(staged: &Path, destination: &Path, current: &Path) -> Result<(), String> {
    fs::rename(staged, destination).map_err(|error| format!("Could not publish the new plugin JAR: {error}"))?;
    if let Err(error) = fs::remove_file(current) {
        let _ = fs::remove_file(destination);
        return Err(format!("Could not retire the previous plugin JAR: {error}"));
    }
    Ok(())
}

fn replace_file(source: &Path, destination: &Path) -> Result<(), String> {
    if destination.exists() {
        let previous = destination.with_extension("swap.previous");
        let _ = fs::remove_file(&previous);
        fs::rename(destination, &previous).map_err(|error| error.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => { let _ = fs::remove_file(previous); Ok(()) }
            Err(error) => { let _ = fs::rename(&previous, destination); Err(error.to_string()) }
        }
    } else {
        fs::rename(source, destination).map_err(|error| error.to_string())
    }
}

fn restore_from_backup(backup: &Path, destination: &Path) -> Result<(), String> {
    if !backup.is_file() { return Err(format!("Plugin rollback backup is missing: {}", backup.display())); }
    if destination.exists() { fs::remove_file(destination).map_err(|error| error.to_string())?; }
    fs::copy(backup, destination).map_err(|error| error.to_string())?;
    if !files_equal(backup, destination)? {
        return Err("Plugin rollback verification failed.".into());
    }
    Ok(())
}

fn cleanup_operation_backups(removals: &[(PathBuf, PathBuf)]) {
    for (_, backup) in removals { let _ = fs::remove_file(backup); }
}

fn files_equal(left: &Path, right: &Path) -> Result<bool, String> {
    let left_meta = fs::metadata(left).map_err(|error| error.to_string())?;
    let right_meta = fs::metadata(right).map_err(|error| error.to_string())?;
    if left_meta.len() != right_meta.len() { return Ok(false); }
    let mut left_file = File::open(left).map_err(|error| error.to_string())?;
    let mut right_file = File::open(right).map_err(|error| error.to_string())?;
    let mut left_buffer = [0u8; 64 * 1024];
    let mut right_buffer = [0u8; 64 * 1024];
    loop {
        let left_count = left_file.read(&mut left_buffer).map_err(|error| error.to_string())?;
        let right_count = right_file.read(&mut right_buffer).map_err(|error| error.to_string())?;
        if left_count != right_count { return Ok(false); }
        if left_count == 0 { return Ok(true); }
        if left_buffer[..left_count] != right_buffer[..right_count] { return Ok(false); }
    }
}

fn canonical_path(path: &Path) -> Result<PathBuf, String> { path.canonicalize().map_err(|error| error.to_string()) }
fn canonical_candidate(path: &Path) -> Result<PathBuf, String> {
    if path.exists() { return canonical_path(path); }
    let parent = path.parent().ok_or_else(|| "Path has no parent.".to_string())?;
    let canonical_parent = canonical_path(parent)?;
    let name = path.file_name().ok_or_else(|| "Path has no filename.".to_string())?;
    Ok(canonical_parent.join(name))
}

fn success_result(plugin_id: String, message: impl Into<String>) -> PluginInstallResult {
    PluginInstallResult { success: true, plugin_id, message: Some(message.into()), restart_required: true }
}
fn failed_result(plugin_id: String, message: impl Into<String>) -> PluginInstallResult {
    PluginInstallResult { success: false, plugin_id, message: Some(message.into()), restart_required: false }
}

#[cfg(test)]
mod tests {
    use super::{parse_paper_dependencies, read_metadata, MAX_PLUGIN_METADATA_BYTES};
    use std::fs;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT_TEST: AtomicU64 = AtomicU64::new(1);
    use serde_yaml::Value;

    #[test]
    fn plugin_metadata_limit_is_bounded() {
        assert!(MAX_PLUGIN_METADATA_BYTES >= 64 * 1024);
        assert!(MAX_PLUGIN_METADATA_BYTES <= 1024 * 1024);
    }

    #[test]
    fn non_regular_plugin_path_is_rejected_before_zip_parsing() {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "lazybuilder-plugin-path-test-{}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).unwrap();
        let fake_jar_directory = directory.join("fake.jar");
        fs::create_dir_all(&fake_jar_directory).unwrap();

        let error = read_metadata(&fake_jar_directory).unwrap_err();
        assert!(error.contains("Unsafe plugin JAR"));

        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn parses_scoped_paper_dependencies_without_treating_scope_names_as_plugins() {
        let value: Value = serde_yaml::from_str(
            "bootstrap:\n  BootstrapDep:\n    required: true\nserver:\n  RuntimeDep:\n    required: true\n  OptionalDep:\n    required: false\n",
        ).expect("valid yaml");
        let dependencies = parse_paper_dependencies(Some(&value));
        assert_eq!(dependencies, vec!["bootstrapdep", "runtimedep"]);
    }
}
