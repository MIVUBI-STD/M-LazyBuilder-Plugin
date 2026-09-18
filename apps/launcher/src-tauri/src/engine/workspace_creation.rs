use crate::engine::{persistence, workspace_registry::{self, WorkspaceEntry}};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};

static NEXT_CREATION_ID: AtomicU64 = AtomicU64::new(1);

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CreationRecoveryReport {
    pub recovered: u32,
    pub completed: u32,
    pub cleaned: u32,
    pub issues: Vec<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PendingCreation {
    destination_parent: String,
    staging_parent: String,
    staging_workspace: String,
    final_path: String,
    requested_name: String,
    workspace_id: Option<String>,
}

pub fn create(parent: &Path, name: &str) -> Result<WorkspaceEntry, String> {
    let name = validate_name(name)?;
    let parent = canonical_safe_directory(parent, "server location")?;
    let final_path = parent.join(&name);
    if final_path.exists() {
        return Err("A file or folder with that server name already exists".into());
    }

    let sequence = NEXT_CREATION_ID.fetch_add(1, Ordering::Relaxed);
    let staging_parent = parent.join(format!(".lazybuilder-creating-{}-{sequence}", std::process::id()));
    let staging_workspace = staging_parent.join(&name);
    if staging_parent.exists() {
        return Err("LazyBuilder creation staging path already exists; retry in a moment".into());
    }
    if !safe_creation_paths(&parent, &staging_parent, &staging_workspace, &final_path, &name) {
        return Err("LazyBuilder refused unsafe server creation staging paths".into());
    }

    let mut intent = PendingCreation {
        destination_parent: parent.display().to_string(),
        staging_parent: staging_parent.display().to_string(),
        staging_workspace: staging_workspace.display().to_string(),
        final_path: final_path.display().to_string(),
        requested_name: name.clone(),
        workspace_id: None,
    };
    add_pending_creation(intent.clone())?;

    fs::create_dir(&staging_parent).map_err(|error| creation_failure(&intent, format!("Could not create server staging directory: {error}")))?;

    let staged = match workspace_registry::create(&staging_parent, &name) {
        Ok(entry) => entry,
        Err(error) => {
            let cleanup = cleanup_staging(&intent);
            let clear = clear_pending_creation(&intent.staging_parent);
            return Err(if cleanup.is_err() || clear.is_err() {
                recovery_error(format!("Server creation failed before publish: {error}. Recovery metadata was preserved because staging cleanup did not complete safely."))
            } else {
                error
            });
        }
    };

    intent.workspace_id = Some(staged.id.clone());
    if let Err(error) = update_pending_creation(&intent) {
        let _ = workspace_registry::deactivate();
        return Err(recovery_error(format!("Server workspace was created in staging, but its durable creation identity could not be recorded: {error}")));
    }

    if let Err(error) = fs::rename(&staging_workspace, &final_path) {
        let _ = workspace_registry::deactivate();
        return Err(recovery_error(format!("Server workspace was created in staging but could not be published: {error}")));
    }

    let relocated = workspace_registry::relocate(&staged.id, &final_path)
        .map_err(|error| recovery_error(format!("Server workspace was published but its registered location could not be finalized: {error}")))?;

    let _ = fs::remove_dir(&staging_parent);
    let _ = clear_pending_creation(&intent.staging_parent);
    Ok(relocated)
}

pub fn recover_pending_creations() -> Result<CreationRecoveryReport, String> {
    let pending = load_pending_creations()?;
    if pending.is_empty() {
        return Ok(CreationRecoveryReport { recovered: 0, completed: 0, cleaned: 0, issues: Vec::new() });
    }

    let mut remaining = Vec::new();
    let mut report = CreationRecoveryReport { recovered: 0, completed: 0, cleaned: 0, issues: Vec::new() };

    for intent in pending {
        let parent = PathBuf::from(&intent.destination_parent);
        let staging_parent = PathBuf::from(&intent.staging_parent);
        let staging_workspace = PathBuf::from(&intent.staging_workspace);
        let final_path = PathBuf::from(&intent.final_path);

        if !safe_creation_paths(&parent, &staging_parent, &staging_workspace, &final_path, &intent.requested_name) {
            report.issues.push(format!("Server creation recovery for {} has unsafe recorded paths. Nothing was changed.", intent.requested_name));
            remaining.push(intent);
            continue;
        }
        if let Err(error) = reject_existing_reparse_points(&[&parent, &staging_parent, &staging_workspace, &final_path]) {
            report.issues.push(format!("Server creation recovery for {} was blocked by filesystem safety validation: {error}", intent.requested_name));
            remaining.push(intent);
            continue;
        }
        if final_path.exists() && staging_workspace.exists() {
            report.issues.push(format!("Server creation recovery for {} is ambiguous because both staging and final workspaces exist. Nothing was deleted.", intent.requested_name));
            remaining.push(intent);
            continue;
        }

        let recovered_identity = match intent.workspace_id.clone() {
            Some(id) => Some(id),
            None => find_registered_workspace_id(&intent.staging_workspace)?
                .or(find_registered_workspace_id(&intent.final_path)?),
        };

        let Some(workspace_id) = recovered_identity.as_deref() else {
            if final_path.exists() {
                report.issues.push(format!("Server creation recovery for {} found a final workspace but no registered workspace identity. Nothing was changed.", intent.requested_name));
                remaining.push(intent);
                continue;
            }
            match cleanup_staging(&intent) {
                Ok(()) => {
                    report.recovered = report.recovered.saturating_add(1);
                    report.cleaned = report.cleaned.saturating_add(1);
                }
                Err(error) => {
                    report.issues.push(format!("Server creation recovery for {} could not clean pre-registration staging: {error}", intent.requested_name));
                    remaining.push(intent);
                }
            }
            continue;
        };

        if final_path.is_dir() {
            match finish_forward(workspace_id, &final_path) {
                Ok(()) => {
                    let _ = fs::remove_dir(&staging_parent);
                    report.recovered = report.recovered.saturating_add(1);
                    report.completed = report.completed.saturating_add(1);
                }
                Err(error) => {
                    report.issues.push(format!("Server creation recovery for {} found a published workspace but could not finish registration: {error}", intent.requested_name));
                    remaining.push(intent);
                }
            }
            continue;
        }
        if final_path.exists() {
            report.issues.push(format!("Server creation recovery for {} found a non-directory final target. Nothing was changed.", intent.requested_name));
            remaining.push(intent);
            continue;
        }

        if staging_workspace.is_dir() {
            match fs::rename(&staging_workspace, &final_path)
                .map_err(|error| format!("Could not publish staged server: {error}"))
                .and_then(|_| finish_forward(workspace_id, &final_path))
            {
                Ok(()) => {
                    let _ = fs::remove_dir(&staging_parent);
                    report.recovered = report.recovered.saturating_add(1);
                    report.completed = report.completed.saturating_add(1);
                }
                Err(error) => {
                    report.issues.push(format!("Server creation recovery for {} could not finish a staged workspace: {error}", intent.requested_name));
                    remaining.push(intent);
                }
            }
            continue;
        }
        if staging_workspace.exists() {
            report.issues.push(format!("Server creation recovery for {} found a non-directory staging workspace. Nothing was changed.", intent.requested_name));
            remaining.push(intent);
            continue;
        }

        match workspace_registry::get(workspace_id) {
            Ok(entry) if paths_equal(&entry.path, &intent.final_path) => {
                let _ = fs::remove_dir(&staging_parent);
                report.recovered = report.recovered.saturating_add(1);
                report.completed = report.completed.saturating_add(1);
            }
            _ => {
                report.issues.push(format!("Server creation recovery for {} could not locate either the staged or published workspace. Recovery intent was preserved.", intent.requested_name));
                remaining.push(intent);
            }
        }
    }

    save_pending_creations(&remaining)?;
    Ok(report)
}

fn finish_forward(workspace_id: &str, final_path: &Path) -> Result<(), String> {
    match workspace_registry::get(workspace_id) {
        Ok(entry) if paths_equal(&entry.path, &final_path.display().to_string()) => Ok(()),
        Ok(_) => {
            workspace_registry::relocate(workspace_id, final_path)?;
            workspace_registry::deactivate()
        }
        Err(error) => Err(format!("Created workspace identity is missing from the server library: {error}")),
    }
}

fn find_registered_workspace_id(path: &str) -> Result<Option<String>, String> {
    Ok(workspace_registry::list()?
        .into_iter()
        .find(|entry| paths_equal(&entry.path, path))
        .map(|entry| entry.id))
}

fn validate_name(value: &str) -> Result<String, String> {
    let trimmed = value.trim();
    if trimmed.is_empty() { return Err("Server name is required".into()); }
    if trimmed.chars().any(|character| character.is_control() || "<>:\"/\\|?*".contains(character)) {
        return Err("Server name contains characters that are not valid in a Windows folder name".into());
    }
    if trimmed.ends_with('.') || trimmed.ends_with(' ') { return Err("Server name may not end with a dot or space".into()); }
    Ok(trimmed.to_string())
}

fn canonical_safe_directory(path: &Path, label: &str) -> Result<PathBuf, String> {
    let metadata = fs::symlink_metadata(path).map_err(|error| format!("Could not inspect {label}: {error}"))?;
    if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
        return Err(format!("LazyBuilder refused a symbolic link or Windows reparse point as the {label}"));
    }
    let canonical = path.canonicalize().map_err(|error| format!("Could not resolve {label}: {error}"))?;
    if !canonical.is_dir() { return Err(format!("Selected {label} is not a directory")); }
    Ok(canonical)
}

