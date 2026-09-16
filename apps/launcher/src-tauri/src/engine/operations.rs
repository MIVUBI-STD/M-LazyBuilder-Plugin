use crate::engine::diagnostics;
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::RwLock;
use std::time::{SystemTime, UNIX_EPOCH};
#[cfg(windows)]
use std::os::windows::fs::MetadataExt;

const MAX_OPERATION_HISTORY: usize = 100;
const OPERATION_JOURNAL_SCHEMA_VERSION: u32 = 1;
const WORKSPACE_LIBRARY_RESOURCE: &str = "workspace-library";
const WORKSPACE_RESOURCE_PREFIX: &str = "workspace:";
#[cfg(windows)]
const FILE_ATTRIBUTE_REPARSE_POINT: u32 = 0x00000400;
static NEXT_OPERATION_ID: AtomicU64 = AtomicU64::new(1);

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum OperationState { Queued, Running, Succeeded, Failed, Cancelling, Cancelled, RecoveryRequired }

impl OperationState {
    pub fn is_terminal(&self) -> bool { matches!(self, Self::Succeeded | Self::Failed | Self::Cancelled | Self::RecoveryRequired) }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OperationProgress { pub current: u64, pub total: Option<u64>, pub unit: String }

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OperationError { pub code: String, pub message: String, pub details: String, pub recoverable: bool }

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OperationSnapshot {
    pub id: String,
    pub correlation_id: String,
    pub kind: String,
    pub resource: String,
    pub state: OperationState,
    pub phase: String,
    pub status: String,
    pub details: String,
    pub progress: Option<OperationProgress>,
    pub can_cancel: bool,
    pub cancel_requested: bool,
    pub warnings: Vec<String>,
    pub error: Option<OperationError>,
    pub created_at_unix_seconds: u64,
    pub updated_at_unix_seconds: u64,
    pub completed_at_unix_seconds: Option<u64>,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OperationRecoveryReport {
    pub interrupted: u32,
    pub recovery_required: u32,
    pub failed: u32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct OperationJournal {
    schema_version: u32,
    entries: VecDeque<OperationSnapshot>,
}

pub struct OperationRegistry {
    entries: RwLock<VecDeque<OperationSnapshot>>,
    journal_path: Option<PathBuf>,
    disabled_reason: Option<String>,
}

impl Default for OperationRegistry {
    fn default() -> Self {
        Self { entries: RwLock::new(VecDeque::new()), journal_path: None, disabled_reason: None }
    }
}

impl OperationRegistry {
    pub fn initialize() -> Result<(Self, OperationRecoveryReport), String> {
        let path = operation_journal_path()?;
        let mut entries = load_journal(&path)?;
        trim_history(&mut entries);
        let report = reconcile_interrupted_entries(&mut entries, now_unix_seconds());

        let registry = Self {
            entries: RwLock::new(entries),
            journal_path: Some(path),
            disabled_reason: None,
        };
        registry.persist_current()?;
        Ok((registry, report))
    }

    pub fn disabled(reason: impl Into<String>) -> Self {
        Self {
            entries: RwLock::new(VecDeque::new()),
            journal_path: None,
            disabled_reason: Some(reason.into()),
        }
    }

    pub fn begin(&self, kind: &str, resource: &str, can_cancel: bool) -> Result<OperationSnapshot, String> { self.begin_internal(kind, resource, can_cancel, false) }
    pub fn begin_exclusive(&self, kind: &str, resource: &str, can_cancel: bool) -> Result<OperationSnapshot, String> { self.begin_internal(kind, resource, can_cancel, true) }

    fn begin_internal(&self, kind: &str, resource: &str, can_cancel: bool, exclusive: bool) -> Result<OperationSnapshot, String> {
        self.ensure_available()?;
        let kind = kind.trim();
        let resource = resource.trim();
        if kind.is_empty() { return Err("Operation kind is required".into()); }
        if resource.is_empty() { return Err("Operation resource is required".into()); }

        let mut guard = self.entries.write().map_err(|_| "operation registry lock poisoned".to_string())?;
        if exclusive && guard.iter().any(|entry| !entry.state.is_terminal() && resources_conflict(resource, &entry.resource)) {
            return Err(format!("Another launcher operation is already active for a conflicting resource: {resource}"));
        }

        let now = now_unix_seconds();
        let sequence = NEXT_OPERATION_ID.fetch_add(1, Ordering::Relaxed);
        let launcher_pid = std::process::id();
        let correlation_id = diagnostics::new_correlation_id("operation");
        let snapshot = OperationSnapshot {
            id: format!("op-{now}-{launcher_pid}-{sequence}"),
            correlation_id: correlation_id.clone(),
            kind: kind.to_string(),
            resource: resource.to_string(),
            state: OperationState::Running,
            phase: "starting".into(),
            status: "Starting".into(),
            details: String::new(),
            progress: None,
            can_cancel,
            cancel_requested: false,
            warnings: Vec::new(),
            error: None,
            created_at_unix_seconds: now,
            updated_at_unix_seconds: now,
            completed_at_unix_seconds: None,
        };

        let mut next = guard.clone();
        next.push_front(snapshot.clone());
        trim_history(&mut next);
        self.persist_entries(&next)?;
        *guard = next;
        diagnostics::info_with_context(&correlation_id, &format!("operation started kind={kind} resource={resource} id={}", snapshot.id));
        Ok(snapshot)
    }

    pub fn list(&self) -> Result<Vec<OperationSnapshot>, String> {
        self.ensure_available()?;
        self.entries.read().map_err(|_| "operation registry lock poisoned".to_string()).map(|entries| entries.iter().cloned().collect())
    }

    pub fn get(&self, id: &str) -> Result<OperationSnapshot, String> {
        self.ensure_available()?;
        self.entries.read().map_err(|_| "operation registry lock poisoned".to_string())?.iter().find(|entry| entry.id == id).cloned().ok_or_else(|| "Launcher operation was not found".to_string())
    }

    pub fn has_active_for_resource(&self, resource: &str) -> Result<bool, String> {
        self.ensure_available()?;
        let resource = resource.trim();
        if resource.is_empty() { return Err("Operation resource is required".into()); }
        Ok(self.entries
            .read()
            .map_err(|_| "operation registry lock poisoned".to_string())?
            .iter()
            .any(|entry| resources_conflict(resource, &entry.resource) && !entry.state.is_terminal()))
    }

    pub fn set_phase(&self, id: &str, phase: &str, status: &str, details: &str, progress: Option<OperationProgress>) -> Result<OperationSnapshot, String> {
        let result = self.mutate(id, |entry| {
            ensure_active(entry)?;
            entry.phase = phase.trim().to_string();
            entry.status = status.trim().to_string();
            entry.details = details.trim().to_string();
            entry.progress = progress;
            Ok(())
        })?;
        diagnostics::info_with_context(&result.correlation_id, &format!("operation phase id={} phase={} status={}", result.id, result.phase, result.status));
        Ok(result)
    }

    pub fn set_cancelable(&self, id: &str, can_cancel: bool) -> Result<OperationSnapshot, String> {
        self.mutate(id, |entry| {
            ensure_active(entry)?;
            if entry.cancel_requested && can_cancel { return Err("Cancellation is already pending for this launcher operation".into()); }
            entry.can_cancel = can_cancel;
            Ok(())
        })
    }

    pub fn add_warning(&self, id: &str, warning: &str) -> Result<OperationSnapshot, String> {
        self.mutate(id, |entry| {
            ensure_active(entry)?;
            let warning = warning.trim();
            if !warning.is_empty() && !entry.warnings.iter().any(|existing| existing == warning) {
                entry.warnings.push(warning.to_string());
            }
            Ok(())
        })
    }

    pub fn request_cancel(&self, id: &str) -> Result<OperationSnapshot, String> {
        let result = self.mutate(id, |entry| {
            ensure_active(entry)?;
            if !entry.can_cancel { return Err("This launcher operation cannot be cancelled safely".into()); }
            entry.cancel_requested = true;
            entry.state = OperationState::Cancelling;
            entry.status = "Cancelling".into();
            Ok(())
        })?;
        diagnostics::info_with_context(&result.correlation_id, &format!("operation cancellation requested id={}", result.id));
        Ok(result)
    }

    pub fn cancellation_requested(&self, id: &str) -> Result<bool, String> { Ok(self.get(id)?.cancel_requested) }
    pub fn succeed(&self, id: &str, status: &str) -> Result<OperationSnapshot, String> { self.finish(id, OperationState::Succeeded, status, None) }
    pub fn cancel(&self, id: &str, status: &str) -> Result<OperationSnapshot, String> { self.finish(id, OperationState::Cancelled, status, None) }
    pub fn fail(&self, id: &str, error: OperationError) -> Result<OperationSnapshot, String> { let status = error.message.clone(); self.finish(id, OperationState::Failed, &status, Some(error)) }
    pub fn require_recovery(&self, id: &str, error: OperationError) -> Result<OperationSnapshot, String> { let status = error.message.clone(); self.finish(id, OperationState::RecoveryRequired, &status, Some(error)) }

    fn finish(&self, id: &str, state: OperationState, status: &str, error: Option<OperationError>) -> Result<OperationSnapshot, String> {
        let result = self.mutate(id, |entry| {
            ensure_active(entry)?;
            let now = now_unix_seconds();
            entry.state = state;
            entry.status = status.trim().to_string();
            entry.error = error;
            entry.can_cancel = false;
            entry.cancel_requested = false;
            entry.progress = entry.progress.take().map(|mut progress| {
                if let Some(total) = progress.total { progress.current = total; }
                progress
            });
            entry.completed_at_unix_seconds = Some(now);
            Ok(())
        })?;
        let message = format!("operation finished id={} state={:?} status={}", result.id, result.state, result.status);
        if matches!(result.state, OperationState::Failed | OperationState::RecoveryRequired) {
            diagnostics::error_with_context(&result.correlation_id, &message);
        } else {
            diagnostics::info_with_context(&result.correlation_id, &message);
        }
        Ok(result)
    }

    fn mutate<F>(&self, id: &str, mutation: F) -> Result<OperationSnapshot, String>
    where F: FnOnce(&mut OperationSnapshot) -> Result<(), String>,
    {
        self.ensure_available()?;
        let mut guard = self.entries.write().map_err(|_| "operation registry lock poisoned".to_string())?;
        let mut next = guard.clone();
        let entry = next.iter_mut().find(|entry| entry.id == id).ok_or_else(|| "Launcher operation was not found".to_string())?;
        mutation(entry)?;
        entry.updated_at_unix_seconds = now_unix_seconds();
        let result = entry.clone();
        trim_history(&mut next);
        self.persist_entries(&next)?;
        *guard = next;
        Ok(result)
    }

    fn ensure_available(&self) -> Result<(), String> {
        if let Some(reason) = &self.disabled_reason {
            Err(format!("Launcher operation journal is unavailable: {reason}"))
        } else {
            Ok(())
        }
    }

    fn persist_current(&self) -> Result<(), String> {
        let entries = self.entries.read().map_err(|_| "operation registry lock poisoned".to_string())?;
        self.persist_entries(&entries)
    }

    fn persist_entries(&self, entries: &VecDeque<OperationSnapshot>) -> Result<(), String> {
        let Some(path) = self.journal_path.as_ref() else { return Ok(()); };
        persist_journal(path, entries)
    }
}

fn resources_conflict(requested: &str, existing: &str) -> bool {
    if requested == existing {
        return true;
    }
    let requested_workspace = requested.starts_with(WORKSPACE_RESOURCE_PREFIX);
    let existing_workspace = existing.starts_with(WORKSPACE_RESOURCE_PREFIX);
    (requested == WORKSPACE_LIBRARY_RESOURCE && existing_workspace)
        || (existing == WORKSPACE_LIBRARY_RESOURCE && requested_workspace)
}

fn reconcile_interrupted_entries(entries: &mut VecDeque<OperationSnapshot>, now: u64) -> OperationRecoveryReport {
    let mut report = OperationRecoveryReport { interrupted: 0, recovery_required: 0, failed: 0 };
    for entry in entries.iter_mut() {
        if entry.state.is_terminal() { continue; }
        report.interrupted = report.interrupted.saturating_add(1);
        entry.can_cancel = false;
        entry.cancel_requested = false;
        entry.updated_at_unix_seconds = now;
        entry.completed_at_unix_seconds = Some(now);
        entry.progress = None;

        if interruption_is_low_risk(&entry.kind) {
            report.failed = report.failed.saturating_add(1);
            entry.state = OperationState::Failed;
            entry.status = "Interrupted when Launcher exited".into();
            entry.error = Some(OperationError {
                code: "INTERRUPTED_LAUNCHER_OPERATION".into(),
                message: "This Launcher operation was interrupted by the previous app exit.".into(),
                details: "No durable workspace mutation is expected from this operation. Retry it if still needed.".into(),
                recoverable: true,
            });
        } else {
            report.recovery_required = report.recovery_required.saturating_add(1);
            entry.state = OperationState::RecoveryRequired;
            entry.status = "Interrupted operation requires reconciliation".into();
            entry.error = Some(OperationError {
                code: "INTERRUPTED_LAUNCHER_OPERATION".into(),
                message: "A previous Launcher operation ended before its terminal state was recorded.".into(),
                details: "LazyBuilder preserved the operation history. Domain recovery runs separately during startup; inspect the affected server before retrying destructive work.".into(),
                recoverable: true,
            });
        }
    }
    report
}

fn interruption_is_low_risk(kind: &str) -> bool {
    matches!(kind, "export-support-bundle")
}

fn operation_journal_path() -> Result<PathBuf, String> {
    let base = std::env::var_os("LOCALAPPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("APPDATA").map(PathBuf::from))
        .ok_or_else(|| "Windows application data directory is unavailable".to_string())?;
    Ok(base.join("LazyBuilder").join("operations.json"))
}

fn load_journal(path: &Path) -> Result<VecDeque<OperationSnapshot>, String> {
    recover_journal_file(path)?;
    if !metadata_entry_exists(path, "Launcher operation journal")? {
        return Ok(VecDeque::new());
    }
    ensure_regular_metadata_file(path, "Launcher operation journal")?;
    let entries = read_journal_file(path)?;
    cleanup_journal_recovery_files(path)?;
    Ok(entries)
}

fn recover_journal_file(path: &Path) -> Result<(), String> {
    let incoming = path.with_extension("json.incoming");
    let previous = path.with_extension("json.previous");

    if metadata_entry_exists(path, "Launcher operation journal")? {
        ensure_regular_metadata_file(path, "Launcher operation journal")?;
        return Ok(());
    }
    if metadata_entry_exists(&previous, "previous Launcher operation journal")? {
        ensure_regular_metadata_file(&previous, "previous Launcher operation journal")?;
        fs::rename(&previous, path).map_err(|error| format!("Could not restore previous Launcher operation journal: {error}"))?;
        return Ok(());
    }
    if metadata_entry_exists(&incoming, "Launcher operation journal staging file")? {
        ensure_regular_metadata_file(&incoming, "Launcher operation journal staging file")?;
        fs::rename(&incoming, path).map_err(|error| format!("Could not publish recovered Launcher operation journal: {error}"))?;
    }
    Ok(())
}

fn cleanup_journal_recovery_files(path: &Path) -> Result<(), String> {
    remove_metadata_file_if_exists(&path.with_extension("json.previous"), "previous Launcher operation journal")?;
    remove_metadata_file_if_exists(&path.with_extension("json.incoming"), "Launcher operation journal staging file")
}

fn read_journal_file(path: &Path) -> Result<VecDeque<OperationSnapshot>, String> {
    let text = fs::read_to_string(path).map_err(|error| format!("Could not read Launcher operation journal: {error}"))?;
    let journal: OperationJournal = serde_json::from_str(&text).map_err(|error| format!("Could not parse Launcher operation journal: {error}"))?;
    if journal.schema_version != OPERATION_JOURNAL_SCHEMA_VERSION {
        return Err(format!("Launcher operation journal schema {} is unsupported by this build", journal.schema_version));
    }
    Ok(journal.entries)
}

fn persist_journal(path: &Path, entries: &VecDeque<OperationSnapshot>) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|error| format!("Could not prepare Launcher operation journal directory: {error}"))?;
    }
    let incoming = path.with_extension("json.incoming");
    remove_metadata_file_if_exists(&incoming, "Launcher operation journal staging file")?;

    let journal = OperationJournal { schema_version: OPERATION_JOURNAL_SCHEMA_VERSION, entries: entries.clone() };
    let text = serde_json::to_string_pretty(&journal).map_err(|error| error.to_string())?;
    let mut file = OpenOptions::new()
        .create_new(true)
        .write(true)
        .open(&incoming)
        .map_err(|error| format!("Could not write Launcher operation journal staging file: {error}"))?;
    file.write_all(text.as_bytes()).map_err(|error| error.to_string())?;
    file.sync_all().map_err(|error| format!("Could not flush Launcher operation journal: {error}"))?;
    drop(file);
    replace_journal_file(&incoming, path)
}

fn replace_journal_file(source: &Path, destination: &Path) -> Result<(), String> {
    ensure_regular_metadata_file(source, "Launcher operation journal staging file")?;
    if metadata_entry_exists(destination, "Launcher operation journal")? {
        ensure_regular_metadata_file(destination, "Launcher operation journal")?;
        let previous = destination.with_extension("json.previous");
        remove_metadata_file_if_exists(&previous, "previous Launcher operation journal")?;
        fs::rename(destination, &previous).map_err(|error| format!("Could not preserve previous Launcher operation journal: {error}"))?;
        match fs::rename(source, destination) {
            Ok(()) => {
                let _ = fs::remove_file(previous);
                Ok(())
            }
            Err(publish_error) => match fs::rename(&previous, destination) {
                Ok(()) => Err(format!("Could not publish Launcher operation journal; previous journal was restored: {publish_error}")),
                Err(rollback_error) => Err(format!(
                    "Could not publish Launcher operation journal ({publish_error}) and could not restore the previous journal ({rollback_error}). Recovery files were preserved."
                )),
            },
        }
    } else {
        fs::rename(source, destination).map_err(|error| format!("Could not publish Launcher operation journal: {error}"))
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
    if !metadata_entry_exists(path, label)? { return Ok(()); }
    ensure_regular_metadata_file(path, label)?;
    fs::remove_file(path).map_err(|error| format!("Could not remove {label}: {error}"))
}

fn ensure_active(entry: &OperationSnapshot) -> Result<(), String> { if entry.state.is_terminal() { Err("Launcher operation is already finished".into()) } else { Ok(()) } }
fn trim_history(entries: &mut VecDeque<OperationSnapshot>) { while entries.len() > MAX_OPERATION_HISTORY { if let Some(position) = entries.iter().rposition(|entry| entry.state.is_terminal()) { entries.remove(position); } else { break; } } }
fn now_unix_seconds() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or_default() }

#[cfg(test)]
mod tests {
    use super::*;

