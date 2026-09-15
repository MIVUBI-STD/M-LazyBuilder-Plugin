use crate::engine::{server_backups, workspace_registry};
use serde::{Deserialize, Serialize};
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use sysinfo::Disks;

const MIN_RESTORE_HEADROOM_BYTES: u64 = 512 * 1024 * 1024;
static NEXT_RESTORE_ID: AtomicU64 = AtomicU64::new(1);

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerRestoreResult {
    pub restored_backup_id: String,
    pub safety_backup: server_backups::ServerBackupSummary,
    pub cleanup_pending: bool,
}

#[derive(Clone, Debug)]
pub struct RestoreFailure {
    pub message: String,
    pub recovery_required: bool,
}

impl RestoreFailure {
    fn failed(message: impl Into<String>) -> Self {
        Self { message: message.into(), recovery_required: false }
    }

    fn recovery(message: impl Into<String>) -> Self {
        Self { message: message.into(), recovery_required: true }
    }
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RestoreRecoveryReport {
    pub recovered: u32,
    pub issues: Vec<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PendingRestore {
    workspace_id: String,
    backup_id: String,
    original_path: String,
    staging_path: String,
    rollback_path: String,
}

pub fn restore_tracked<F>(
    workspace_id: &str,
    backup_id: &str,
    mut progress: F,
) -> Result<ServerRestoreResult, RestoreFailure>
where
    F: FnMut(&str, &str, &str, Option<(u64, u64)>),
{
    validate_backup_id(backup_id).map_err(RestoreFailure::failed)?;
    ensure_no_pending_restore(workspace_id).map_err(RestoreFailure::recovery)?;

    let entry = workspace_registry::get(workspace_id).map_err(RestoreFailure::failed)?;
    let root = PathBuf::from(&entry.path)
        .canonicalize()
        .map_err(|error| RestoreFailure::failed(format!("Could not resolve server workspace before restore: {error}")))?;
    reject_reparse_point(&root).map_err(RestoreFailure::failed)?;
    if !workspace_identity_matches(&root, workspace_id).map_err(RestoreFailure::failed)? {
        return Err(RestoreFailure::failed("Registered server identity does not match its workspace manifest"));
    }

    progress("validating", "Validating restore point", "Checking backup identity and compatibility before changing the current server.", None);
    let backup = server_backups::list(workspace_id)
        .map_err(RestoreFailure::failed)?
        .into_iter()
        .find(|item| item.id == backup_id)
        .ok_or_else(|| RestoreFailure::failed("Server backup was not found or failed validation"))?;
    let backup_root = PathBuf::from(&backup.path);
    reject_reparse_point(&backup_root).map_err(RestoreFailure::failed)?;
    let snapshot = backup_root.join("workspace");
    reject_reparse_point(&snapshot).map_err(RestoreFailure::failed)?;
    if !workspace_identity_matches(&snapshot, workspace_id).map_err(RestoreFailure::failed)? {
        return Err(RestoreFailure::failed("Restore point workspace identity does not match the selected server"));
    }

    progress("safety-backup", "Creating pre-restore safety backup", "Creating a full restore point of the current server before replacement.", None);
    let safety_backup = server_backups::create_tracked(workspace_id, |phase, _status, details, measured| {
        let status = match phase {
            "preflight" => "Checking safety-backup storage",
            "copying" => "Copying current server to safety backup",
            "validating" => "Validating safety backup",
            "publishing" => "Publishing safety backup",
            _ => "Preparing safety backup",
        };
        progress("safety-backup", status, details, measured);
    })
    .map_err(|error| RestoreFailure::failed(format!("Could not create the required pre-restore safety backup: {error}")))?;

    let parent = root.parent().ok_or_else(|| RestoreFailure::failed("Server workspace has no parent directory for restore staging"))?;
    let available = available_space_for(parent);
    let required = backup.source_bytes.saturating_add(MIN_RESTORE_HEADROOM_BYTES.max(backup.source_bytes / 10));
    if let Some(available) = available {
        if available < required {
            return Err(RestoreFailure::failed(format!(
                "Not enough storage to stage this restore. Required approximately {} MB after the safety backup; available {} MB.",
                bytes_to_mb(required),
                bytes_to_mb(available)
            )));
        }
    }

    let base = root.file_name().and_then(|value| value.to_str()).unwrap_or("server");
    let sequence = NEXT_RESTORE_ID.fetch_add(1, Ordering::Relaxed);
    let token = format!("{}-{sequence}", std::process::id());
    let staging = parent.join(format!(".{base}.lazybuilder-restoring-{token}"));
    let rollback = parent.join(format!(".{base}.lazybuilder-restore-rollback-{token}"));
    if staging.exists() || rollback.exists() {
        return Err(RestoreFailure::failed("LazyBuilder restore staging already exists; retry in a moment"));
    }

    progress("staging", "Staging restore point", "Copying the selected restore point into a validated staging workspace.", Some((0, backup.source_bytes)));
    fs::create_dir(&staging)
        .map_err(|error| RestoreFailure::failed(format!("Could not create restore staging directory: {error}")))?;
    if let Err(error) = copy_tree(&snapshot, &staging, Path::new(""), backup.source_bytes, 0, &mut progress) {
        let _ = fs::remove_dir_all(&staging);
        return Err(RestoreFailure::failed(error));
    }
    match workspace_identity_matches(&staging, workspace_id) {
        Ok(true) => {}
        Ok(false) => {
            let _ = fs::remove_dir_all(&staging);
            return Err(RestoreFailure::failed("Staged restore validation failed because workspace identity changed"));
        }
        Err(error) => {
            let _ = fs::remove_dir_all(&staging);
            return Err(RestoreFailure::failed(error));
        }
    }

    let intent = PendingRestore {
        workspace_id: workspace_id.to_string(),
        backup_id: backup_id.to_string(),
        original_path: root.display().to_string(),
        staging_path: staging.display().to_string(),
        rollback_path: rollback.display().to_string(),
    };
    if let Err(error) = add_pending_restore(intent.clone()) {
        let _ = fs::remove_dir_all(&staging);
        return Err(RestoreFailure::recovery(format!("Could not persist server restore recovery intent: {error}")));
    }

    progress("committing", "Swapping restored server into place", "The current workspace is being moved to rollback staging before the restored workspace is published.", None);
    if let Err(error) = fs::rename(&root, &rollback) {
        let _ = fs::remove_dir_all(&staging);
        let clear_result = clear_pending_restore(workspace_id, &rollback);
        if let Err(clear_error) = clear_result {
            return Err(RestoreFailure::recovery(format!(
                "Could not move the current server into restore rollback staging ({error}) and recovery intent cleanup also failed ({clear_error})."
            )));
        }
        return Err(RestoreFailure::failed(format!("Could not move the current server into restore rollback staging: {error}")));
    }

    if let Err(publish_error) = fs::rename(&staging, &root) {
        match fs::rename(&rollback, &root) {
            Ok(()) => {
                let cleanup_staging = remove_owned_restore_path(&staging);
                let clear_intent = clear_pending_restore(workspace_id, &rollback);
                if cleanup_staging.is_err() || clear_intent.is_err() {
                    return Err(RestoreFailure::recovery(format!(
                        "Could not publish the restored server ({publish_error}). The original server was restored, but restore cleanup still requires reconciliation."
                    )));
                }
                return Err(RestoreFailure::failed(format!("Could not publish the restored server; the original server was restored automatically: {publish_error}")));
            }
            Err(rollback_error) => {
                return Err(RestoreFailure::recovery(format!(
                    "Could not publish the restored server ({publish_error}) and automatic rollback also failed ({rollback_error}). LazyBuilder preserved restore recovery metadata for startup reconciliation."
                )));
            }
        }
    }

    if !workspace_identity_matches(&root, workspace_id).unwrap_or(false) {
        return Err(RestoreFailure::recovery(
            "The restored workspace was published but failed identity verification. The previous workspace and recovery metadata were preserved for safe reconciliation.",
        ));
    }

    progress("verifying", "Verifying restored server", "The selected restore point is active and its workspace identity is valid.", None);
    let rollback_cleanup = remove_owned_restore_path(&rollback);
    let intent_cleanup = if rollback_cleanup.is_ok() {
        clear_pending_restore(workspace_id, &rollback)
    } else {
        Ok(())
    };
    let cleanup_pending = rollback_cleanup.is_err() || intent_cleanup.is_err();

    Ok(ServerRestoreResult {
        restored_backup_id: backup_id.to_string(),
        safety_backup,
        cleanup_pending,
    })
}

pub fn recover_pending_restores() -> Result<RestoreRecoveryReport, String> {
    let pending = load_pending_restores()?;
    if pending.is_empty() {
        return Ok(RestoreRecoveryReport { recovered: 0, issues: Vec::new() });
    }

    let mut remaining = Vec::new();
    let mut recovered = 0u32;
    let mut issues = Vec::new();

    for intent in pending {
        let original = PathBuf::from(&intent.original_path);
        let staging = PathBuf::from(&intent.staging_path);
        let rollback = PathBuf::from(&intent.rollback_path);
        let entry = match workspace_registry::get(&intent.workspace_id) {
            Ok(entry) => entry,
            Err(error) => {
                issues.push(format!("Restore recovery for {} could not resolve its server library entry: {error}", intent.workspace_id));
                remaining.push(intent);
                continue;
            }
        };

        if !path_matches(&entry.path, &intent.original_path) || !safe_restore_paths(&original, &staging, &rollback) {
            issues.push(format!("Restore recovery for {} has unsafe or mismatched paths and was preserved for manual recovery.", entry.name));
            remaining.push(intent);
            continue;
        }

        if let Err(error) = reject_existing_reparse_points(&[&original, &staging, &rollback]) {
            issues.push(format!("Restore recovery for {} was blocked by filesystem safety validation: {error}", entry.name));
            remaining.push(intent);
            continue;
        }

        if original.is_dir() && workspace_identity_matches(&original, &intent.workspace_id).unwrap_or(false) {
            let staging_cleanup = remove_owned_restore_path(&staging);
            let rollback_cleanup = remove_owned_restore_path(&rollback);
            if staging_cleanup.is_err() || rollback_cleanup.is_err() {
                issues.push(format!("Restore recovery for {} completed but cleanup is still pending.", entry.name));
                remaining.push(intent);
            } else {
                recovered = recovered.saturating_add(1);
            }
            continue;
        }

        if !original.exists() && rollback.is_dir() && workspace_identity_matches(&rollback, &intent.workspace_id).unwrap_or(false) {
            match fs::rename(&rollback, &original) {
                Ok(()) if workspace_identity_matches(&original, &intent.workspace_id).unwrap_or(false) => {
                    match remove_owned_restore_path(&staging) {
                        Ok(()) => recovered = recovered.saturating_add(1),
                        Err(error) => {
                            issues.push(format!("Restore recovery for {} restored the preserved original workspace, but staging cleanup is still pending: {error}", entry.name));
                            remaining.push(intent);
                        }
                    }
                }
                Ok(()) => {
                    issues.push(format!("Restore recovery for {} restored rollback staging but workspace identity still needs attention.", entry.name));
                    remaining.push(intent);
                }
                Err(error) => {
                    issues.push(format!("Restore recovery for {} could not restore the preserved original workspace: {error}", entry.name));
                    remaining.push(intent);
                }
            }
            continue;
        }

        issues.push(format!("Restore recovery for {} is ambiguous; LazyBuilder preserved all restore paths for manual recovery.", entry.name));
        remaining.push(intent);
    }

    save_pending_restores(&remaining)?;
    Ok(RestoreRecoveryReport { recovered, issues })
}

fn pending_restores_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("pending-restores.json"))
}

