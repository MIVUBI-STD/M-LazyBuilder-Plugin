use crate::engine::diagnostics;
use serde::{Deserialize, Serialize};
use std::collections::VecDeque;
use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::RwLock;
use std::time::{SystemTime, UNIX_EPOCH};

const MAX_OPERATION_HISTORY: usize = 100;
const OPERATION_JOURNAL_SCHEMA_VERSION: u32 = 1;
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

        let now = now_unix_seconds();
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
        if exclusive && guard.iter().any(|entry| entry.resource == resource && !entry.state.is_terminal()) {
            return Err(format!("Another launcher operation is already active for {resource}"));
        }
        let now = now_unix_seconds();
        let sequence = NEXT_OPERATION_ID.fetch_add(1, Ordering::Relaxed);
        let correlation_id = diagnostics::new_correlation_id("operation");
        let snapshot = OperationSnapshot {
            id: format!("op-{now}-{sequence}"),
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
            .any(|entry| entry.resource == resource && !entry.state.is_terminal()))
    }

    pub fn set_phase(&self, id: &str, phase: &str, status: &str, details: &str, progress: Option<OperationProgress>) -> Result<OperationSnapshot, String> {
        let result = self.mutate(id, |entry| { ensure_active(entry)?; entry.phase = phase.trim().to_string(); entry.status = status.trim().to_string(); entry.details = details.trim().to_string(); entry.progress = progress; Ok(()) })?;
        diagnostics::info_with_context(&result.correlation_id, &format!("operation phase id={} phase={} status={}", result.id, result.phase, result.status));
        Ok(result)
    }

    pub fn set_cancelable(&self, id: &str, can_cancel: bool) -> Result<OperationSnapshot, String> {
        self.mutate(id, |entry| { ensure_active(entry)?; if entry.cancel_requested && can_cancel { return Err("Cancellation is already pending for this launcher operation".into()); } entry.can_cancel = can_cancel; Ok(()) })
    }

    pub fn add_warning(&self, id: &str, warning: &str) -> Result<OperationSnapshot, String> {
        self.mutate(id, |entry| { ensure_active(entry)?; let warning = warning.trim(); if !warning.is_empty() && !entry.warnings.iter().any(|existing| existing == warning) { entry.warnings.push(warning.to_string()); } Ok(()) })
    }

    pub fn request_cancel(&self, id: &str) -> Result<OperationSnapshot, String> {
        let result = self.mutate(id, |entry| { ensure_active(entry)?; if !entry.can_cancel { return Err("This launcher operation cannot be cancelled safely".into()); } entry.cancel_requested = true; entry.state = OperationState::Cancelling; entry.status = "Cancelling".into(); Ok(()) })?;
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
            entry.progress = entry.progress.take().map(|mut progress| { if let Some(total) = progress.total { progress.current = total; } progress });
            entry.completed_at_unix_seconds = Some(now);
            Ok(())
        })?;
        let message = format!("operation finished id={} state={:?} status={}", result.id, result.state, result.status);
        if matches!(result.state, OperationState::Failed | OperationState::RecoveryRequired) { diagnostics::error_with_context(&result.correlation_id, &message); } else { diagnostics::info_with_context(&result.correlation_id, &message); }
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
    if !path.is_file() { return Ok(VecDeque::new()); }
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
    let previous = path.with_extension("json.previous");
    let journal = OperationJournal { schema_version: OPERATION_JOURNAL_SCHEMA_VERSION, entries: entries.clone() };
    let text = serde_json::to_string_pretty(&journal).map_err(|error| error.to_string())?;
    {
        let mut file = OpenOptions::new().create(true).truncate(true).write(true).open(&incoming)
            .map_err(|error| format!("Could not write Launcher operation journal staging file: {error}"))?;
        file.write_all(text.as_bytes()).map_err(|error| error.to_string())?;
        file.sync_all().map_err(|error| format!("Could not flush Launcher operation journal: {error}"))?;
    }

    if path.exists() {
        let _ = fs::remove_file(&previous);
        fs::rename(path, &previous).map_err(|error| format!("Could not preserve previous Launcher operation journal: {error}"))?;
        match fs::rename(&incoming, path) {
            Ok(()) => {
                let _ = fs::remove_file(previous);
                Ok(())
            }
            Err(error) => {
                let _ = fs::rename(&previous, path);
                Err(format!("Could not publish Launcher operation journal: {error}"))
            }
        }
    } else {
        fs::rename(incoming, path).map_err(|error| format!("Could not publish Launcher operation journal: {error}"))
    }
}

fn ensure_active(entry: &OperationSnapshot) -> Result<(), String> { if entry.state.is_terminal() { Err("Launcher operation is already finished".into()) } else { Ok(()) } }
fn trim_history(entries: &mut VecDeque<OperationSnapshot>) { while entries.len() > MAX_OPERATION_HISTORY { if let Some(position) = entries.iter().rposition(|entry| entry.state.is_terminal()) { entries.remove(position); } else { break; } } }
fn now_unix_seconds() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).map(|value| value.as_secs()).unwrap_or_default() }

#[cfg(test)]
mod tests {
    use super::*;

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
        for index in 0..(MAX_OPERATION_HISTORY + 5) { let operation = registry.begin("finished", &format!("resource:{index}"), false).unwrap(); registry.succeed(&operation.id, "done").unwrap(); }
        let list = registry.list().unwrap();
        assert!(list.len() <= MAX_OPERATION_HISTORY + 1);
        assert!(list.iter().any(|operation| operation.id == active.id));
    }

    #[test]
    fn low_risk_interruption_classification_is_explicit() {
        assert!(interruption_is_low_risk("export-support-bundle"));
        assert!(!interruption_is_low_risk("restore-server"));
        assert!(!interruption_is_low_risk("update-paper"));
    }

    #[test]
    fn disabled_registry_refuses_new_operations() {
        let registry = OperationRegistry::disabled("unsupported journal");
        assert!(registry.begin("backup-server", "workspace:test", false).is_err());
        assert!(registry.list().is_err());
    }
}