    fn sample_operation(kind: &str, state: OperationState) -> OperationSnapshot {
        OperationSnapshot {
            id: "op-test".into(),
            correlation_id: "operation-test".into(),
            kind: kind.into(),
            resource: "workspace:test".into(),
            state,
            phase: "copying".into(),
            status: "Working".into(),
            details: String::new(),
            progress: Some(OperationProgress { current: 4, total: Some(10), unit: "bytes".into() }),
            can_cancel: true,
            cancel_requested: false,
            warnings: Vec::new(),
            error: None,
            created_at_unix_seconds: 1,
            updated_at_unix_seconds: 1,
            completed_at_unix_seconds: None,
        }
    }

    fn temp_journal_path(label: &str) -> PathBuf {
        let sequence = NEXT_OPERATION_ID.fetch_add(1, Ordering::Relaxed);
        let directory = std::env::temp_dir().join(format!("lazybuilder-operation-journal-test-{}-{label}-{sequence}", std::process::id()));
        fs::create_dir_all(&directory).unwrap();
        directory.join("operations.json")
    }

    #[test]
    fn operation_lifecycle_has_one_terminal_transition() {
        let registry = OperationRegistry::default();
        let started = registry.begin("duplicate-server", "workspace:test", true).unwrap();
        assert_eq!(started.state, OperationState::Running);
        assert!(!started.correlation_id.is_empty());
        let running = registry.set_phase(&started.id, "copying", "Copying server files", "", Some(OperationProgress { current: 25, total: Some(100), unit: "bytes".into() })).unwrap();
        assert_eq!(running.progress.unwrap().current, 25);
        let finished = registry.succeed(&started.id, "Server duplicated").unwrap();
        assert_eq!(finished.state, OperationState::Succeeded);
        assert!(!finished.can_cancel);
        assert!(finished.completed_at_unix_seconds.is_some());
        assert!(registry.succeed(&started.id, "again").is_err());
    }