fn load_pending_restores() -> Result<Vec<PendingRestore>, String> {
    let path = pending_restores_path()?;
    if !path.is_file() {
        return Ok(Vec::new());
    }
    let text = fs::read_to_string(path).map_err(|error| format!("Could not read pending server restores: {error}"))?;
    serde_json::from_str(&text).map_err(|error| format!("Could not parse pending server restores: {error}"))
}

fn save_pending_restores(entries: &[PendingRestore]) -> Result<(), String> {
    let path = pending_restores_path()?;
    if entries.is_empty() {
        if path.exists() {
            fs::remove_file(path).map_err(|error| error.to_string())?;
        }
        return Ok(());
    }
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| error.to_string())?;
    }
    let incoming = path.with_extension("json.incoming");
    let backup = path.with_extension("json.previous");
    let text = serde_json::to_string_pretty(entries).map_err(|error| error.to_string())?;
    let mut file = fs::File::create(&incoming).map_err(|error| error.to_string())?;
    file.write_all(text.as_bytes()).map_err(|error| error.to_string())?;
    file.sync_all().map_err(|error| error.to_string())?;
    drop(file);

    if path.exists() {
        let _ = fs::remove_file(&backup);
        fs::rename(&path, &backup).map_err(|error| error.to_string())?;
        match fs::rename(&incoming, &path) {
            Ok(()) => {
                let _ = fs::remove_file(backup);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&backup, &path);
                Err(error.to_string())
            }
        }
    } else {
        fs::rename(incoming, path).map_err(|error| error.to_string())
    }
}

