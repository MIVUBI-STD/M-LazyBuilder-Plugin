use serde::Serialize;
use std::collections::VecDeque;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::RwLock;
use std::time::{SystemTime, UNIX_EPOCH};

const MAX_OPERATION_HISTORY: usize = 100;
static NEXT_OPERATION_ID: AtomicU64 = AtomicU64::new(1);

#[derive(Clone, Debug, PartialEq, Eq, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum OperationState {
    Queued,
    Running,
    Succeeded,
    Failed,
    Cancelling,
    Cancelled,
    RecoveryRequired,
}

impl OperationState {
    pub fn is_terminal(&self) -> bool {
        matches!(
            self,
            Self::Succeeded | Self::Failed | Self::Cancelled | Self::RecoveryRequired
        )
    }
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OperationProgress {
    pub current: u64,
    pub total: Option<u64>,
    pub unit: String,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OperationError {
    pub code: String,
    pub message: String,
    pub details: String,
    pub recoverable: bool,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct OperationSnapshot {
    pub id: String,
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

#[derive(Default)]
pub struct OperationRegistry {
    entries: RwLock<VecDeque<OperationSnapshot>>,
}

impl OperationRegistry {
    pub fn begin(&self, kind: &str, resource: &str, can_cancel: bool) -> Result<OperationSnapshot, String> {
        let now = now_unix_seconds();
        let sequence = NEXT_OPERATION_ID.fetch_add(1, Ordering::Relaxed);
        let snapshot = OperationSnapshot {
            id: format!("op-{now}-{sequence}"),
            kind: kind.trim().to_string(),
            resource: resource.trim().to_string(),
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
        if snapshot.kind.is_empty() {
            return Err("Operation kind is required".into());
        }
        if snapshot.resource.is_empty() {
            return Err("Operation resource is required".into());
        }

        let mut entries = self.entries.write().map_err(|_| "operation registry lock poisoned".to_string())?;
        entries.push_front(snapshot.clone());
        trim_history(&mut entries);
        Ok(snapshot)
    }

    pub fn list(&self) -> Result<Vec<OperationSnapshot>, String> {
        self.entries
            .read()
            .map_err(|_| "operation registry lock poisoned".to_string())
            .map(|entries| entries.iter().cloned().collect())
    }

    pub fn get(&self, id: &str) -> Result<OperationSnapshot, String> {
        self.entries
            .read()
            .map_err(|_| "operation registry lock poisoned".to_string())?
            .iter()
            .find(|entry| entry.id == id)
            .cloned()
            .ok_or_else(|| "Launcher operation was not found".to_string())
    }

    pub fn set_phase(
        &self,
        id: &str,
        phase: &str,
        status: &str,
        details: &str,
        progress: Option<OperationProgress>,
    ) -> Result<OperationSnapshot, String> {
        self.mutate(id, |entry| {
            ensure_active(entry)?;
            entry.phase = phase.trim().to_string();
            entry.status = status.trim().to_string();
            entry.details = details.trim().to_string();
            entry.progress = progress;
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
        self.mutate(id, |entry| {
            ensure_active(entry)?;
            if !entry.can_cancel {
                return Err("This launcher operation cannot be cancelled safely".into());
            }
            entry.cancel_requested = true;
            entry.state = OperationState::Cancelling;
            entry.status = "Cancelling".into();
            Ok(())
        })
    }

    pub fn cancellation_requested(&self, id: &str) -> Result<bool, String> {
        Ok(self.get(id)?.cancel_requested)
    }

    pub fn succeed(&self, id: &str, status: &str) -> Result<OperationSnapshot, String> {
        self.finish(id, OperationState::Succeeded, status, None)
    }

    pub fn cancel(&self, id: &str, status: &str) -> Result<OperationSnapshot, String> {
        self.finish(id, OperationState::Cancelled, status, None)
    }

    pub fn fail(&self, id: &str, error: OperationError) -> Result<OperationSnapshot, String> {
        let status = error.message.clone();
        self.finish(id, OperationState::Failed, &status, Some(error))
    }

    pub fn require_recovery(&self, id: &str, error: OperationError) -> Result<OperationSnapshot, String> {
        let status = error.message.clone();
        self.finish(id, OperationState::RecoveryRequired, &status, Some(error))
    }

    fn finish(
        &self,
        id: &str,
        state: OperationState,
        status: &str,
        error: Option<OperationError>,
    ) -> Result<OperationSnapshot, String> {
        self.mutate(id, |entry| {
            ensure_active(entry)?;
            let now = now_unix_seconds();
            entry.state = state;
            entry.status = status.trim().to_string();
            entry.error = error;
            entry.progress = entry.progress.take().map(|mut progress| {
                if let Some(total) = progress.total {
                    progress.current = total;
                }
                progress
            });
            entry.completed_at_unix_seconds = Some(now);
            Ok(())
        })
    }

    fn mutate<F>(&self, id: &str, mutation: F) -> Result<OperationSnapshot, String>
    where
        F: FnOnce(&mut OperationSnapshot) -> Result<(), String>,
    {
        let mut entries = self.entries.write().map_err(|_| "operation registry lock poisoned".to_string())?;
        let entry = entries
            .iter_mut()
            .find(|entry| entry.id == id)
            .ok_or_else(|| "Launcher operation was not found".to_string())?;
        mutation(entry)?;
        entry.updated_at_unix_seconds = now_unix_seconds();
        Ok(entry.clone())
    }
}

fn ensure_active(entry: &OperationSnapshot) -> Result<(), String> {
    if entry.state.is_terminal() {
        return Err("Launcher operation is already finished".into());
    }
    Ok(())
}

fn trim_history(entries: &mut VecDeque<OperationSnapshot>) {
    while entries.len() > MAX_OPERATION_HISTORY {
        if let Some(position) = entries.iter().rposition(|entry| entry.state.is_terminal()) {
            entries.remove(position);
        } else {
            break;
        }
    }
}

fn now_unix_seconds() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|value| value.as_secs())
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn operation_lifecycle_has_one_terminal_transition() {
        let registry = OperationRegistry::default();
        let started = registry.begin("duplicate-server", "workspace:test", true).unwrap();
        assert_eq!(started.state, OperationState::Running);

        let running = registry
            .set_phase(
                &started.id,
                "copying",
                "Copying server files",
                "",
                Some(OperationProgress { current: 25, total: Some(100), unit: "bytes".into() }),
            )
            .unwrap();
        assert_eq!(running.progress.unwrap().current, 25);

        let finished = registry.succeed(&started.id, "Server duplicated").unwrap();
        assert_eq!(finished.state, OperationState::Succeeded);
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
}