    #[test]
    fn cancellation_requires_explicit_capability() {
        let registry = OperationRegistry::default();
        let fixed = registry.begin("delete-server", "workspace:fixed", false).unwrap();
        assert!(registry.request_cancel(&fixed.id).is_err());
        let cancellable = registry.begin("download-runtime", "runtime:java", true).unwrap();
        let requested = registry.request_cancel(&cancellable.id).unwrap();
        assert_eq!(requested.state, OperationState::Cancelling);
        assert!(registry.cancellation_requested(&cancellable.id).unwrap());
    }

    #[test]
    fn exclusive_resource_rejects_second_active_operation() {
        let registry = OperationRegistry::default();
        let first = registry.begin_exclusive("duplicate-server", "workspace:test", true).unwrap();
        assert!(registry.has_active_for_resource("workspace:test").unwrap());
        assert!(registry.begin_exclusive("backup-server", "workspace:test", true).is_err());
        registry.succeed(&first.id, "done").unwrap();
        assert!(!registry.has_active_for_resource("workspace:test").unwrap());
        assert!(registry.begin_exclusive("backup-server", "workspace:test", true).is_ok());
    }

    #[test]
    fn workspace_library_conflicts_with_workspace_resources() {
        assert!(resources_conflict("workspace-library", "workspace:one"));
        assert!(resources_conflict("workspace:one", "workspace-library"));
        assert!(resources_conflict("workspace:one", "workspace:one"));
        assert!(!resources_conflict("workspace:one", "workspace:two"));
        assert!(!resources_conflict("launcher:support-bundle", "workspace:one"));
    }