fn ensure_no_pending_restore(workspace_id: &str) -> Result<(), String> {
    if load_pending_restores()?.iter().any(|item| item.workspace_id == workspace_id) {
        Err("A previous restore for this server still requires recovery. Restart LazyBuilder and resolve that restore before starting another one.".into())
    } else {
        Ok(())
    }
}

fn add_pending_restore(intent: PendingRestore) -> Result<(), String> {
    let mut entries = load_pending_restores()?;
    if entries.iter().any(|item| item.workspace_id == intent.workspace_id) {
        return Err("A previous restore for this server still requires recovery; LazyBuilder will not overwrite its recovery intent.".into());
    }
    entries.push(intent);
    save_pending_restores(&entries)
}

fn clear_pending_restore(workspace_id: &str, rollback: &Path) -> Result<(), String> {
    let mut entries = load_pending_restores()?;
    entries.retain(|item| !(item.workspace_id == workspace_id && Path::new(&item.rollback_path) == rollback));
    save_pending_restores(&entries)
}

fn copy_tree<F>(source: &Path, destination: &Path, relative: &Path, total: u64, mut copied: u64, progress: &mut F) -> Result<u64, String>
where
    F: FnMut(&str, &str, &str, Option<(u64, u64)>),
{
    for item in fs::read_dir(source).map_err(|error| format!("Could not read restore point {}: {error}", source.display()))? {
        let item = item.map_err(|error| error.to_string())?;
        let metadata = fs::symlink_metadata(item.path()).map_err(|error| error.to_string())?;
        if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
            return Err(format!("Cannot safely restore a backup containing a symbolic link or Windows reparse point: {}", item.path().display()));
        }
        let rel = relative.join(item.file_name());
        let target = destination.join(item.file_name());
        if metadata.file_type().is_dir() {
            fs::create_dir(&target).map_err(|error| format!("Could not create restore directory {}: {error}", target.display()))?;
            copied = copy_tree(&item.path(), &target, &rel, total, copied, progress)?;
        } else if metadata.file_type().is_file() {
            fs::copy(item.path(), &target).map_err(|error| format!("Could not restore {}: {error}", rel.display()))?;
            copied = copied.saturating_add(metadata.len());
            progress("staging", "Staging restore point", &rel.to_string_lossy(), Some((copied.min(total), total)));
        }
    }
    Ok(copied)
}

