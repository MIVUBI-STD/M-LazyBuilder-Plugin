use crate::engine::{persistence, workspace_registry};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};

const RECOVERY_SCHEMA_VERSION: u32 = 1;
const LEGACY_SWEEP_SCHEMA_VERSION: u32 = 1;
const STAGING_PREFIX: &str = ".creating-backup-";

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PendingBackupRecovery {
    schema_version: u32,
    workspace_ids: Vec<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct LegacySweepMarker {
    schema_version: u32,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupRecoveryReport {
    pub recovered_workspaces: u32,
    pub removed_staging: u32,
    pub issues: Vec<String>,
}

pub fn begin(workspace_id: &str) -> Result<(), String> {
    let workspace_id = workspace_id.trim();
    if workspace_id.is_empty() {
        return Err("Backup recovery workspace identity is required".into());
    }
    let mut pending = load_pending()?;
    if pending.workspace_ids.iter().any(|item| item == workspace_id) {
        return Err("A previous backup for this server still requires staging recovery. Restart LazyBuilder before starting another backup-affecting operation.".into());
    }
    pending.workspace_ids.push(workspace_id.to_string());
    save_pending(&pending)
}

pub fn recover_workspace(workspace_id: &str) -> Result<u32, String> {
    let removed = cleanup_workspace_staging(workspace_id)?;
    clear(workspace_id)?;
    Ok(removed)
}

pub fn recover_pending() -> Result<BackupRecoveryReport, String> {
    let pending = load_pending()?;
    if pending.workspace_ids.is_empty() {
        return Ok(BackupRecoveryReport { recovered_workspaces: 0, removed_staging: 0, issues: Vec::new() });
    }

    let mut remaining = Vec::new();
    let mut recovered_workspaces = 0u32;
    let mut removed_staging = 0u32;
    let mut issues = Vec::new();

    for workspace_id in pending.workspace_ids {
        match cleanup_workspace_staging(&workspace_id) {
            Ok(removed) => {
                removed_staging = removed_staging.saturating_add(removed);
                recovered_workspaces = recovered_workspaces.saturating_add(1);
            }
            Err(error) => {
                issues.push(format!("Backup staging recovery for workspace {workspace_id} needs attention: {error}"));
                remaining.push(workspace_id);
            }
        }
    }

    save_pending(&PendingBackupRecovery { schema_version: RECOVERY_SCHEMA_VERSION, workspace_ids: remaining })?;
    Ok(BackupRecoveryReport { recovered_workspaces, removed_staging, issues })
}

pub fn legacy_sweep_required() -> Result<bool, String> {
    let path = legacy_sweep_marker_path()?;
    persistence::recover_atomic_file(&path, "backup recovery migration marker")?;
    if !persistence::metadata_entry_exists(&path, "backup recovery migration marker")? {
        return Ok(true);
    }
    let marker = match persistence::read_json::<LegacySweepMarker>(
        &path,
        "backup recovery migration marker",
    ) {
        Ok(marker) => marker,
        Err(_) => return Ok(true),
    };
    persistence::cleanup_recovery_files(&path, "backup recovery migration marker")?;
    Ok(marker.schema_version != LEGACY_SWEEP_SCHEMA_VERSION)
}

pub fn mark_legacy_sweep_complete() -> Result<(), String> {
    let path = legacy_sweep_marker_path()?;
    persistence::write_json_atomically(
        &path,
        &LegacySweepMarker { schema_version: LEGACY_SWEEP_SCHEMA_VERSION },
        "backup recovery migration marker",
    )
}

fn legacy_marker_requires_sweep(text: &str) -> bool {
    serde_json::from_str::<LegacySweepMarker>(text)
        .map(|marker| marker.schema_version != LEGACY_SWEEP_SCHEMA_VERSION)
        .unwrap_or(true)
}

fn clear(workspace_id: &str) -> Result<(), String> {
    let mut pending = load_pending()?;
    pending.workspace_ids.retain(|item| item != workspace_id);
    save_pending(&pending)
}

fn cleanup_workspace_staging(workspace_id: &str) -> Result<u32, String> {
    let entry = workspace_registry::get(workspace_id)?;
    let workspace = PathBuf::from(&entry.path)
        .canonicalize()
        .map_err(|error| format!("Could not resolve registered server workspace: {error}"))?;
    if !workspace.is_dir() {
        return Err("Registered server workspace is not a directory".into());
    }
    reject_link(&workspace)?;

    let parent = workspace.parent().ok_or_else(|| "Server workspace has no parent directory for backup recovery".to_string())?;
    let backup_root = parent.join(".lazybuilder-backups").join(workspace_id);
    if !backup_root.exists() {
        return Ok(0);
    }
    reject_link(&backup_root)?;
    if !backup_root.is_dir() {
        return Err("Server backup recovery location is not a directory".into());
    }

    let canonical_root = backup_root.canonicalize().map_err(|error| error.to_string())?;
    let mut removed = 0u32;
    for item in fs::read_dir(&canonical_root).map_err(|error| format!("Could not inspect server backup recovery location: {error}"))? {
        let item = item.map_err(|error| error.to_string())?;
        let name = item.file_name().to_string_lossy().to_string();
        if !name.starts_with(STAGING_PREFIX) {
            continue;
        }
        let path = item.path();
        let metadata = fs::symlink_metadata(&path).map_err(|error| error.to_string())?;
        if metadata.file_type().is_symlink() || is_reparse_point(&metadata) || !metadata.file_type().is_dir() {
            return Err(format!("Unsafe backup staging artifact was preserved for manual review: {}", path.display()));
        }
        let canonical = path.canonicalize().map_err(|error| error.to_string())?;
        if canonical.parent() != Some(canonical_root.as_path()) {
            return Err("LazyBuilder refused an unsafe backup staging recovery path".into());
        }
        fs::remove_dir_all(&canonical).map_err(|error| format!("Could not clean interrupted backup staging: {error}"))?;
        removed = removed.saturating_add(1);
    }
    Ok(removed)
}

fn load_pending() -> Result<PendingBackupRecovery, String> {
    load_pending_from(&pending_path()?)
}

fn load_pending_from(path: &Path) -> Result<PendingBackupRecovery, String> {
    persistence::recover_atomic_file(path, "pending backup recovery index")?;
    if !persistence::metadata_entry_exists(path, "pending backup recovery index")? {
        return Ok(PendingBackupRecovery {
            schema_version: RECOVERY_SCHEMA_VERSION,
            workspace_ids: Vec::new(),
        });
    }

    let pending: PendingBackupRecovery =
        persistence::read_json(path, "pending backup recovery index")
            .map_err(|error| format!("Could not parse pending backup recovery index: {error}"))?;
    if pending.schema_version != RECOVERY_SCHEMA_VERSION {
        return Err(format!(
            "Backup recovery index schema {} is unsupported by this Launcher",
            pending.schema_version
        ));
    }
    persistence::cleanup_recovery_files(path, "pending backup recovery index")?;
    Ok(pending)
}

fn save_pending(pending: &PendingBackupRecovery) -> Result<(), String> {
    let path = pending_path()?;
    if pending.workspace_ids.is_empty() {
        persistence::safe_path::remove_regular_file_if_present(
            &path,
            "pending backup recovery index",
        )?;
        persistence::cleanup_recovery_files(&path, "pending backup recovery index")?;
        return Ok(());
    }
    persistence::write_json_atomically(
        &path,
        pending,
        "pending backup recovery index",
    )
}

fn app_data_root() -> Result<PathBuf, String> {
    std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .map(|base| base.join("LazyBuilder"))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())
}

fn pending_path() -> Result<PathBuf, String> { Ok(app_data_root()?.join("pending-backups.json")) }
fn legacy_sweep_marker_path() -> Result<PathBuf, String> { Ok(app_data_root()?.join("backup-recovery-index-v1.json")) }

fn reject_link(path: &Path) -> Result<(), String> {
    let metadata = fs::symlink_metadata(path).map_err(|error| error.to_string())?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        Err(format!("LazyBuilder refused a symbolic link or Windows reparse point during backup recovery: {}", path.display()))
    } else {
        Ok(())
    }
}