    #[test]
    fn library_and_workspace_exclusive_operations_are_serialized() {
        let registry = OperationRegistry::default();
        let library = registry.begin_exclusive("adopt-server", "workspace-library", false).unwrap();
        assert!(registry.has_active_for_resource("workspace:one").unwrap());
        assert!(registry.begin_exclusive("backup-server", "workspace:one", false).is_err());
        registry.succeed(&library.id, "done").unwrap();

        let workspace = registry.begin_exclusive("backup-server", "workspace:one", false).unwrap();
        assert!(registry.has_active_for_resource("workspace-library").unwrap());
        assert!(registry.begin_exclusive("create-server", "workspace-library", false).is_err());
        assert!(registry.begin_exclusive("backup-server", "workspace:two", false).is_ok());
        registry.succeed(&workspace.id, "done").unwrap();
    }

    #[test]
    fn publish_boundary_can_disable_late_cancellation() {
        let registry = OperationRegistry::default();
        let operation = registry.begin_exclusive("duplicate-server", "workspace:test", true).unwrap();
        registry.set_cancelable(&operation.id, false).unwrap();
        assert!(registry.request_cancel(&operation.id).is_err());
    }

    #[test]
    fn bounded_history_never_evicts_active_operations_first() {
        let registry = OperationRegistry::default();
        let active = registry.begin("active", "resource:active", false).unwrap();
        for index in 0..(MAX_OPERATION_HISTORY + 5) {
            let operation = registry.begin("finished", &format!("resource:{index}"), false).unwrap();
            registry.succeed(&operation.id, "done").unwrap();
        }
        let list = registry.list().unwrap();
        assert!(list.len() <= MAX_OPERATION_HISTORY + 1);
        assert!(list.iter().any(|operation| operation.id == active.id));
    }