fn workspace_identity_matches(root: &Path, workspace_id: &str) -> Result<bool, String> {
    let manifest = root.join("tools").join("lazybuilder").join("config").join("workspace.json");
    let text = fs::read_to_string(manifest).map_err(|error| format!("Could not read workspace manifest: {error}"))?;
    let value: serde_json::Value = serde_json::from_str(&text).map_err(|error| format!("Could not parse workspace manifest: {error}"))?;
    Ok(value.get("workspaceId").and_then(serde_json::Value::as_str) == Some(workspace_id))
}

fn safe_restore_paths(original: &Path, staging: &Path, rollback: &Path) -> bool {
    let Some(parent) = original.parent() else { return false; };
    if staging.parent() != Some(parent) || rollback.parent() != Some(parent) {
        return false;
    }
    let Some(base) = original.file_name().and_then(|value| value.to_str()) else { return false; };
    let staging_prefix = format!(".{base}.lazybuilder-restoring-");
    let rollback_prefix = format!(".{base}.lazybuilder-restore-rollback-");
    staging.file_name().and_then(|value| value.to_str()).is_some_and(|name| name.starts_with(&staging_prefix) && name.len() > staging_prefix.len())
        && rollback.file_name().and_then(|value| value.to_str()).is_some_and(|name| name.starts_with(&rollback_prefix) && name.len() > rollback_prefix.len())
}

fn path_matches(left: &str, right: &str) -> bool {
    if cfg!(windows) { left.eq_ignore_ascii_case(right) } else { left == right }
}

fn remove_owned_restore_path(path: &Path) -> Result<(), String> {
    if !path.exists() {
        return Ok(());
    }
    reject_reparse_point(path)?;
    if !path.is_dir() {
        return Err(format!("Restore-owned path is not a directory: {}", path.display()));
    }
    fs::remove_dir_all(path).map_err(|error| error.to_string())
}

fn reject_existing_reparse_points(paths: &[&Path]) -> Result<(), String> {
    for path in paths {
        if path.exists() {
            reject_reparse_point(path)?;
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

fn bytes_to_mb(bytes: u64) -> u64 {
    bytes.saturating_add(1024 * 1024 - 1) / (1024 * 1024)
}

#[cfg(windows)]
fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0400;
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}

#[cfg(not(windows))]
fn is_reparse_point(_metadata: &fs::Metadata) -> bool { false }

fn reject_reparse_point(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|error| error.to_string())?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        Err(format!("LazyBuilder refused a symbolic link or Windows reparse point during restore: {}", path.display()))
    } else {
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn restore_paths_must_share_the_workspace_parent_and_owned_prefixes() {
        let root = Path::new("D:/Servers/Build");
        assert!(safe_restore_paths(
            root,
            Path::new("D:/Servers/.Build.lazybuilder-restoring-1-2"),
            Path::new("D:/Servers/.Build.lazybuilder-restore-rollback-1-2"),
        ));
        assert!(!safe_restore_paths(
            root,
            Path::new("D:/Other/.Build.lazybuilder-restoring-1-2"),
            Path::new("D:/Servers/.Build.lazybuilder-restore-rollback-1-2"),
        ));
    }

    #[test]
    fn backup_id_validation_rejects_path_traversal() {
        assert!(validate_backup_id("backup-123-4-1").is_ok());
        assert!(validate_backup_id("../backup-123").is_err());
    }
}