#[cfg(windows)]
fn is_reparse_point(metadata: &fs::Metadata) -> bool {
    use std::os::windows::fs::MetadataExt;
    const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x0400;
    metadata.file_attributes() & FILE_ATTRIBUTE_REPARSE_POINT != 0
}

#[cfg(not(windows))]
fn is_reparse_point(_metadata: &fs::Metadata) -> bool { false }

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicU64, Ordering};

    static NEXT_TEST_ID: AtomicU64 = AtomicU64::new(1);

    fn temp_index_path(label: &str) -> PathBuf {
        let sequence = NEXT_TEST_ID.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!("lazybuilder-backup-recovery-{label}-{}-{sequence}", std::process::id()));
        fs::create_dir_all(&directory).unwrap();
        directory.join("pending-backups.json")
    }

    #[test]
    fn staging_prefix_is_narrowly_scoped() {
        assert!(".creating-backup-123".starts_with(STAGING_PREFIX));
        assert!(!"backup-123".starts_with(STAGING_PREFIX));
        assert!(!".creating-other-123".starts_with(STAGING_PREFIX));
    }

    #[test]
    fn pending_index_schema_is_explicit() {
        let pending = PendingBackupRecovery { schema_version: RECOVERY_SCHEMA_VERSION, workspace_ids: vec!["workspace-a".into()] };
        let text = serde_json::to_string(&pending).unwrap();
        assert!(text.contains("schemaVersion"));
        assert!(text.contains("workspace-a"));
    }

    #[test]
    fn interrupted_index_publish_prefers_previous_committed_copy() {
        let path = temp_index_path("previous-wins");
        let previous = path.with_extension("json.previous");
        let incoming = path.with_extension("json.incoming");
        let committed = PendingBackupRecovery { schema_version: RECOVERY_SCHEMA_VERSION, workspace_ids: vec!["committed-workspace".into()] };
        let uncommitted = PendingBackupRecovery { schema_version: RECOVERY_SCHEMA_VERSION, workspace_ids: vec!["incoming-workspace".into()] };
        fs::write(&previous, serde_json::to_vec(&committed).unwrap()).unwrap();
        fs::write(&incoming, serde_json::to_vec(&uncommitted).unwrap()).unwrap();

        let loaded = load_pending_from(&path).unwrap();
        assert_eq!(loaded.workspace_ids, vec!["committed-workspace"]);
        assert!(path.is_file());
        assert!(!previous.exists());
        assert!(!incoming.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn incoming_index_is_recovered_when_no_committed_copy_exists() {
        let path = temp_index_path("incoming-only");
        let incoming = path.with_extension("json.incoming");
        let value = PendingBackupRecovery { schema_version: RECOVERY_SCHEMA_VERSION, workspace_ids: vec!["incoming-workspace".into()] };
        fs::write(&incoming, serde_json::to_vec(&value).unwrap()).unwrap();

        let loaded = load_pending_from(&path).unwrap();
        assert_eq!(loaded.workspace_ids, vec!["incoming-workspace"]);
        assert!(path.is_file());
        assert!(!incoming.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn legacy_sweep_marker_schema_is_explicit() {
        let marker = LegacySweepMarker { schema_version: LEGACY_SWEEP_SCHEMA_VERSION };
        let text = serde_json::to_string(&marker).unwrap();
        assert_eq!(text, r#"{"schemaVersion":1}"#);
        assert!(!legacy_marker_requires_sweep(&text));
    }

    #[test]
    fn malformed_or_future_legacy_marker_requires_a_sweep() {
        assert!(legacy_marker_requires_sweep("not-json"));
        assert!(legacy_marker_requires_sweep(r#"{"schemaVersion":2}"#));
    }
}