    #[test]
    fn interrupted_mutations_require_recovery_and_reconciliation_is_idempotent() {
        let mut entries = VecDeque::from([
            sample_operation("restore-server", OperationState::Running),
            sample_operation("export-support-bundle", OperationState::Cancelling),
        ]);
        entries[0].id = "op-restore".into();
        entries[1].id = "op-support".into();

        let report = reconcile_interrupted_entries(&mut entries, 50);
        assert_eq!(report.interrupted, 2);
        assert_eq!(report.recovery_required, 1);
        assert_eq!(report.failed, 1);
        assert_eq!(entries[0].state, OperationState::RecoveryRequired);
        assert_eq!(entries[1].state, OperationState::Failed);
        assert!(entries.iter().all(|entry| entry.completed_at_unix_seconds == Some(50)));

        let second = reconcile_interrupted_entries(&mut entries, 60);
        assert_eq!(second.interrupted, 0);
        assert_eq!(entries[0].completed_at_unix_seconds, Some(50));
    }

    #[test]
    fn journal_round_trip_preserves_operation_history() {
        let path = temp_journal_path("round-trip");
        let entries = VecDeque::from([sample_operation("backup-server", OperationState::Succeeded)]);
        persist_journal(&path, &entries).unwrap();
        let loaded = load_journal(&path).unwrap();
        assert_eq!(loaded.len(), 1);
        assert_eq!(loaded[0].kind, "backup-server");
        assert_eq!(loaded[0].state, OperationState::Succeeded);
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn operation_journal_recovery_prefers_previous_committed_copy() {
        let path = temp_journal_path("previous");
        let previous = path.with_extension("json.previous");
        let incoming = path.with_extension("json.incoming");
        let journal = OperationJournal { schema_version: OPERATION_JOURNAL_SCHEMA_VERSION, entries: VecDeque::new() };
        let text = serde_json::to_string(&journal).unwrap();
        fs::write(&previous, text.as_bytes()).unwrap();
        fs::write(&incoming, text.as_bytes()).unwrap();
        let loaded = load_journal(&path).unwrap();
        assert!(loaded.is_empty());
        assert!(path.exists());
        assert!(!incoming.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn operation_journal_incoming_recovers_without_committed_copy() {
        let path = temp_journal_path("incoming");
        let incoming = path.with_extension("json.incoming");
        let journal = OperationJournal { schema_version: OPERATION_JOURNAL_SCHEMA_VERSION, entries: VecDeque::new() };
        fs::write(&incoming, serde_json::to_vec(&journal).unwrap()).unwrap();
        let loaded = load_journal(&path).unwrap();
        assert!(loaded.is_empty());
        assert!(path.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn malformed_operation_journal_preserves_recovery_evidence() {
        let path = temp_journal_path("malformed");
        let previous = path.with_extension("json.previous");
        let incoming = path.with_extension("json.incoming");
        fs::write(&path, b"not-json").unwrap();
        fs::write(&previous, b"previous").unwrap();
        fs::write(&incoming, b"incoming").unwrap();
        assert!(load_journal(&path).is_err());
        assert!(previous.exists());
        assert!(incoming.exists());
        let _ = fs::remove_dir_all(path.parent().unwrap());
    }

    #[test]
    fn disabled_registry_refuses_new_operations() {
        let registry = OperationRegistry::disabled("unsupported journal");
        assert!(registry.begin("backup-server", "workspace:test", false).is_err());
        assert!(registry.list().is_err());
    }
}
