use crate::engine::{paths, persistence};
use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, HashMap, HashSet};
use std::fs::{self, File, OpenOptions};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::{Mutex, OnceLock};
use std::time::SystemTime;
use zip::ZipArchive;

const MAX_PLUGIN_METADATA_BYTES: u64 = 256 * 1024;
const MAX_PLUGIN_METADATA_CACHE_ENTRIES: usize = 256;
static PLUGIN_METADATA_CACHE: OnceLock<Mutex<HashMap<PathBuf, CachedPluginMetadata>>> = OnceLock::new();

#[derive(Clone)]
struct CachedPluginMetadata {
    size: u64,
    modified: Option<SystemTime>,
    result: Result<PluginMetadata, String>,
}

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

#[derive(Clone, Debug, PartialEq, Eq)]
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
        if let Some(problem) = dependency_problem(&workspace, &incoming)? {
            return Ok(failed_result(incoming.id, problem));
        }

        let destination = plugins_directory(&workspace).join(safe_jar_name(&incoming.name, &incoming.version));
        if destination.exists() {
            return Ok(failed_result(incoming.id, "Target plugin filename already exists."));
        }
        copy_atomic(&source, &destination, &incoming)?;
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
        if let Some(problem) = dependency_problem(&workspace, &incoming)? {
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
        stage_validated_plugin(&source, &staged, &incoming)?;

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
            if let Some(problem) = dependency_problem(&workspace, metadata)? {
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
        publish_recovery_backup(source, &rollback)?;
        if let Err(error) = fs::remove_file(source) {
            let restore = restore_from_backup(&rollback, source);
            return match restore {
                Ok(()) => {
                    let _ = fs::remove_file(&rollback);
                    Err(format!("Could not remove invalid plugin JAR; previous file was restored: {error}"))
                }
                Err(rollback_error) => Err(format!(
                    "Could not remove invalid plugin JAR: {error}; rollback also failed: {rollback_error}. Recovery JAR retained at {}.",
                    rollback.display()
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
        let keep_index = match unique_duplicate_selection(&existing, &requested) {
            Ok(index) => index,
            Err(reason) => return Ok(failed_result(canonical_id, reason)),
        };

        let backup_dir = backup_directory(&workspace);
        fs::create_dir_all(&backup_dir).map_err(|error| error.to_string())?;
        let mut removals = Vec::new();
        for (index, item) in existing.iter().enumerate() {
            if index == keep_index { continue; }
            let backup = backup_dir.join(format!(".{canonical_id}.duplicate-{index}.rollback.jar"));
            if backup.exists() {
                return Err(format!(
                    "Previous plugin recovery backup still exists at {}. Resolve that recovery before retrying.",
                    backup.display()
                ));
            }
            removals.push((item.path.clone(), backup));
        }

        // Prepare the entire recovery set before removing any installed JAR.
        // On preparation failure nothing has been retired, so discard only
        // backups created by this attempt instead of leaving a false recovery.
        let mut prepared = Vec::new();
        for (source, backup) in &removals {
            if let Err(error) = publish_recovery_backup(source, backup) {
                cleanup_operation_backups(&prepared);
                return Err(error);
            }
            prepared.push((source.clone(), backup.clone()));
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
                if rollback_errors.is_empty() {
                    cleanup_operation_backups(&removals);
                    return Err(format!("Duplicate resolution failed and previous files were restored: {error}"));
                }
                // A failed restore may have left a previously removed JAR absent.
                // Its prepared backup is the recovery artifact: never delete it.
                return Err(format!(
                    "Duplicate resolution failed: {error}; rollback also reported: {}. Recovery JAR backups have been retained in {}.",
                    rollback_errors.join("; "),
                    backup_dir.display()
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
            if let Some(reason) = dependency_problem_from_scan(&scanned, metadata) {
                state = "Problem".into();
                problem = Some(reason);
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

    let fingerprint = fs::metadata(path).map_err(|error| error.to_string())?;
    let size = fingerprint.len();
    let modified = fingerprint.modified().ok();
    if let Ok(cache) = PLUGIN_METADATA_CACHE.get_or_init(|| Mutex::new(HashMap::new())).lock() {
        if let Some(cached) = cache.get(path) {
            if cached.size == size && cached.modified == modified {
                return cached.result.clone();
            }
        }
    }

    let result = read_metadata_uncached(path);
    if let Ok(mut cache) = PLUGIN_METADATA_CACHE.get_or_init(|| Mutex::new(HashMap::new())).lock() {
        if cache.len() >= MAX_PLUGIN_METADATA_CACHE_ENTRIES && !cache.contains_key(path) {
            cache.clear();
        }
        cache.insert(path.to_path_buf(), CachedPluginMetadata { size, modified, result: result.clone() });
    }
    result
}

fn read_metadata_uncached(path: &Path) -> Result<PluginMetadata, String> {
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

fn dependency_problem(workspace: &Path, metadata: &PluginMetadata) -> Result<Option<String>, String> {
    let scanned = scan_all(workspace)?;
    Ok(dependency_problem_from_scan(&scanned, metadata))
}

/// Inspect the required dependency closure using the same authoritative scan for
/// presentation and mutation admission. This does not resolve plugin versions or
/// invent load ordering for Paper's BEFORE/AFTER/OMIT dependency rules.
fn dependency_problem_from_scan(scanned: &[ScannedPlugin], metadata: &PluginMetadata) -> Option<String> {
    if metadata.dependencies.iter().any(|dependency| dependency == &metadata.id) {
        return Some(format!("Plugin {} declares itself as a required dependency.", metadata.name));
    }

    let mut visited = HashSet::from([metadata.id.clone()]);
    let mut pending = metadata.dependencies.iter().rev()
        .map(|dependency| (metadata.name.clone(), dependency.clone()))
        .collect::<Vec<_>>();

    while let Some((parent, dependency)) = pending.pop() {
        if !visited.insert(dependency.clone()) {
            // A dependency already visited requires no second disk/metadata scan.
            // Cyclic Paper load ordering needs separate, version-specific evidence.
            continue;
        }
        let mut candidates = scanned.iter().filter(|plugin| {
            plugin.metadata.as_ref().is_some_and(|value| value.id == dependency)
        });
        let Some(installed) = candidates.next() else {
            return Some(format!("Missing required dependency {dependency} (required by {parent})."));
        };
        if candidates.next().is_some() {
            return Some(format!("Required dependency {dependency} has duplicate JARs (required by {parent})."));
        }
        if !installed.enabled {
            return Some(format!("Required dependency {dependency} is disabled (required by {parent})."));
        }
        if let Some(required) = installed.metadata.as_ref() {
            pending.extend(required.dependencies.iter().rev()
                .map(|nested| (required.name.clone(), nested.clone())));
        }
    }
    None
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

fn unique_duplicate_selection(existing: &[ScannedPlugin], requested: &str) -> Result<usize, String> {
    let mut matching = existing.iter().enumerate().filter(|(_, item)| {
        item.path.file_name().and_then(|value| value.to_str())
            .is_some_and(|value| value.eq_ignore_ascii_case(requested))
    });
    let Some((keep_index, _)) = matching.next() else {
        return Err("Selected JAR is not one of the duplicate candidates.".into());
    };
    if matching.next().is_some() {
        return Err("Multiple plugin JARs share this filename across directories. Resolve ambiguous files outside Plugin Manager before removing any version.".into());
    }
    Ok(keep_index)
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
    match fs::symlink_metadata(&legacy_disabled) {
        Ok(_) => {
            // A linked/junction migration directory must never import JARs
            // from outside the current workspace.
            paths::ensure_existing_owned_directory(
                workspace, &legacy_disabled, "legacy disabled plugin directory",
            )?;
            for entry in fs::read_dir(&legacy_disabled).map_err(|error| error.to_string())? {
                let source = entry.map_err(|error| error.to_string())?.path();
                if !is_jar(&source) { continue; }
                persistence::safe_path::ensure_regular_file(&source, "legacy disabled plugin JAR")?;
                let Some(file_name) = source.file_name() else { continue; };
                let destination = disabled.join(file_name);
                if destination.exists() { continue; }
                move_file_verified(&source, &destination)?;
            }
        }
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {}
        Err(error) => return Err(format!("Cannot inspect legacy disabled plugin directory: {error}")),
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
    persistence::safe_path::ensure_regular_file(source, "source plugin JAR")?;
    if let Some(parent) = staged.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    // A previous .incoming file can be the only surviving recovery artifact.
    // CREATE_NEW refuses files, symlinks, and races instead of replacing them.
    let mut input = File::open(source).map_err(|error| error.to_string())?;
    let mut output = OpenOptions::new().write(true).create_new(true).open(staged)
        .map_err(|error| format!("Plugin staging path {} is unavailable; preserve existing recovery files: {error}", staged.display()))?;
    let result = (|| -> Result<(), String> {
        std::io::copy(&mut input, &mut output).map_err(|error| error.to_string())?;
        output.flush().map_err(|error| error.to_string())?;
        output.sync_all().map_err(|error| error.to_string())?;
        drop(output);
        if !files_equal(source, staged)? {
            return Err("Staged plugin JAR verification failed.".into());
        }
        Ok(())
    })();
    if result.is_err() {
        // This invocation created the staging file with CREATE_NEW. Only it
        // can clean that file, never a pre-existing recovery copy.
        let _ = fs::remove_file(staged);
    }
    result
}

fn verify_staged_plugin(staged: &Path, expected: &PluginMetadata) -> Result<(), String> {
    // The candidate being published must have the identity checked at admission.
    // Uncached ZIP parsing avoids stale same-size/same-mtime metadata snapshots.
    let actual = read_metadata_uncached(staged)?;
    validate_target_compatibility(&actual)?;
    if &actual != expected {
        return Err("Selected plugin JAR changed while staging; no plugin was published.".into());
    }
    Ok(())
}

fn stage_validated_plugin(source: &Path, staged: &Path, expected: &PluginMetadata) -> Result<(), String> {
    stage_copy(source, staged)?;
    if let Err(error) = verify_staged_plugin(staged, expected) {
        // This operation created this candidate, so cleanup cannot destroy
        // pre-existing recovery evidence.
        let _ = fs::remove_file(staged);
        return Err(error);
    }
    Ok(())
}

fn copy_atomic(source: &Path, destination: &Path, expected: &PluginMetadata) -> Result<(), String> {
    let temporary = destination.with_extension("jar.incoming");
    stage_validated_plugin(source, &temporary, expected)?;
    replace_file(&temporary, destination)
}

fn publish_backup(source: &Path, destination: &Path) -> Result<(), String> {
    if let Some(parent) = destination.parent() { fs::create_dir_all(parent).map_err(|error| error.to_string())?; }
    let staged = destination.with_extension("jar.incoming");
    stage_copy(source, &staged)?;
    replace_file(&staged, destination)
}

// Short-lived duplicate/problem recovery backups are deliberately not reusable.
// A failed rollback leaves these files behind; a later retry must never replace
// the remaining recovery evidence with bytes from a different operation.
fn publish_recovery_backup(source: &Path, destination: &Path) -> Result<(), String> {
    if destination.exists() {
        return Err(format!(
            "Previous plugin recovery backup still exists at {}. Resolve that recovery before retrying.",
            destination.display()
        ));
    }
    publish_backup(source, destination)
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
    persistence::safe_path::ensure_regular_file(source, "staged plugin JAR")?;
    let previous = destination.with_extension("swap.previous");
    if persistence::metadata_entry_exists(&previous, "previous plugin swap")? {
        return Err(format!(
            "Unresolved previous plugin swap at {}; preserve the recovery file before retrying.",
            previous.display()
        ));
    }
    if persistence::metadata_entry_exists(destination, "plugin destination")? {
        persistence::safe_path::ensure_regular_file(destination, "plugin destination")?;
        fs::rename(destination, &previous).map_err(|error| error.to_string())?;
        match fs::rename(source, destination) {
            Ok(()) => {
                // Publication committed; a failed cleanup must not make the
                // caller undo the new JAR or destroy the old recovery copy.
                let _ = persistence::safe_path::remove_regular_file_if_present(&previous, "previous plugin swap");
                Ok(())
            }
            Err(error) => match fs::rename(&previous, destination) {
                Ok(()) => Err(format!("Could not publish staged plugin JAR; original restored: {error}")),
                Err(restore_error) => Err(format!(
                    "Could not publish staged plugin JAR ({error}) or restore previous JAR ({restore_error}). Recovery at {} was preserved.",
                    previous.display()
                )),
            },
        }
    } else {
        fs::rename(source, destination).map_err(|error| error.to_string())
    }
}

fn restore_from_backup(backup: &Path, destination: &Path) -> Result<(), String> {
    persistence::safe_path::ensure_regular_file(backup, "plugin rollback backup")?;
    // Verify the replacement before touching a surviving destination. Never
    // remove the original JAR first and leave a partial copy on failure.
    let staged = destination.with_extension("jar.restore");
    stage_copy(backup, &staged)?;
    replace_file(&staged, destination)?;
    if !files_equal(backup, destination)? {
        return Err("Plugin rollback verification failed; recovery backup retained.".into());
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
    use super::{
        copy_atomic, dependency_problem_from_scan, parse_paper_dependencies, read_metadata,
        publish_recovery_backup, restore_from_backup, replace_file, stage_copy,
        unique_duplicate_selection, PluginMetadata, ScannedPlugin, MAX_PLUGIN_METADATA_BYTES,
    };
    use std::{fs, io::Write, path::PathBuf};
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT_TEST: AtomicU64 = AtomicU64::new(1);
    use serde_yaml::Value;

    fn scanned(id: &str, enabled: bool, required: &[&str]) -> ScannedPlugin {
        ScannedPlugin {
            path: PathBuf::from(format!("{id}.jar")),
            metadata: Some(PluginMetadata {
                id: id.to_string(),
                name: id.to_string(),
                version: "1.0".into(),
                api_version: "1.21".into(),
                dependencies: required.iter().map(|value| value.to_string()).collect(),
            }),
            enabled,
            error: None,
        }
    }

    fn test_directory(label: &str) -> PathBuf {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let path = std::env::temp_dir().join(format!(
            "lazybuilder-plugin-{label}-{}-{sequence}", std::process::id()
        ));
        fs::create_dir_all(&path).unwrap();
        path
    }

    fn write_test_plugin_jar(path: &std::path::Path, name: &str, version: &str) {
        let file = fs::File::create(path).unwrap();
        let mut archive = zip::ZipWriter::new(file);
        archive.start_file("plugin.yml", zip::write::SimpleFileOptions::default()).unwrap();
        write!(archive, "name: {name}\nversion: '{version}'\napi-version: '1.21'\n").unwrap();
        archive.finish().unwrap();
    }

    #[test]
    fn duplicate_filename_across_enabled_and_disabled_is_ambiguous() {
        let mut a = scanned("sample", true, &[]);
        let mut b = scanned("sample", false, &[]);
        a.path = PathBuf::from("server/plugins/sample.jar");
        b.path = PathBuf::from("tools/lazybuilder/disabled-plugins/sample.jar");
        assert!(unique_duplicate_selection(&[a, b], "sample.jar").unwrap_err().contains("share this filename"));
    }

    #[test]
    fn duplicate_selection_accepts_only_one_matching_candidate() {
        let mut a = scanned("sample", true, &[]);
        let mut b = scanned("sample", false, &[]);
        a.path = PathBuf::from("server/plugins/sample-v1.jar");
        b.path = PathBuf::from("tools/lazybuilder/disabled-plugins/sample-v2.jar");
        assert_eq!(unique_duplicate_selection(&[a.clone(), b.clone()], "sample-v2.jar"), Ok(1));
        assert!(unique_duplicate_selection(&[a, b], "missing.jar").is_err());
    }

    #[test]
    fn staged_jar_change_never_replaces_existing_install() {
        let directory = test_directory("changed-stage");
        let validated = directory.join("validated.jar");
        let changed = directory.join("changed.jar");
        let installed = directory.join("installed.jar");
        write_test_plugin_jar(&validated, "VerifiedPlugin", "1.0");
        write_test_plugin_jar(&changed, "ChangedPlugin", "1.0");
        let expected = super::read_metadata_uncached(&validated).unwrap();
        fs::write(&installed, b"previous JAR").unwrap();
        assert!(copy_atomic(&changed, &installed, &expected).unwrap_err().contains("changed while staging"));
        assert_eq!(fs::read(&installed).unwrap(), b"previous JAR");
        assert!(!installed.with_extension("jar.incoming").exists());
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn matching_staged_metadata_is_published() {
        let directory = test_directory("matching-stage");
        let source = directory.join("source.jar");
        let installed = directory.join("installed.jar");
        write_test_plugin_jar(&source, "VerifiedPlugin", "1.0");
        let expected = super::read_metadata_uncached(&source).unwrap();
        copy_atomic(&source, &installed, &expected).unwrap();
        assert_eq!(super::read_metadata_uncached(&installed).unwrap(), expected);
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn direct_self_dependency_is_never_satisfied_by_an_installed_version() {
        let plugins = vec![scanned("alpha", true, &["alpha"])];
        let problem = dependency_problem_from_scan(&plugins, plugins[0].metadata.as_ref().unwrap());
        assert!(problem.unwrap().contains("itself"));
    }

    #[test]
    fn missing_transitive_dependency_is_reported_to_caller() {
        let plugins = vec![
            scanned("alpha", true, &["beta"]),
            scanned("beta", true, &["gamma"]),
        ];
        let problem = dependency_problem_from_scan(&plugins, plugins[0].metadata.as_ref().unwrap());
        assert!(problem.unwrap().contains("gamma"));
    }

    #[test]
    fn disabled_or_duplicate_transitive_dependencies_are_rejected() {
        let plugins = vec![
            scanned("alpha", true, &["beta"]),
            scanned("beta", true, &["gamma"]),
            scanned("gamma", false, &[]),
        ];
        assert!(dependency_problem_from_scan(&plugins, plugins[0].metadata.as_ref().unwrap())
            .unwrap().contains("disabled"));
        let mut duplicated = plugins;
        duplicated[2] = scanned("gamma", true, &[]);
        duplicated.push(scanned("gamma", true, &[]));
        assert!(dependency_problem_from_scan(&duplicated, duplicated[0].metadata.as_ref().unwrap())
            .unwrap().contains("duplicate"));
    }

    #[test]
    fn complete_dependency_closure_is_accepted_without_extra_metadata_authority() {
        let plugins = vec![
            scanned("alpha", true, &["beta"]),
            scanned("beta", true, &["gamma"]),
            scanned("gamma", true, &[]),
        ];
        assert!(dependency_problem_from_scan(&plugins, plugins[0].metadata.as_ref().unwrap()).is_none());
    }

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
    fn rollback_restores_verified_bytes_without_discarding_previous_file_on_invalid_backup() {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "lazybuilder-plugin-rollback-test-{}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).unwrap();
        let destination = directory.join("plugin.jar");
        let backup = directory.join("plugin.previous.jar");

        fs::write(&destination, b"current plugin").unwrap();
        assert!(restore_from_backup(&backup, &destination).is_err());
        assert_eq!(fs::read(&destination).unwrap(), b"current plugin");

        fs::write(&backup, b"verified previous plugin").unwrap();
        restore_from_backup(&backup, &destination).unwrap();
        assert_eq!(fs::read(&destination).unwrap(), b"verified previous plugin");
        assert_eq!(fs::read(&backup).unwrap(), b"verified previous plugin");
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn stage_copy_refuses_to_overwrite_existing_recovery_bytes() {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "lazybuilder-plugin-incoming-collision-{}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).unwrap();
        let source = directory.join("new.jar");
        let pending = directory.join("plugin.jar.incoming");
        fs::write(&source, b"new candidate").unwrap();
        fs::write(&pending, b"only recovery copy").unwrap();

        assert!(stage_copy(&source, &pending).is_err());
        assert_eq!(fs::read(&pending).unwrap(), b"only recovery copy");
        assert_eq!(fs::read(&source).unwrap(), b"new candidate");
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn previous_swap_blocks_new_publication_even_when_destination_is_missing() {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "lazybuilder-plugin-unresolved-swap-{}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).unwrap();
        let staged = directory.join("new.jar.incoming");
        let destination = directory.join("plugin.jar");
        let previous = destination.with_extension("swap.previous");
        fs::write(&staged, b"new candidate").unwrap();
        fs::write(&previous, b"only surviving old JAR").unwrap();

        assert!(replace_file(&staged, &destination).is_err());
        assert_eq!(fs::read(&previous).unwrap(), b"only surviving old JAR");
        assert_eq!(fs::read(&staged).unwrap(), b"new candidate");
        assert!(!destination.exists());
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn normal_replacement_keeps_a_verified_new_jar() {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "lazybuilder-plugin-normal-swap-{}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).unwrap();
        let staged = directory.join("new.jar.incoming");
        let destination = directory.join("plugin.jar");
        fs::write(&staged, b"new JAR").unwrap();
        fs::write(&destination, b"previous JAR").unwrap();

        replace_file(&staged, &destination).unwrap();
        assert_eq!(fs::read(&destination).unwrap(), b"new JAR");
        assert!(!destination.with_extension("swap.previous").exists());
        let _ = fs::remove_dir_all(directory);
    }

    #[test]
    fn retry_never_overwrites_an_unresolved_recovery_backup() {
        let sequence = NEXT_TEST.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!(
            "lazybuilder-plugin-recovery-collision-{}-{sequence}",
            std::process::id()
        ));
        fs::create_dir_all(&directory).unwrap();
        let original = directory.join("original.jar");
        let backup = directory.join("recovery.jar");
        fs::write(&original, b"new attempt").unwrap();
        fs::write(&backup, b"only surviving previous version").unwrap();
        assert!(publish_recovery_backup(&original, &backup).is_err());
        assert_eq!(fs::read(&backup).unwrap(), b"only surviving previous version");
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