fn safe_creation_paths(parent: &Path, staging_parent: &Path, staging_workspace: &Path, final_path: &Path, name: &str) -> bool {
    if staging_parent.parent() != Some(parent) || final_path.parent() != Some(parent) { return false; }
    if staging_workspace.parent() != Some(staging_parent) { return false; }
    if staging_workspace.file_name().and_then(|value| value.to_str()) != Some(name) || final_path.file_name().and_then(|value| value.to_str()) != Some(name) { return false; }
    staging_parent.file_name().and_then(|value| value.to_str()).is_some_and(|value| value.starts_with(".lazybuilder-creating-") && value.len() > ".lazybuilder-creating-".len())
}

fn cleanup_staging(intent: &PendingCreation) -> Result<(), String> {
    let parent = PathBuf::from(&intent.destination_parent);
    let staging_parent = PathBuf::from(&intent.staging_parent);
    let staging_workspace = PathBuf::from(&intent.staging_workspace);
    let final_path = PathBuf::from(&intent.final_path);
    if !safe_creation_paths(&parent, &staging_parent, &staging_workspace, &final_path, &intent.requested_name) {
        return Err("LazyBuilder refused an unsafe server creation staging cleanup path".into());
    }
    if staging_parent.exists() {
        let metadata = fs::symlink_metadata(&staging_parent).map_err(|error| error.to_string())?;
        if metadata.file_type().is_symlink() || is_reparse_point(&metadata) || !metadata.file_type().is_dir() {
            return Err("Server creation staging is not a safe directory".into());
        }
        fs::remove_dir_all(&staging_parent).map_err(|error| format!("Could not clean server creation staging: {error}"))?;
    }
    Ok(())
}

