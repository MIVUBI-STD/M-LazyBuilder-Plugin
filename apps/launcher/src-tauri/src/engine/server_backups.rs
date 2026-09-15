use crate::engine::workspace_registry;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};
use sysinfo::Disks;

const BACKUP_SCHEMA_VERSION: u32 = 1;
const MIN_BACKUP_HEADROOM_BYTES: u64 = 512 * 1024 * 1024;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerBackupSummary {
    pub id: String,
    pub workspace_id: String,
    pub workspace_name: String,
    pub path: String,
    pub created_unix_seconds: u64,
    pub source_bytes: u64,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerBackupEstimate {
    pub source_bytes: u64,
    pub required_bytes: u64,
    pub available_bytes: Option<u64>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct BackupManifest {
    schema_version: u32,
    id: String,
    workspace_id: String,
    workspace_name: String,
    created_unix_seconds: u64,
    source_bytes: u64,
}

pub fn list(workspace_id: &str) -> Result<Vec<ServerBackupSummary>, String> {
    let entry = workspace_registry::get(workspace_id)?;
    let root = validated_workspace_root(&entry)?;
    let backup_root = backup_root_for(&root, workspace_id)?;
    cleanup_stale_staging(&backup_root)?;
    if !backup_root.is_dir() {
        return Ok(Vec::new());
    }

    let mut backups = Vec::new();
    for item in fs::read_dir(&backup_root).map_err(|error| format!("Could not read server backups: {error}"))? {
        let item = item.map_err(|error| error.to_string())?;
        let file_type = item.file_type().map_err(|error| error.to_string())?;
        if !file_type.is_dir() || is_reparse_point(&item.metadata().map_err(|error| error.to_string())?) {
            continue;
        }
        let backup_path = item.path();
        let manifest = match read_backup_manifest(&backup_path) {
            Ok(value) => value,
            Err(_) => continue,
        };
        if manifest.workspace_id != workspace_id || manifest.schema_version != BACKUP_SCHEMA_VERSION {
            continue;
        }
        let snapshot = backup_path.join("workspace");
        if !snapshot.is_dir() || !backup_snapshot_identity_matches(&snapshot, workspace_id)? {
            continue;
        }
        backups.push(summary_from_manifest(&backup_path, manifest));
    }
    backups.sort_by(|left, right| right.created_unix_seconds.cmp(&left.created_unix_seconds));
    Ok(backups)
}

pub fn estimate(workspace_id: &str) -> Result<ServerBackupEstimate, String> {
    let entry = workspace_registry::get(workspace_id)?;
    let source = validated_workspace_root(&entry)?;
    let destination = backup_root_for(&source, workspace_id)?;
    if let Some(parent) = destination.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    fs::create_dir_all(&destination).map_err(|error| format!("Could not prepare backup storage: {error}"))?;
    let source_bytes = directory_size_filtered(&source, Path::new(""))?;
    let margin = (source_bytes / 10).max(MIN_BACKUP_HEADROOM_BYTES);
    let required_bytes = source_bytes.saturating_add(margin);
    let available_bytes = available_space_for(&destination);
    Ok(ServerBackupEstimate { source_bytes, required_bytes, available_bytes })
}

pub fn create_tracked<F>(workspace_id: &str, mut progress: F) -> Result<ServerBackupSummary, String>
where
    F: FnMut(&str, &str, &str, Option<(u64, u64)>),
{
    let entry = workspace_registry::get(workspace_id)?;
    let source = validated_workspace_root(&entry)?;
    let backup_root = backup_root_for(&source, workspace_id)?;
    fs::create_dir_all(&backup_root).map_err(|error| format!("Could not create backup storage: {error}"))?;
    cleanup_stale_staging(&backup_root)?;

    progress("preflight", "Checking backup storage", "Calculating server size and available disk space.", None);
    let source_bytes = directory_size_filtered(&source, Path::new(""))?;
    let margin = (source_bytes / 10).max(MIN_BACKUP_HEADROOM_BYTES);
    let required_bytes = source_bytes.saturating_add(margin);
    if let Some(available) = available_space_for(&backup_root) {
        if available < required_bytes {
            return Err(format!(
                "Not enough storage to back up this server. Required approximately {} MB; available {} MB.",
                bytes_to_mb(required_bytes),
                bytes_to_mb(available)
            ));
        }
    }

    let created = now_unix_seconds();
    let id = format!("backup-{}-{}", now_unix_millis(), std::process::id());
    let staging = backup_root.join(format!(".creating-{id}"));
    let final_root = backup_root.join(&id);
    if staging.exists() || final_root.exists() {
        return Err("LazyBuilder backup staging path already exists; retry in a moment".into());
    }
    let snapshot = staging.join("workspace");
    fs::create_dir_all(&snapshot).map_err(|error| format!("Could not create backup staging directory: {error}"))?;

    let copy_result = copy_directory_filtered(&source, &snapshot, Path::new(""), source_bytes, &mut progress);
    if let Err(error) = copy_result {
        let _ = fs::remove_dir_all(&staging);
        return Err(error);
    }

    progress("validating", "Validating backup", "Verifying the staged workspace identity before publishing the restore point.", None);
    if !backup_snapshot_identity_matches(&snapshot, workspace_id)? {
        let _ = fs::remove_dir_all(&staging);
        return Err("Backup validation failed because the staged workspace identity does not match the source server".into());
    }

    let manifest = BackupManifest {
        schema_version: BACKUP_SCHEMA_VERSION,
        id: id.clone(),
        workspace_id: workspace_id.to_string(),
        workspace_name: entry.name.clone(),
        created_unix_seconds: created,
        source_bytes,
    };
    write_backup_manifest(&staging, &manifest)?;

    progress("publishing", "Publishing backup", "Publishing the validated restore point atomically.", Some((source_bytes, source_bytes)));
    if let Err(error) = fs::rename(&staging, &final_root) {
        let _ = fs::remove_dir_all(&staging);
        return Err(format!("Could not publish server backup: {error}"));
    }

    Ok(summary_from_manifest(&final_root, manifest))
}

pub fn delete(workspace_id: &str, backup_id: &str) -> Result<(), String> {
    validate_backup_id(backup_id)?;
    let entry = workspace_registry::get(workspace_id)?;
    let root = validated_workspace_root(&entry)?;
    let backup_root = backup_root_for(&root, workspace_id)?;
    let target = backup_root.join(backup_id);
    if !target.is_dir() {
        return Err("Server backup was not found".into());
    }
    let manifest = read_backup_manifest(&target)?;
    if manifest.schema_version != BACKUP_SCHEMA_VERSION || manifest.workspace_id != workspace_id || manifest.id != backup_id {
        return Err("LazyBuilder refused to delete a backup whose identity does not match the selected server".into());
    }
    let canonical_root = backup_root.canonicalize().map_err(|error| error.to_string())?;
    let canonical_target = target.canonicalize().map_err(|error| error.to_string())?;
    if canonical_target.parent() != Some(canonical_root.as_path()) {
        return Err("LazyBuilder refused an unsafe backup deletion path".into());
    }
    reject_reparse_point(&canonical_target)?;
    fs::remove_dir_all(canonical_target).map_err(|error| format!("Could not delete server backup: {error}"))
}

fn backup_root_for(workspace: &Path, workspace_id: &str) -> Result<PathBuf, String> {
    let parent = workspace.parent().ok_or_else(|| "Server workspace has no parent directory for backup storage".to_string())?;
    Ok(parent.join(".lazybuilder-backups").join(workspace_id))
}

fn validated_workspace_root(entry: &workspace_registry::WorkspaceEntry) -> Result<PathBuf, String> {
    let root = PathBuf::from(&entry.path)
        .canonicalize()
        .map_err(|error| format!("Could not resolve registered server workspace: {error}"))?;
    if !root.is_dir() {
        return Err("Registered server workspace is not a directory".into());
    }
    reject_reparse_point(&root)?;
    if !backup_snapshot_identity_matches(&root, &entry.id)? {
        return Err("Registered server identity does not match its workspace manifest".into());
    }
    Ok(root)
}

fn backup_snapshot_identity_matches(root: &Path, workspace_id: &str) -> Result<bool, String> {
    let path = root.join("tools").join("lazybuilder").join("config").join("workspace.json");
    let text = fs::read_to_string(path).map_err(|error| format!("Could not read workspace manifest: {error}"))?;
    let value: serde_json::Value = serde_json::from_str(&text).map_err(|error| format!("Could not parse workspace manifest: {error}"))?;
    Ok(value.get("workspaceId").and_then(serde_json::Value::as_str) == Some(workspace_id))
}

fn write_backup_manifest(root: &Path, manifest: &BackupManifest) -> Result<(), String> {
    let path = root.join("backup.json");
    let temp = root.join("backup.json.tmp");
    let text = serde_json::to_string_pretty(manifest).map_err(|error| error.to_string())?;
    fs::write(&temp, text).map_err(|error| error.to_string())?;
    fs::rename(temp, path).map_err(|error| error.to_string())
}

fn read_backup_manifest(root: &Path) -> Result<BackupManifest, String> {
    let text = fs::read_to_string(root.join("backup.json")).map_err(|error| format!("Could not read backup metadata: {error}"))?;
    serde_json::from_str(&text).map_err(|error| format!("Could not parse backup metadata: {error}"))
}

fn summary_from_manifest(root: &Path, manifest: BackupManifest) -> ServerBackupSummary {
    ServerBackupSummary {
        id: manifest.id,
        workspace_id: manifest.workspace_id,
        workspace_name: manifest.workspace_name,
        path: root.display().to_string(),
        created_unix_seconds: manifest.created_unix_seconds,
        source_bytes: manifest.source_bytes,
    }
}

fn copy_directory_filtered<F>(source: &Path, destination: &Path, relative: &Path, total: u64, progress: &mut F) -> Result<u64, String>
where
    F: FnMut(&str, &str, &str, Option<(u64, u64)>),
{
    copy_directory_filtered_inner(source, destination, relative, total, 0, progress)
}

fn copy_directory_filtered_inner<F>(source: &Path, destination: &Path, relative: &Path, total: u64, mut copied: u64, progress: &mut F) -> Result<u64, String>
where
    F: FnMut(&str, &str, &str, Option<(u64, u64)>),
{
    for item in fs::read_dir(source).map_err(|error| format!("Could not read {}: {error}", source.display()))? {
        let item = item.map_err(|error| error.to_string())?;
        let metadata = item.metadata().map_err(|error| error.to_string())?;
        let file_type = item.file_type().map_err(|error| error.to_string())?;
        let rel = relative.join(item.file_name());
        if should_skip_backup_path(&rel) {
            continue;
        }
        if file_type.is_symlink() || is_reparse_point(&metadata) {
            return Err(format!("Cannot safely back up a server containing a symbolic link or Windows reparse point: {}", item.path().display()));
        }
        let target = destination.join(item.file_name());
        if file_type.is_dir() {
            fs::create_dir(&target).map_err(|error| format!("Could not create {}: {error}", target.display()))?;
            copied = copy_directory_filtered_inner(&item.path(), &target, &rel, total, copied, progress)?;
        } else if file_type.is_file() {
            let size = metadata.len();
            fs::copy(item.path(), &target).map_err(|error| format!("Could not copy {}: {error}", item.path().display()))?;
            copied = copied.saturating_add(size);
            progress("copying", "Copying server files", &rel.to_string_lossy(), Some((copied.min(total), total)));
        }
    }
    Ok(copied)
}

fn directory_size_filtered(root: &Path, relative: &Path) -> Result<u64, String> {
    let mut total = 0u64;
    for item in fs::read_dir(root).map_err(|error| format!("Could not inspect {}: {error}", root.display()))? {
        let item = item.map_err(|error| error.to_string())?;
        let metadata = item.metadata().map_err(|error| error.to_string())?;
        let file_type = item.file_type().map_err(|error| error.to_string())?;
        let rel = relative.join(item.file_name());
        if should_skip_backup_path(&rel) {
            continue;
        }
        if file_type.is_symlink() || is_reparse_point(&metadata) {
            return Err(format!("Cannot safely back up a server containing a symbolic link or Windows reparse point: {}", item.path().display()));
        }
        if file_type.is_dir() {
            total = total.saturating_add(directory_size_filtered(&item.path(), &rel)?);
        } else if file_type.is_file() {
            total = total.saturating_add(metadata.len());
        }
    }
    Ok(total)
}

fn should_skip_backup_path(relative: &Path) -> bool {
    let normalized = relative.to_string_lossy().replace('\\', "/").to_ascii_lowercase();
    if normalized == "world-system/work" || normalized.starts_with("world-system/work/") { return true; }
    if normalized == "tools/lazybuilder/cache" || normalized.starts_with("tools/lazybuilder/cache/") { return true; }
    if normalized == "tools/lazybuilder/logs" || normalized.starts_with("tools/lazybuilder/logs/") { return true; }
    let file_name = relative.file_name().and_then(|value| value.to_str()).unwrap_or("").to_ascii_lowercase();
    file_name.ends_with(".tmp")
        || file_name.ends_with(".incoming")
        || file_name.ends_with(".lock")
        || file_name == "server-process.json"
        || file_name == "server-start.lock"
}

fn cleanup_stale_staging(root: &Path) -> Result<(), String> {
    if !root.is_dir() {
        return Ok(());
    }
    for item in fs::read_dir(root).map_err(|error| error.to_string())? {
        let item = item.map_err(|error| error.to_string())?;
        let name = item.file_name().to_string_lossy().to_string();
        if !name.starts_with(".creating-backup-") {
            continue;
        }
        let metadata = item.metadata().map_err(|error| error.to_string())?;
        if item.file_type().map_err(|error| error.to_string())?.is_dir() && !is_reparse_point(&metadata) {
            fs::remove_dir_all(item.path()).map_err(|error| format!("Could not clean interrupted backup staging: {error}"))?;
        }
    }
    Ok(())
}

fn validate_backup_id(value: &str) -> Result<(), String> {
    if value.starts_with("backup-") && value.chars().all(|character| character.is_ascii_alphanumeric() || character == '-') {
        Ok(())
    } else {
        Err("Invalid server backup identity".into())
    }
}

fn available_space_for(path: &Path) -> Option<u64> {
    let disks = Disks::new_with_refreshed_list();
    disks.list().iter().filter(|disk| path.starts_with(disk.mount_point())).max_by_key(|disk| disk.mount_point().as_os_str().len()).map(|disk| disk.available_space())
}

fn bytes_to_mb(bytes: u64) -> u64 { bytes.saturating_add(1024 * 1024 - 1) / (1024 * 1024) }
fn now_unix_seconds() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or_default() }
fn now_unix_millis() -> u128 { SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_millis()).unwrap_or_default() }

#[cfg(windows)]
fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0400;
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}

#[cfg(not(windows))]
fn is_reparse_point(_metadata: &fs::Metadata) -> bool { false }

fn reject_reparse_point(path: &Path) -> Result<(), String> {
    let metadata = fs::metadata(path).map_err(|error| error.to_string())?;
    if is_reparse_point(&metadata) {
        Err(format!("LazyBuilder refused a Windows reparse point for backup safety: {}", path.display()))
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn backup_filter_excludes_transient_runtime_paths() {
        assert!(should_skip_backup_path(Path::new("world-system/work/job.tmp")));
        assert!(should_skip_backup_path(Path::new("tools/lazybuilder/cache/server-process.json")));
        assert!(should_skip_backup_path(Path::new("tools/lazybuilder/logs/launcher.log")));
        assert!(!should_skip_backup_path(Path::new("server/plugins/Example/config.yml")));
        assert!(!should_skip_backup_path(Path::new("world-system/worlds/build/region/r.0.0.mca")));
    }

    #[test]
    fn backup_id_validation_is_strict() {
        assert!(validate_backup_id("backup-123-4").is_ok());
        assert!(validate_backup_id("../backup-123").is_err());
        assert!(validate_backup_id("backup_123").is_err());
    }
}
