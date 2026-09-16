use crate::engine::java_runtime;
use serde::{Deserialize, Serialize};
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::{OnceLock, RwLock};
use std::time::{SystemTime, UNIX_EPOCH};
use sysinfo::Disks;
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;

const REGISTRY_SCHEMA_VERSION: u32 = 1;
const WORKSPACE_SCHEMA_VERSION: u32 = 1;
const MINECRAFT_VERSION: &str = "1.21.4";
const SERVER_PLATFORM: &str = "paper";
const MIN_DUPLICATE_HEADROOM_BYTES: u64 = 512 * 1024 * 1024;
#[cfg(windows)]
const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x00000400;
static ACTIVE_WORKSPACE: OnceLock<RwLock<Option<PathBuf>>> = OnceLock::new();

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorkspaceEntry {
    pub id: String,
    pub name: String,
    pub path: String,
    pub last_opened_unix_seconds: u64,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct WorkspaceRegistryFile {
    schema_version: u32,
    servers: Vec<WorkspaceEntry>,
}

impl Default for WorkspaceRegistryFile {
    fn default() -> Self { Self { schema_version: REGISTRY_SCHEMA_VERSION, servers: Vec::new() } }
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorkspaceManifest {
    pub schema_version: u32,
    pub workspace_id: String,
    pub name: String,
    pub minecraft_version: String,
    pub server_platform: String,
    pub paper_build: Option<u32>,
    pub world_manager_version: Option<String>,
    pub utilities_manager_version: Option<String>,
    pub created_unix_seconds: u64,
    pub last_opened_unix_seconds: u64,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProvisioningStatus {
    pub workspace_created: bool,
    pub java_ready: bool,
    pub paper_ready: bool,
    pub core_modules_ready: bool,
    pub config_ready: bool,
    pub eula_accepted: bool,
    pub ready: bool,
    pub next_step: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WorkspaceDuplicateEstimate {
    pub source_bytes: u64,
    pub required_bytes: u64,
    pub available_bytes: Option<u64>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DuplicateRecoveryReport {
    pub recovered: u32,
    pub completed: u32,
    pub cleaned: u32,
    pub issues: Vec<String>,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PendingDeletion {
    workspace_id: String,
    original_path: String,
    staging_path: String,
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PendingDuplicate {
    source_workspace_id: String,
    source_path: String,
    destination_parent: String,
    staging_path: String,
    final_path: String,
    requested_name: String,
}

pub fn initialize() -> Result<(), String> {
    let _ = load_registry()?;
    recover_pending_deletions()?;
    set_active_memory(None)
}

pub fn active_workspace() -> Result<PathBuf, String> {
    ACTIVE_WORKSPACE.get_or_init(|| RwLock::new(None)).read().map_err(|_| "workspace state lock poisoned".to_string())?.clone().ok_or_else(|| "No LazyBuilder server workspace is active. Create or open a server first.".to_string())
}

pub fn current() -> Result<Option<WorkspaceEntry>, String> {
    let registry = load_registry()?;
    let Some(active) = active_workspace_memory()? else { return Ok(None); };
    let active_text = active.display().to_string();
    Ok(registry.servers.into_iter().find(|entry| entry.path == active_text))
}

pub fn get(id: &str) -> Result<WorkspaceEntry, String> {
    load_registry()?.servers.into_iter().find(|entry| entry.id == id).ok_or_else(|| "Saved server workspace was not found".to_string())
}

pub fn list() -> Result<Vec<WorkspaceEntry>, String> {
    let mut registry = load_registry()?;
    registry.servers.sort_by(|left, right| right.last_opened_unix_seconds.cmp(&left.last_opened_unix_seconds));
    Ok(registry.servers)
}

pub fn create(parent: &Path, name: &str) -> Result<WorkspaceEntry, String> {
    let safe_name = validate_workspace_name(name)?;
    let parent = parent.canonicalize().map_err(|error| format!("Could not resolve server location: {error}"))?;
    if !parent.is_dir() { return Err("Selected server location is not a directory".into()); }
    let root = parent.join(&safe_name);
    if root.exists() {
        let mut entries = fs::read_dir(&root).map_err(|error| error.to_string())?;
        if entries.next().is_some() { return Err("A non-empty folder with that server name already exists".into()); }
    }
    fs::create_dir_all(&root).map_err(|error| error.to_string())?;
    provision_layout(&root)?;
    let canonical = root.canonicalize().map_err(|error| error.to_string())?;
    let id = workspace_id(&canonical.display().to_string());
    let now = now_unix_seconds();
    write_manifest(&canonical, WorkspaceManifest {
        schema_version: WORKSPACE_SCHEMA_VERSION,
        workspace_id: id,
        name: safe_name.clone(),
        minecraft_version: MINECRAFT_VERSION.into(),
        server_platform: SERVER_PLATFORM.into(),
        paper_build: None,
        world_manager_version: None,
        utilities_manager_version: None,
        created_unix_seconds: now,
        last_opened_unix_seconds: now,
    })?;
    register_and_activate(&canonical, &safe_name)
}

pub fn open(root: &Path) -> Result<WorkspaceEntry, String> { open_with_display_name(root, None) }

pub fn open_with_display_name(root: &Path, requested_name: Option<&str>) -> Result<WorkspaceEntry, String> {
    let root = root.canonicalize().map_err(|error| format!("Could not resolve server workspace: {error}"))?;
    if !root.is_dir() { return Err("Selected workspace is not a directory".into()); }
    let requested_name = requested_name.map(validate_display_name).transpose()?;
    let name = match read_manifest(&root)? {
        Some(mut manifest) => {
            validate_manifest(&manifest)?;
            if let Some(name) = requested_name.as_ref() { manifest.name = name.clone(); }
            manifest.last_opened_unix_seconds = now_unix_seconds();
            write_manifest(&root, manifest.clone())?;
            manifest.name
        }
        None if looks_like_legacy_lazybuilder_workspace(&root) => {
            let name = requested_name.unwrap_or_else(|| root.file_name().and_then(|value| value.to_str()).unwrap_or("LazyBuilder Server").to_string());
            let id = workspace_id(&root.display().to_string());
            let now = now_unix_seconds();
            write_manifest(&root, WorkspaceManifest {
                schema_version: WORKSPACE_SCHEMA_VERSION,
                workspace_id: id,
                name: name.clone(),
                minecraft_version: MINECRAFT_VERSION.into(),
                server_platform: SERVER_PLATFORM.into(),
                paper_build: None,
                world_manager_version: None,
                utilities_manager_version: None,
                created_unix_seconds: now,
                last_opened_unix_seconds: now,
            })?;
            name
        }
        None => return Err("This folder is not a LazyBuilder workspace. Plain Paper servers must be adopted through the migration flow instead of opened directly.".into()),
    };
    provision_layout(&root)?;
    register_and_activate(&root, &name)
}

pub fn activate(id: &str) -> Result<WorkspaceEntry, String> {
    let mut registry = load_registry()?;
    let entry = registry.servers.iter_mut().find(|entry| entry.id == id).ok_or_else(|| "Saved server workspace was not found".to_string())?;
    let path = PathBuf::from(&entry.path);
    if !path.is_dir() { return Err(format!("Saved server workspace is currently unavailable: {}. Reconnect or restore that location and try again.", path.display())); }
    let canonical = path.canonicalize().map_err(|error| error.to_string())?;
    let manifest = read_manifest(&canonical)?.ok_or_else(|| "Saved server workspace has no LazyBuilder manifest".to_string())?;
    validate_manifest(&manifest)?;
    if manifest.workspace_id != entry.id { return Err("Saved server workspace identity does not match its manifest. Use Locate only with the original workspace identity.".into()); }
    entry.last_opened_unix_seconds = now_unix_seconds();
    let result = entry.clone();
    save_registry(&registry)?;
    let mut updated_manifest = manifest;
    updated_manifest.last_opened_unix_seconds = now_unix_seconds();
    write_manifest(&canonical, updated_manifest)?;
    set_active_memory(Some(canonical))?;
    Ok(result)
}

pub fn deactivate() -> Result<(), String> { set_active_memory(None) }

pub fn relocate(id: &str, selected_root: &Path) -> Result<WorkspaceEntry, String> {
    let previous = get(id)?;
    let selected_metadata = fs::symlink_metadata(selected_root).map_err(|error| format!("Could not inspect selected server location: {error}"))?;
    if is_unsafe_link_or_reparse(selected_root, &selected_metadata.file_type())? { return Err("LazyBuilder refused a symbolic link or Windows reparse point as a server location".into()); }
    let canonical = selected_root.canonicalize().map_err(|error| format!("Could not resolve selected server location: {error}"))?;
    if !canonical.is_dir() { return Err("Selected server location is not a directory".into()); }
    let manifest = read_manifest(&canonical)?.ok_or_else(|| "Selected folder is not the missing LazyBuilder server: workspace.json is missing".to_string())?;
    validate_manifest(&manifest)?;
    if manifest.workspace_id != id { return Err("Selected folder belongs to a different LazyBuilder server. Use Add existing for that server instead.".into()); }
    let canonical_text = canonical.display().to_string();
    let mut registry = load_registry()?;
    if registry.servers.iter().any(|entry| entry.id != id && entry.path.eq_ignore_ascii_case(&canonical_text)) { return Err("That folder is already registered as another LazyBuilder server".into()); }
    let target = registry.servers.iter_mut().find(|entry| entry.id == id).ok_or_else(|| "Saved server workspace was not found".to_string())?;
    let was_active = active_workspace_memory()?.is_some_and(|active| active.display().to_string().eq_ignore_ascii_case(&previous.path));
    target.path = canonical_text;
    target.name = manifest.name.clone();
    target.last_opened_unix_seconds = now_unix_seconds();
    let result = target.clone();
    save_registry(&registry)?;
    if was_active { set_active_memory(Some(canonical))?; }
    Ok(result)
}

pub fn duplicate_estimate(id: &str, destination_parent: &Path) -> Result<WorkspaceDuplicateEstimate, String> {
    let entry = get(id)?;
    let source = validated_registered_root(&entry)?;
    let parent = duplicate_destination_parent(&source, destination_parent)?;
    let source_bytes = directory_size_filtered(&source, Path::new(""))?;
    let margin = (source_bytes / 10).max(MIN_DUPLICATE_HEADROOM_BYTES);
    let required_bytes = source_bytes.saturating_add(margin);
    let available_bytes = available_space_for(&parent);
    Ok(WorkspaceDuplicateEstimate { source_bytes, required_bytes, available_bytes })
}

pub fn duplicate(id: &str, destination_parent: &Path, name: &str) -> Result<WorkspaceEntry, String> {
    let entry = get(id)?;
    let source = validated_registered_root(&entry)?;
    let safe_name = validate_workspace_name(name)?;
    let parent = duplicate_destination_parent(&source, destination_parent)?;
    let final_root = parent.join(&safe_name);
    if final_root.exists() { return Err("A file or folder with the duplicate server name already exists".into()); }

    let estimate = duplicate_estimate(id, &parent)?;
    if let Some(available) = estimate.available_bytes {
        if available < estimate.required_bytes {
            return Err(format!("Not enough storage to duplicate this server. Required approximately {} MB; available {} MB.", bytes_to_mb(estimate.required_bytes), bytes_to_mb(available)));
        }
    }

    let staging = parent.join(format!(".lazybuilder-copying-{}-{}", std::process::id(), now_unix_millis()));
    if staging.exists() { return Err("LazyBuilder duplicate staging path already exists".into()); }
    if !safe_duplicate_paths(&parent, &staging, &final_root, &safe_name) { return Err("LazyBuilder refused unsafe duplicate staging paths".into()); }

    let intent = PendingDuplicate {
        source_workspace_id: entry.id.clone(),
        source_path: source.display().to_string(),
        destination_parent: parent.display().to_string(),
        staging_path: staging.display().to_string(),
        final_path: final_root.display().to_string(),
        requested_name: safe_name.clone(),
    };
    add_pending_duplicate(intent.clone())?;

    if let Err(error) = fs::create_dir(&staging) {
        return duplicate_failure_with_cleanup(&intent, false, format!("Could not create duplicate staging directory: {error}"));
    }

    let prepare_result = (|| -> Result<(), String> {
        copy_directory_filtered(&source, &staging, Path::new(""))?;
        let now = now_unix_seconds();
        let new_id = workspace_id(&final_root.display().to_string());
        let mut manifest = read_manifest(&staging)?.ok_or_else(|| "Duplicate staging copy is missing its LazyBuilder workspace manifest".to_string())?;
        validate_manifest(&manifest)?;
        manifest.workspace_id = new_id;
        manifest.name = safe_name.clone();
        manifest.created_unix_seconds = now;
        manifest.last_opened_unix_seconds = now;
        write_manifest(&staging, manifest)
    })();
    if let Err(error) = prepare_result { return duplicate_failure_with_cleanup(&intent, true, error); }

    if let Err(error) = fs::rename(&staging, &final_root) {
        return duplicate_failure_with_cleanup(&intent, true, format!("Could not publish duplicated server: {error}"));
    }

    let canonical = final_root.canonicalize().map_err(|error| duplicate_recovery_error(format!("Duplicated server was published but its final path could not be validated: {error}")))?;
    let canonical_id = workspace_id(&canonical.display().to_string());
    let mut manifest = read_manifest(&canonical).map_err(|error| duplicate_recovery_error(format!("Published duplicate manifest could not be read: {error}")))?.ok_or_else(|| duplicate_recovery_error("Published duplicate is missing its LazyBuilder workspace manifest"))?;
    validate_manifest(&manifest).map_err(|error| duplicate_recovery_error(format!("Published duplicate manifest is invalid: {error}")))?;
    manifest.workspace_id = canonical_id;
    manifest.name = safe_name.clone();
    write_manifest(&canonical, manifest).map_err(|error| duplicate_recovery_error(format!("Could not finalize published duplicate identity: {error}")))?;

    let result = register_only(&canonical, &safe_name).map_err(|error| duplicate_recovery_error(format!("Duplicated server was published but could not be registered: {error}")))?;
    let _ = clear_pending_duplicate(&intent.staging_path);
    Ok(result)
}

pub fn recover_pending_duplicates() -> Result<DuplicateRecoveryReport, String> {
    let pending = load_pending_duplicates()?;
    if pending.is_empty() { return Ok(DuplicateRecoveryReport { recovered: 0, completed: 0, cleaned: 0, issues: Vec::new() }); }

    let mut remaining = Vec::new();
    let mut report = DuplicateRecoveryReport { recovered: 0, completed: 0, cleaned: 0, issues: Vec::new() };

    for intent in pending {
        let source = PathBuf::from(&intent.source_path);
        let parent = PathBuf::from(&intent.destination_parent);
        let staging = PathBuf::from(&intent.staging_path);
        let final_root = PathBuf::from(&intent.final_path);

        if !safe_duplicate_paths(&parent, &staging, &final_root, &intent.requested_name) {
            report.issues.push(format!("Duplicate recovery for {} has unsafe recorded paths. All files were preserved for manual review.", intent.requested_name));
            remaining.push(intent);
            continue;
        }
        if !path_matches(&get(&intent.source_workspace_id).map(|entry| entry.path).unwrap_or_default(), &intent.source_path) {
            report.issues.push(format!("Duplicate recovery for {} no longer matches the registered source server path. All files were preserved.", intent.requested_name));
            remaining.push(intent);
            continue;
        }
        if let Err(error) = reject_existing_reparse_points(&[&source, &parent, &staging, &final_root]) {
            report.issues.push(format!("Duplicate recovery for {} was blocked by filesystem safety validation: {error}", intent.requested_name));
            remaining.push(intent);
            continue;
        }

        if final_root.exists() && staging.exists() {
            report.issues.push(format!("Duplicate recovery for {} is ambiguous because both staging and final paths exist. Nothing was deleted.", intent.requested_name));
            remaining.push(intent);
            continue;
        }

        if final_root.is_dir() {
            match finish_recovered_duplicate(&final_root, &intent.requested_name) {
                Ok(()) => {
                    report.recovered = report.recovered.saturating_add(1);
                    report.completed = report.completed.saturating_add(1);
                }
                Err(error) => {
                    report.issues.push(format!("Duplicate recovery for {} found a published copy but could not safely register it: {error}", intent.requested_name));
                    remaining.push(intent);
                }
            }
            continue;
        }

        if final_root.exists() {
            report.issues.push(format!("Duplicate recovery for {} found a non-directory final target. Nothing was changed.", intent.requested_name));
            remaining.push(intent);
            continue;
        }

        if staging.is_dir() {
            match remove_owned_duplicate_staging(&parent, &staging) {
                Ok(()) => {
                    report.recovered = report.recovered.saturating_add(1);
                    report.cleaned = report.cleaned.saturating_add(1);
                }
                Err(error) => {
                    report.issues.push(format!("Duplicate recovery for {} could not clean interrupted staging: {error}", intent.requested_name));
                    remaining.push(intent);
                }
            }
            continue;
        }

        if staging.exists() {
            report.issues.push(format!("Duplicate recovery for {} found a non-directory staging target. Nothing was changed.", intent.requested_name));
            remaining.push(intent);
            continue;
        }

        report.recovered = report.recovered.saturating_add(1);
        report.cleaned = report.cleaned.saturating_add(1);
    }

    save_pending_duplicates(&remaining)?;
    Ok(report)
}

pub fn remove_from_library(id: &str) -> Result<(), String> {
    let was_active = current()?.is_some_and(|entry| entry.id == id);
    let mut registry = load_registry()?;
    if !registry.servers.iter().any(|entry| entry.id == id) { return Err("Saved server workspace was not found".into()); }
    registry.servers.retain(|entry| entry.id != id);
    save_registry(&registry)?;
    if was_active { set_active_memory(None)?; }
    Ok(())
}

pub fn delete(id: &str, typed_display_name: &str) -> Result<(), String> {
    let entry = get(id)?;
    let was_active = current()?.is_some_and(|candidate| candidate.id == id);
    if typed_display_name != entry.name { return Err("Type the server name exactly to confirm permanent deletion".into()); }
    let root = validated_registered_root(&entry)?;
    let parent = root.parent().ok_or_else(|| "Server workspace has no parent directory".to_string())?;
    let base = root.file_name().and_then(|value| value.to_str()).unwrap_or("server");
    let staging = parent.join(format!(".{base}.lazybuilder-deleting-{}-{}", std::process::id(), now_unix_seconds()));
    if staging.exists() { return Err("LazyBuilder deletion staging path already exists".into()); }
    if !is_safe_deletion_staging(&root, &staging) { return Err("LazyBuilder refused an unsafe deletion staging path".into()); }

    add_pending_deletion(PendingDeletion { workspace_id: entry.id.clone(), original_path: root.display().to_string(), staging_path: staging.display().to_string() })?;
    if let Err(error) = fs::rename(&root, &staging) {
        let _ = clear_pending_deletion(&entry.id, &staging);
        return Err(format!("Could not stage server for deletion: {error}"));
    }

    let mut registry = load_registry()?;
    registry.servers.retain(|candidate| candidate.id != id);
    if let Err(error) = save_registry(&registry) {
        match fs::rename(&staging, &root) {
            Ok(()) => { let _ = clear_pending_deletion(&entry.id, &staging); return Err(format!("Could not update server library during deletion: {error}")); }
            Err(rollback_error) => return Err(format!("Could not update server library during deletion: {error}. Rollback also failed: {rollback_error}. Recovery intent was preserved for the next LazyBuilder start.")),
        }
    }
    if was_active { set_active_memory(None)?; }
    match fs::remove_dir_all(&staging) {
        Ok(()) => clear_pending_deletion(&entry.id, &staging),
        Err(error) => Err(format!("The server was removed from the library, but final deletion cleanup is pending and will be retried on the next LazyBuilder start: {error}")),
    }
}

pub fn provisioning_status() -> Result<ProvisioningStatus, String> {
    let root = active_workspace()?;
    let workspace_created = manifest_path(&root).is_file();
    let config_ready = root.join("tools").join("lazybuilder").join("config").is_dir() && root.join("server").is_dir() && root.join("server").join("plugins").is_dir();
    let paper_ready = root.join("server").join("paper.jar").is_file();
    let plugins = root.join("server").join("plugins");
    let core_modules_ready = contains_plugin_prefix(&plugins, "World-Manager-")? && contains_plugin_prefix(&plugins, "Utilities-Manager-")?;
    let eula_accepted = read_eula(&root)?;
    let java_ready = java_runtime::managed_java_ready();
    let ready = workspace_created && java_ready && paper_ready && core_modules_ready && config_ready && eula_accepted;
    let next_step = if !workspace_created { "Create workspace metadata" }
        else if !java_ready { "Install Java 21 runtime" }
        else if !paper_ready { "Provision Paper 1.21.4" }
        else if !core_modules_ready { "Install LazyBuilder core modules" }
        else if !config_ready { "Prepare server configuration" }
        else if !eula_accepted { "Accept the Minecraft EULA" }
        else { "Ready" };
    Ok(ProvisioningStatus { workspace_created, java_ready, paper_ready, core_modules_ready, config_ready, eula_accepted, ready, next_step: next_step.into() })
}

pub fn accept_eula() -> Result<(), String> {
    let root = active_workspace()?;
    fs::write(root.join("server").join("eula.txt"), "# Accepted through LazyBuilder after explicit user confirmation\neula=true\n").map_err(|error| error.to_string())
}

fn duplicate_failure_with_cleanup(intent: &PendingDuplicate, staging_may_exist: bool, message: String) -> Result<WorkspaceEntry, String> {
    let staging = PathBuf::from(&intent.staging_path);
    let parent = PathBuf::from(&intent.destination_parent);
    let cleanup = if staging_may_exist { remove_owned_duplicate_staging(&parent, &staging) } else { Ok(()) };
    let clear = clear_pending_duplicate(&intent.staging_path);
    if cleanup.is_err() || clear.is_err() {
        Err(duplicate_recovery_error(format!("{message}. Duplicate recovery metadata was preserved because cleanup did not complete safely.")))
    } else {
        Err(message)
    }
}

fn duplicate_recovery_error(message: impl AsRef<str>) -> String {
    format!("DUPLICATE_RECOVERY_REQUIRED: {}", message.as_ref())
}

fn duplicate_destination_parent(source: &Path, destination_parent: &Path) -> Result<PathBuf, String> {
    let parent = destination_parent.canonicalize().map_err(|error| format!("Could not resolve duplicate destination: {error}"))?;
    if !parent.is_dir() { return Err("Duplicate destination is not a directory".into()); }
    if parent == source || parent.starts_with(source) { return Err("Choose a duplicate destination outside the source server workspace".into()); }
    Ok(parent)
}

fn safe_duplicate_paths(parent: &Path, staging: &Path, final_root: &Path, requested_name: &str) -> bool {
    if staging.parent() != Some(parent) || final_root.parent() != Some(parent) { return false; }
    if final_root.file_name().and_then(|value| value.to_str()) != Some(requested_name) { return false; }
    staging.file_name().and_then(|value| value.to_str()).is_some_and(|name| name.starts_with(".lazybuilder-copying-") && name.len() > ".lazybuilder-copying-".len())
}

fn validated_registered_root(entry: &WorkspaceEntry) -> Result<PathBuf, String> {
    let root = PathBuf::from(&entry.path).canonicalize().map_err(|error| format!("Could not resolve registered server workspace: {error}"))?;
    if !root.is_dir() { return Err("Registered server workspace is not a directory".into()); }
    let manifest = read_manifest(&root)?.ok_or_else(|| "Registered server workspace has no LazyBuilder manifest".to_string())?;
    validate_manifest(&manifest)?;
    if manifest.workspace_id != entry.id { return Err("Registered server identity does not match its workspace manifest".into()); }
    Ok(root)
}

fn registered_manifest(root: &Path) -> Result<WorkspaceManifest, String> {
    let manifest = read_manifest(root)?.ok_or_else(|| "LazyBuilder workspace manifest is missing".to_string())?;
    validate_manifest(&manifest)?;
    Ok(manifest)
}

fn register_and_activate(root: &Path, name: &str) -> Result<WorkspaceEntry, String> {
    let canonical = root.canonicalize().map_err(|error| error.to_string())?;
    let canonical_text = canonical.display().to_string();
    let manifest = registered_manifest(&canonical)?;
    let id = manifest.workspace_id;
    let now = now_unix_seconds();
    let mut registry = load_registry()?;
    if registry.servers.iter().any(|entry| entry.id != id && entry.path.eq_ignore_ascii_case(&canonical_text)) { return Err("This workspace path is already registered as another LazyBuilder server".into()); }
    if let Some(existing) = registry.servers.iter_mut().find(|entry| entry.id == id) {
        existing.name = name.to_string();
        existing.path = canonical_text.clone();
        existing.last_opened_unix_seconds = now;
    } else {
        registry.servers.push(WorkspaceEntry { id: id.clone(), name: name.to_string(), path: canonical_text, last_opened_unix_seconds: now });
    }
    let result = registry.servers.iter().find(|entry| entry.id == id).cloned().ok_or_else(|| "Registered server workspace could not be recovered".to_string())?;
    save_registry(&registry)?;
    set_active_memory(Some(canonical))?;
    Ok(result)
}

fn register_only(root: &Path, name: &str) -> Result<WorkspaceEntry, String> {
    let canonical = root.canonicalize().map_err(|error| error.to_string())?;
    let canonical_text = canonical.display().to_string();
    let id = registered_manifest(&canonical)?.workspace_id;
    let now = now_unix_seconds();
    let mut registry = load_registry()?;
    if registry.servers.iter().any(|entry| entry.id == id || entry.path.eq_ignore_ascii_case(&canonical_text)) { return Err("This server workspace is already registered in LazyBuilder".into()); }
    let result = WorkspaceEntry { id, name: name.to_string(), path: canonical_text, last_opened_unix_seconds: now };
    registry.servers.push(result.clone());
    save_registry(&registry)?;
    Ok(result)
}

fn finish_recovered_duplicate(root: &Path, requested_name: &str) -> Result<(), String> {
    let canonical = root.canonicalize().map_err(|error| error.to_string())?;
    let canonical_text = canonical.display().to_string();
    let expected_id = workspace_id(&canonical_text);
    let mut manifest = registered_manifest(&canonical)?;
    if manifest.workspace_id != expected_id { return Err("Published duplicate identity does not match its final canonical path".into()); }
    if manifest.name != requested_name { return Err("Published duplicate name does not match the recorded duplicate intent".into()); }

    let registry = load_registry()?;
    if let Some(existing) = registry.servers.iter().find(|entry| entry.id == expected_id || entry.path.eq_ignore_ascii_case(&canonical_text)) {
        if existing.id == expected_id && existing.path.eq_ignore_ascii_case(&canonical_text) { return Ok(()); }
        return Err("Published duplicate conflicts with another registered server".into());
    }
    drop(registry);
    manifest.last_opened_unix_seconds = now_unix_seconds();
    write_manifest(&canonical, manifest)?;
    register_only(&canonical, requested_name).map(|_| ())
}

fn active_workspace_memory() -> Result<Option<PathBuf>, String> {
    ACTIVE_WORKSPACE.get_or_init(|| RwLock::new(None)).read().map_err(|_| "workspace state lock poisoned".to_string()).map(|guard| guard.clone())
}

fn set_active_memory(value: Option<PathBuf>) -> Result<(), String> {
    *ACTIVE_WORKSPACE.get_or_init(|| RwLock::new(None)).write().map_err(|_| "workspace state lock poisoned".to_string())? = value;
    Ok(())
}

fn app_data_root() -> Result<PathBuf, String> {
    std::env::var_os("LOCALAPPDATA").map(PathBuf::from).or_else(|| std::env::var_os("APPDATA").map(PathBuf::from)).map(|path| path.join("LazyBuilder")).ok_or_else(|| "Windows application data directory is unavailable".to_string())
}
fn registry_path() -> Result<PathBuf, String> { Ok(app_data_root()?.join("workspaces.json")) }
fn pending_deletions_path() -> Result<PathBuf, String> { Ok(app_data_root()?.join("pending-deletions.json")) }
fn pending_duplicates_path() -> Result<PathBuf, String> { Ok(app_data_root()?.join("pending-duplicates.json")) }

fn load_registry() -> Result<WorkspaceRegistryFile, String> {
    let path = registry_path()?;
    recover_json_file(&path, "workspace registry")?;
    if !metadata_entry_exists(&path, "workspace registry")? { return Ok(WorkspaceRegistryFile::default()); }
    ensure_regular_metadata_file(&path, "workspace registry")?;
    let text = fs::read_to_string(&path).map_err(|error| error.to_string())?;
    let mut registry: WorkspaceRegistryFile = serde_json::from_str(&text).map_err(|error| error.to_string())?;
    if registry.schema_version != REGISTRY_SCHEMA_VERSION { return Err("Workspace registry schema is newer or unsupported".into()); }
    registry.servers.retain(|entry| !entry.path.trim().is_empty());
    Ok(registry)
}

fn save_registry(registry: &WorkspaceRegistryFile) -> Result<(), String> {
    let path = registry_path()?;
    let text = serde_json::to_string_pretty(registry).map_err(|error| error.to_string())?;
    write_json_file(&path, text.as_bytes(), "workspace registry")
}

fn load_pending_deletions() -> Result<Vec<PendingDeletion>, String> {
    let path = pending_deletions_path()?;
    recover_json_file(&path, "pending server deletions")?;
    if !metadata_entry_exists(&path, "pending server deletions")? { return Ok(Vec::new()); }
    ensure_regular_metadata_file(&path, "pending server deletions")?;
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    serde_json::from_str(&text).map_err(|error| format!("Could not read pending server deletions: {error}"))
}

fn save_pending_deletions(entries: &[PendingDeletion]) -> Result<(), String> {
    let path = pending_deletions_path()?;
    if entries.is_empty() {
        remove_metadata_file_if_exists(&path, "pending server deletions")?;
        return Ok(());
    }
    let text = serde_json::to_string_pretty(entries).map_err(|error| error.to_string())?;
    write_json_file(&path, text.as_bytes(), "pending server deletions")
}

fn load_pending_duplicates() -> Result<Vec<PendingDuplicate>, String> {
    let path = pending_duplicates_path()?;
    recover_json_file(&path, "pending server duplicates")?;
    if !metadata_entry_exists(&path, "pending server duplicates")? { return Ok(Vec::new()); }
    ensure_regular_metadata_file(&path, "pending server duplicates")?;
    let text = fs::read_to_string(path).map_err(|error| format!("Could not read pending server duplicates: {error}"))?;
    serde_json::from_str(&text).map_err(|error| format!("Could not parse pending server duplicates: {error}"))
}

fn save_pending_duplicates(entries: &[PendingDuplicate]) -> Result<(), String> {
    let path = pending_duplicates_path()?;
    if entries.is_empty() {
        remove_metadata_file_if_exists(&path, "pending server duplicates")?;
        return Ok(());
    }
    let text = serde_json::to_string_pretty(entries).map_err(|error| error.to_string())?;
    write_json_file(&path, text.as_bytes(), "pending server duplicates")
}

fn add_pending_duplicate(intent: PendingDuplicate) -> Result<(), String> {
    let mut entries = load_pending_duplicates()?;
    if entries.iter().any(|item| path_matches(&item.final_path, &intent.final_path) || path_matches(&item.staging_path, &intent.staging_path)) {
        return Err("A previous duplicate at this destination still requires recovery".into());
    }
    entries.push(intent);
    save_pending_duplicates(&entries)
}

fn clear_pending_duplicate(staging_path: &str) -> Result<(), String> {
    let mut entries = load_pending_duplicates()?;
    entries.retain(|item| !path_matches(&item.staging_path, staging_path));
    save_pending_duplicates(&entries)
}

fn write_json_file(destination: &Path, bytes: &[u8], label: &str) -> Result<(), String> {
    if let Some(parent) = destination.parent() {
        fs::create_dir_all(parent).map_err(|error| format!("Could not prepare {label} directory: {error}"))?;
    }
    let incoming = destination.with_extension("json.incoming");
    if metadata_entry_exists(&incoming, &format!("{label} staging file"))? {
        ensure_regular_metadata_file(&incoming, &format!("{label} staging file"))?;
        fs::remove_file(&incoming).map_err(|error| format!("Could not clear stale {label} staging file: {error}"))?;
    }
    let mut file = fs::OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(&incoming)
        .map_err(|error| format!("Could not create {label} staging file: {error}"))?;
    file.write_all(bytes).map_err(|error| format!("Could not write {label} staging file: {error}"))?;
    file.sync_all().map_err(|error| format!("Could not flush {label} staging file: {error}"))?;
    drop(file);
    replace_json_file(&incoming, destination, label)
}

fn recover_json_file(destination: &Path, label: &str) -> Result<(), String> {
    let previous = destination.with_extension("json.previous");
    let incoming = destination.with_extension("json.incoming");
    let legacy_tmp = destination.with_extension("json.tmp");

    if metadata_entry_exists(destination, label)? {
        ensure_regular_metadata_file(destination, label)?;
        remove_metadata_file_if_exists(&previous, &format!("previous {label}"))?;
        remove_metadata_file_if_exists(&incoming, &format!("{label} staging file"))?;
        remove_metadata_file_if_exists(&legacy_tmp, &format!("legacy {label} staging file"))?;
        return Ok(());
    }

    if metadata_entry_exists(&previous, &format!("previous {label}"))? {
        ensure_regular_metadata_file(&previous, &format!("previous {label}"))?;
        fs::rename(&previous, destination).map_err(|error| format!("Could not restore previous {label}: {error}"))?;
        remove_metadata_file_if_exists(&incoming, &format!("{label} staging file"))?;
        remove_metadata_file_if_exists(&legacy_tmp, &format!("legacy {label} staging file"))?;
        return Ok(());
    }

    let incoming_exists = metadata_entry_exists(&incoming, &format!("{label} staging file"))?;
    let legacy_exists = metadata_entry_exists(&legacy_tmp, &format!("legacy {label} staging file"))?;
    if incoming_exists && legacy_exists {
        return Err(format!("LazyBuilder found ambiguous {label} recovery staging files. Both were preserved for manual review."));
    }
    let staging = if incoming_exists { Some(incoming) } else if legacy_exists { Some(legacy_tmp) } else { None };
    if let Some(staging) = staging {
        ensure_regular_metadata_file(&staging, &format!("{label} staging file"))?;
        fs::rename(&staging, destination).map_err(|error| format!("Could not publish recovered {label}: {error}"))?;
    }
    Ok(())
}

fn replace_json_file(source: &Path, destination: &Path, label: &str) -> Result<(), String> {
    ensure_regular_metadata_file(source, &format!("{label} staging file"))?;
    if metadata_entry_exists(destination, label)? {
        ensure_regular_metadata_file(destination, label)?;
        let backup = destination.with_extension("json.previous");
        remove_metadata_file_if_exists(&backup, &format!("previous {label}"))?;
        fs::rename(destination, &backup).map_err(|error| format!("Could not preserve previous {label}: {error}"))?;
        match fs::rename(source, destination) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(publish_error) => match fs::rename(&backup, destination) {
                Ok(()) => Err(format!("Could not publish {label}; previous metadata was restored: {publish_error}")),
                Err(rollback_error) => Err(format!("Could not publish {label} ({publish_error}) and could not restore previous metadata ({rollback_error}). Recovery files were preserved.")),
            },
        }
    } else {
        fs::rename(source, destination).map_err(|error| format!("Could not publish {label}: {error}"))
    }
}

fn metadata_entry_exists(path: &Path, label: &str) -> Result<bool, String> {
    match fs::symlink_metadata(path) {
        Ok(_) => Ok(true),
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => Ok(false),
        Err(error) => Err(format!("Could not inspect {label}: {error}")),
    }
}

fn ensure_regular_metadata_file(path: &Path, label: &str) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|error| format!("Could not inspect {label}: {error}"))?;
    if metadata.file_type().is_symlink() {
        return Err(format!("LazyBuilder refused a symbolic link as {label}"));
    }
    #[cfg(windows)]
    if metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0 {
        return Err(format!("LazyBuilder refused a Windows reparse point as {label}"));
    }
    if !metadata.file_type().is_file() {
        return Err(format!("LazyBuilder expected {label} to be a regular file"));
    }
    Ok(())
}

fn remove_metadata_file_if_exists(path: &Path, label: &str) -> Result<(), String> {
    if !metadata_entry_exists(path, label)? {
        return Ok(());
    }
    ensure_regular_metadata_file(path, label)?;
    fs::remove_file(path).map_err(|error| format!("Could not remove {label}: {error}"))
}

fn add_pending_deletion(entry: PendingDeletion) -> Result<(), String> {
    let mut entries = load_pending_deletions()?;
    entries.retain(|candidate| candidate.workspace_id != entry.workspace_id);
    entries.push(entry);
    save_pending_deletions(&entries)
}
fn clear_pending_deletion(workspace_id: &str, staging: &Path) -> Result<(), String> {
    let mut entries = load_pending_deletions()?;
    entries.retain(|candidate| !(candidate.workspace_id == workspace_id && Path::new(&candidate.staging_path) == staging));
    save_pending_deletions(&entries)
}

fn recover_pending_deletions() -> Result<(), String> {
    let pending = load_pending_deletions()?;
    if pending.is_empty() { return Ok(()); }
    let mut registry = load_registry()?;
    let mut remaining = Vec::new();
    let mut registry_changed = false;
    for entry in pending {
        let original = PathBuf::from(&entry.original_path);
        let staging = PathBuf::from(&entry.staging_path);
        if !is_safe_deletion_staging(&original, &staging) { remaining.push(entry); continue; }
        if staging.exists() {
            if fs::remove_dir_all(&staging).is_err() { remaining.push(entry); continue; }
            registry.servers.retain(|candidate| candidate.id != entry.workspace_id);
            registry_changed = true;
        } else if original.exists() {
        } else {
            registry.servers.retain(|candidate| candidate.id != entry.workspace_id);
            registry_changed = true;
        }
    }
    if registry_changed { save_registry(&registry)?; }
    save_pending_deletions(&remaining)
}

fn is_safe_deletion_staging(original: &Path, staging: &Path) -> bool {
    let Some(original_parent) = original.parent() else { return false; };
    let Some(staging_parent) = staging.parent() else { return false; };
    if original_parent != staging_parent { return false; }
    let Some(base) = original.file_name().and_then(|value| value.to_str()) else { return false; };
    let Some(name) = staging.file_name().and_then(|value| value.to_str()) else { return false; };
    let prefix = format!(".{base}.lazybuilder-deleting-");
    name.strip_prefix(&prefix).is_some_and(|suffix| !suffix.is_empty())
}

fn remove_owned_duplicate_staging(parent: &Path, staging: &Path) -> Result<(), String> {
    if !staging.exists() { return Ok(()); }
    if staging.parent() != Some(parent) || !staging.file_name().and_then(|value| value.to_str()).is_some_and(|name| name.starts_with(".lazybuilder-copying-") && name.len() > ".lazybuilder-copying-".len()) {
        return Err("LazyBuilder refused an unsafe duplicate staging cleanup path".into());
    }
    let metadata = fs::symlink_metadata(staging).map_err(|error| error.to_string())?;
    if is_unsafe_link_or_reparse(staging, &metadata.file_type())? { return Err("LazyBuilder refused a duplicate staging symlink or Windows reparse point".into()); }
    if !metadata.file_type().is_dir() { return Err("Duplicate staging path is not a directory".into()); }
    fs::remove_dir_all(staging).map_err(|error| format!("Could not clean duplicate staging: {error}"))
}

fn reject_existing_reparse_points(paths: &[&Path]) -> Result<(), String> {
    for path in paths {
        if !path.exists() { continue; }
        let metadata = fs::symlink_metadata(path).map_err(|error| error.to_string())?;
        if is_unsafe_link_or_reparse(path, &metadata.file_type())? { return Err(format!("Unsafe symbolic link or Windows reparse point: {}", path.display())); }
    }
    Ok(())
}

fn path_matches(left: &str, right: &str) -> bool { if cfg!(windows) { left.eq_ignore_ascii_case(right) } else { left == right } }

fn provision_layout(root: &Path) -> Result<(), String> {
    let directories = [root.join("server"), root.join("server").join("plugins"), root.join("world-system").join("worlds"), root.join("world-system").join("imports"), root.join("world-system").join("exports"), root.join("world-system").join("backups"), root.join("world-system").join("work"), root.join("tools").join("lazybuilder").join("config"), root.join("tools").join("lazybuilder").join("cache"), root.join("tools").join("lazybuilder").join("logs"), root.join("tools").join("lazybuilder").join("disabled-plugins"), root.join("tools").join("lazybuilder").join("plugin-backups")];
    for directory in directories { fs::create_dir_all(directory).map_err(|error| error.to_string())?; }
    Ok(())
}
fn manifest_path(root: &Path) -> PathBuf { root.join("tools").join("lazybuilder").join("config").join("workspace.json") }
fn write_manifest(root: &Path, manifest: WorkspaceManifest) -> Result<(), String> {
    let path = manifest_path(root);
    let text = serde_json::to_string_pretty(&manifest).map_err(|error| error.to_string())?;
    write_json_file(&path, text.as_bytes(), "workspace manifest")
}
fn read_manifest(root: &Path) -> Result<Option<WorkspaceManifest>, String> {
    let path = manifest_path(root);
    recover_json_file(&path, "workspace manifest")?;
    if !metadata_entry_exists(&path, "workspace manifest")? { return Ok(None); }
    ensure_regular_metadata_file(&path, "workspace manifest")?;
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    Ok(Some(serde_json::from_str(&text).map_err(|error| error.to_string())?))
}
fn validate_manifest(manifest: &WorkspaceManifest) -> Result<(), String> {
    if manifest.schema_version != WORKSPACE_SCHEMA_VERSION { return Err("Workspace manifest schema is newer or unsupported".into()); }
    if manifest.minecraft_version != MINECRAFT_VERSION { return Err(format!("This LazyBuilder build currently supports Minecraft {MINECRAFT_VERSION}; workspace targets {}.", manifest.minecraft_version)); }
    if !manifest.server_platform.eq_ignore_ascii_case(SERVER_PLATFORM) { return Err("This LazyBuilder build currently supports Paper workspaces only".into()); }
    Ok(())
}
fn looks_like_legacy_lazybuilder_workspace(root: &Path) -> bool { root.join("server").is_dir() && root.join("world-system").is_dir() && root.join("tools").join("lazybuilder").is_dir() }
fn contains_plugin_prefix(directory: &Path, prefix: &str) -> Result<bool, String> {
    if !directory.is_dir() { return Ok(false); }
    for entry in fs::read_dir(directory).map_err(|error| error.to_string())? {
        let entry = entry.map_err(|error| error.to_string())?;
        if !entry.file_type().map_err(|error| error.to_string())?.is_file() { continue; }
        let name = entry.file_name().to_string_lossy().to_string();
        if name.starts_with(prefix) && name.to_ascii_lowercase().ends_with(".jar") { return Ok(true); }
    }
    Ok(false)
}
fn read_eula(root: &Path) -> Result<bool, String> {
    let path = root.join("server").join("eula.txt");
    if !path.is_file() { return Ok(false); }
    let text = fs::read_to_string(path).map_err(|error| error.to_string())?;
    Ok(text.lines().any(|line| line.trim().eq_ignore_ascii_case("eula=true")))
}

fn copy_directory_filtered(source: &Path, destination: &Path, relative: &Path) -> Result<(), String> {
    for entry in fs::read_dir(source).map_err(|error| format!("Could not read {}: {error}", source.display()))? {
        let entry = entry.map_err(|error| error.to_string())?;
        let file_type = entry.file_type().map_err(|error| error.to_string())?;
        let name = entry.file_name();
        let rel = relative.join(&name);
        if should_skip_duplicate_path(&rel) { continue; }
        if is_unsafe_link_or_reparse(&entry.path(), &file_type)? { return Err(format!("Cannot safely duplicate a server containing a symbolic link or Windows reparse point: {}", entry.path().display())); }
        let target = destination.join(&name);
        if file_type.is_dir() { fs::create_dir(&target).map_err(|error| format!("Could not create {}: {error}", target.display()))?; copy_directory_filtered(&entry.path(), &target, &rel)?; }
        else if file_type.is_file() { fs::copy(entry.path(), &target).map_err(|error| format!("Could not copy {}: {error}", entry.path().display()))?; }
    }
    Ok(())
}
fn directory_size_filtered(root: &Path, relative: &Path) -> Result<u64, String> {
    let mut total = 0u64;
    for entry in fs::read_dir(root).map_err(|error| format!("Could not inspect {}: {error}", root.display()))? {
        let entry = entry.map_err(|error| error.to_string())?;
        let file_type = entry.file_type().map_err(|error| error.to_string())?;
        let rel = relative.join(entry.file_name());
        if should_skip_duplicate_path(&rel) { continue; }
        if is_unsafe_link_or_reparse(&entry.path(), &file_type)? { return Err(format!("Cannot safely duplicate a server containing a symbolic link or Windows reparse point: {}", entry.path().display())); }
        if file_type.is_dir() { total = total.saturating_add(directory_size_filtered(&entry.path(), &rel)?); }
        else if file_type.is_file() { total = total.saturating_add(entry.metadata().map_err(|error| error.to_string())?.len()); }
    }
    Ok(total)
}
fn is_unsafe_link_or_reparse(path: &Path, file_type: &fs::FileType) -> Result<bool, String> {
    if file_type.is_symlink() { return Ok(true); }
    #[cfg(windows)] { let metadata = fs::symlink_metadata(path).map_err(|error| error.to_string())?; return Ok(metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0); }
    #[cfg(not(windows))] { let _ = path; Ok(false) }
}
fn should_skip_duplicate_path(relative: &Path) -> bool {
    let normalized = relative.to_string_lossy().replace('\\', "/").to_ascii_lowercase();
    if normalized == "world-system/work" || normalized.starts_with("world-system/work/") { return true; }
    if normalized == "tools/lazybuilder/cache" || normalized.starts_with("tools/lazybuilder/cache/") { return true; }
    if normalized == "tools/lazybuilder/logs" || normalized.starts_with("tools/lazybuilder/logs/") { return true; }
    let file_name = relative.file_name().and_then(|value| value.to_str()).unwrap_or("").to_ascii_lowercase();
    file_name.ends_with(".tmp") || file_name.ends_with(".incoming") || file_name.ends_with(".previous") || file_name.ends_with(".lock") || file_name == "server-process.json" || file_name == "server-start.lock"
}
fn available_space_for(path: &Path) -> Option<u64> {
    let disks = Disks::new_with_refreshed_list();
    disks.list().iter().filter(|disk| path.starts_with(disk.mount_point())).max_by_key(|disk| disk.mount_point().as_os_str().len()).map(|disk| disk.available_space())
}
fn bytes_to_mb(bytes: u64) -> u64 { bytes.saturating_add(1024 * 1024 - 1) / (1024 * 1024) }
fn validate_workspace_name(value: &str) -> Result<String, String> {
    let trimmed = value.trim();
    if trimmed.is_empty() { return Err("Server name is required".into()); }
    if trimmed.chars().any(|character| character.is_control() || "<>:\"/\\|?*".contains(character)) { return Err("Server name contains characters that are not valid in a Windows folder name".into()); }
    if trimmed.ends_with('.') || trimmed.ends_with(' ') { return Err("Server name may not end with a dot or space".into()); }
    Ok(trimmed.to_string())
}
fn validate_display_name(value: &str) -> Result<String, String> {
    let trimmed = value.trim();
    if trimmed.is_empty() { return Err("Workspace display name is required".into()); }
    if trimmed.chars().any(char::is_control) { return Err("Workspace display name contains control characters".into()); }
    if trimmed.chars().count() > 96 { return Err("Workspace display name is too long".into()); }
    Ok(trimmed.to_string())
}
fn workspace_id(path: &str) -> String {
    use sha2::{Digest, Sha256};
    let mut digest = Sha256::new();
    digest.update(path.to_lowercase().as_bytes());
    format!("{:x}", digest.finalize())
}
fn now_unix_seconds() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).map(|duration| duration.as_secs()).unwrap_or(0) }
fn now_unix_millis() -> u128 { SystemTime::now().duration_since(UNIX_EPOCH).map(|duration| duration.as_millis()).unwrap_or(0) }

#[cfg(test)]
mod tests {
    use super::*;

    fn temp_metadata_path(name: &str) -> PathBuf {
        let directory = std::env::temp_dir().join(format!("lazybuilder-workspace-metadata-test-{}-{name}-{}", std::process::id(), now_unix_millis()));
        fs::create_dir_all(&directory).unwrap();
        directory.join("metadata.json")
    }

    #[test] fn duplicate_filter_excludes_transient_runtime_paths() {
        assert!(should_skip_duplicate_path(Path::new("world-system/work/job.tmp")));
        assert!(should_skip_duplicate_path(Path::new("tools/lazybuilder/logs/launcher.log")));
        assert!(should_skip_duplicate_path(Path::new("tools/lazybuilder/cache/runtime.bin")));
        assert!(should_skip_duplicate_path(Path::new("server/server-start.lock")));
        assert!(!should_skip_duplicate_path(Path::new("server/plugins/Example/config.yml")));
        assert!(!should_skip_duplicate_path(Path::new("world-system/worlds/build/region/r.0.0.mca")));
    }
    #[test] fn duplicate_destination_guard_rejects_source_and_descendants() {
        let source = Path::new("D:/Servers/Build");
        assert!(source == Path::new("D:/Servers/Build"));
        assert!(Path::new("D:/Servers/Build/Copies").starts_with(source));
        assert!(!Path::new("D:/Servers").starts_with(source));
    }
    #[test] fn deletion_staging_guard_requires_same_parent_and_workspace_name() {
        let original = Path::new("D:/Servers/Build");
        assert!(is_safe_deletion_staging(original, Path::new("D:/Servers/.Build.lazybuilder-deleting-1-2")));
        assert!(!is_safe_deletion_staging(original, Path::new("D:/Other/.Build.lazybuilder-deleting-1-2")));
        assert!(!is_safe_deletion_staging(original, Path::new("D:/Servers/.Other.lazybuilder-deleting-1-2")));
        assert!(!is_safe_deletion_staging(original, Path::new("D:/Servers/.Build.lazybuilder-deleting-")));
    }
    #[test] fn duplicate_recovery_paths_are_strictly_scoped() {
        let parent = Path::new("D:/Servers");
        assert!(safe_duplicate_paths(parent, Path::new("D:/Servers/.lazybuilder-copying-1-2"), Path::new("D:/Servers/Build Copy"), "Build Copy"));
        assert!(!safe_duplicate_paths(parent, Path::new("D:/Other/.lazybuilder-copying-1-2"), Path::new("D:/Servers/Build Copy"), "Build Copy"));
        assert!(!safe_duplicate_paths(parent, Path::new("D:/Servers/.lazybuilder-copying-1-2"), Path::new("D:/Servers/Other"), "Build Copy"));
    }
    #[test] fn workspace_metadata_recovery_prefers_previous_committed_copy() {
        let path = temp_metadata_path("previous");
        let previous = path.with_extension("json.previous");
        let incoming = path.with_extension("json.incoming");
        fs::write(&previous, b"previous").unwrap();
        fs::write(&incoming, b"incoming").unwrap();
        recover_json_file(&path, "test metadata").unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "previous");
        assert!(!incoming.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
    #[test] fn workspace_metadata_recovery_supports_legacy_tmp_staging() {
        let path = temp_metadata_path("legacy");
        let legacy = path.with_extension("json.tmp");
        fs::write(&legacy, b"legacy-staging").unwrap();
        recover_json_file(&path, "test metadata").unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "legacy-staging");
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
    #[test] fn workspace_metadata_recovery_preserves_ambiguous_staging() {
        let path = temp_metadata_path("ambiguous");
        let incoming = path.with_extension("json.incoming");
        let legacy = path.with_extension("json.tmp");
        fs::write(&incoming, b"incoming").unwrap();
        fs::write(&legacy, b"legacy").unwrap();
        assert!(recover_json_file(&path, "test metadata").is_err());
        assert!(incoming.exists());
        assert!(legacy.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
}