fn reject_existing_reparse_points(paths: &[&Path]) -> Result<(), String> {
    for path in paths {
        if !path.exists() { continue; }
        let metadata = fs::symlink_metadata(path).map_err(|error| error.to_string())?;
        if metadata.file_type().is_symlink() || is_reparse_point(&metadata) {
            return Err(format!("Unsafe symbolic link or Windows reparse point: {}", path.display()));
        }
    }
    Ok(())
}

fn pending_creations_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("pending-creations.json"))
}

fn load_pending_creations() -> Result<Vec<PendingCreation>, String> {
    let path = pending_creations_path()?;
    recover_pending_creation_file(&path)?;
    if !persistence::metadata_entry_exists(&path, "pending server creation intent")? {
        return Ok(Vec::new());
    }
    let entries = persistence::read_json(&path, "pending server creation intent")
        .map_err(|error| format!("Could not parse pending server creations: {error}"))?;
    cleanup_pending_creation_recovery_files(&path)?;
    Ok(entries)
}

fn save_pending_creations(entries: &[PendingCreation]) -> Result<(), String> {
    let path = pending_creations_path()?;
    if entries.is_empty() {
        persistence::safe_path::remove_regular_file_if_present(
            &path,
            "pending server creation intent",
        )?;
        cleanup_pending_creation_recovery_files(&path)?;
        return Ok(());
    }
    persistence::write_json_atomically(
        &path,
        &entries,
        "pending server creation intent",
    )
}

fn recover_pending_creation_file(path: &Path) -> Result<(), String> {
    persistence::recover_atomic_file(path, "pending server creation intent")
}

fn cleanup_pending_creation_recovery_files(path: &Path) -> Result<(), String> {
    persistence::cleanup_recovery_files(path, "pending server creation intent")
}

fn add_pending_creation(intent: PendingCreation) -> Result<(), String> {
    let mut entries = load_pending_creations()?;
    if entries.iter().any(|item| paths_equal(&item.final_path, &intent.final_path) || paths_equal(&item.staging_parent, &intent.staging_parent)) {
        return Err("A previous server creation at this destination still requires recovery".into());
    }
    entries.push(intent);
    save_pending_creations(&entries)
}

fn update_pending_creation(intent: &PendingCreation) -> Result<(), String> {
    let mut entries = load_pending_creations()?;
    let target = entries.iter_mut().find(|item| paths_equal(&item.staging_parent, &intent.staging_parent))
        .ok_or_else(|| "Server creation recovery intent was not found".to_string())?;
    *target = intent.clone();
    save_pending_creations(&entries)
}

fn clear_pending_creation(staging_parent: &str) -> Result<(), String> {
    let mut entries = load_pending_creations()?;
    entries.retain(|item| !paths_equal(&item.staging_parent, staging_parent));
    save_pending_creations(&entries)
}

fn creation_failure(intent: &PendingCreation, message: String) -> String {
    if clear_pending_creation(&intent.staging_parent).is_err() {
        recovery_error(format!("{message}. Recovery intent cleanup failed."))
    } else {
        message
    }
}

fn recovery_error(message: impl AsRef<str>) -> String { format!("CREATE_RECOVERY_REQUIRED: {}", message.as_ref()) }
fn paths_equal(left: &str, right: &str) -> bool { if cfg!(windows) { left.eq_ignore_ascii_case(right) } else { left == right } }

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
    use std::time::{SystemTime, UNIX_EPOCH};

    fn temp_metadata_path(label: &str) -> PathBuf {
        let nonce = SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos();
        let directory = std::env::temp_dir().join(format!("lazybuilder-creation-metadata-{label}-{}-{nonce}", std::process::id()));
        fs::create_dir_all(&directory).unwrap();
        directory.join("pending-creations.json")
    }

    #[test]
    fn creation_paths_are_strictly_scoped() {
        let parent = Path::new("D:/Servers");
        assert!(safe_creation_paths(
            parent,
            Path::new("D:/Servers/.lazybuilder-creating-1-2"),
            Path::new("D:/Servers/.lazybuilder-creating-1-2/Build"),
            Path::new("D:/Servers/Build"),
            "Build",
        ));
        assert!(!safe_creation_paths(
            parent,
            Path::new("D:/Other/.lazybuilder-creating-1-2"),
            Path::new("D:/Other/.lazybuilder-creating-1-2/Build"),
            Path::new("D:/Servers/Build"),
            "Build",
        ));
    }

    #[test]
    fn creation_intent_recovery_prefers_previous_committed_copy() {
        let path = temp_metadata_path("previous");
        let previous = path.with_extension("json.previous");
        let incoming = path.with_extension("json.incoming");
        fs::write(&previous, b"[]").unwrap();
        fs::write(&incoming, b"[]").unwrap();
        recover_pending_creation_file(&path).unwrap();
        assert!(path.exists());
        assert!(incoming.exists());
        cleanup_pending_creation_recovery_files(&path).unwrap();
        assert!(!incoming.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn creation_intent_incoming_recovers_without_committed_copy() {
        let path = temp_metadata_path("incoming");
        let incoming = path.with_extension("json.incoming");
        fs::write(&incoming, b"[]").unwrap();
        recover_pending_creation_file(&path).unwrap();
        assert_eq!(fs::read_to_string(&path).unwrap(), "[]");
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn malformed_creation_intent_preserves_recovery_evidence() {
        let path = temp_metadata_path("malformed");
        let previous = path.with_extension("json.previous");
        let incoming = path.with_extension("json.incoming");
        fs::write(&path, b"not-json").unwrap();
        fs::write(&previous, b"[]").unwrap();
        fs::write(&incoming, b"[]").unwrap();
        recover_pending_creation_file(&path).unwrap();
        assert!(serde_json::from_str::<Vec<PendingCreation>>(&fs::read_to_string(&path).unwrap()).is_err());
        assert!(previous.exists());
        assert!(incoming.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }
}
