use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::{OperationError, OperationProgress, OperationRegistry};
use crate::engine::server_start_lock::ServerStartLease;
use crate::engine::{server_backups, server_process_guard, server_restore, workspace_registry};
use tauri::{AppHandle, Manager, State};

const MIN_PROGRESS_JOURNAL_STEP_BYTES: u64 = 16 * 1024 * 1024;

#[tauri::command]
pub fn server_backup_list(workspace_id: String) -> CommandResult<Vec<server_backups::ServerBackupSummary>> {
    server_backups::list(&workspace_id).map_err(CommandError::from)
}

#[tauri::command]
pub fn server_backup_estimate(workspace_id: String) -> CommandResult<server_backups::ServerBackupEstimate> {
    let entry = workspace_registry::get(&workspace_id).map_err(CommandError::from)?;
    server_process_guard::ensure_root_not_running(std::path::Path::new(&entry.path)).map_err(CommandError::from)?;
    server_backups::estimate(&workspace_id).map_err(CommandError::from)
}

#[tauri::command]
pub async fn server_backup_create(app: AppHandle, workspace_id: String) -> CommandResult<server_backups::ServerBackupSummary> {
    let resource = format!("workspace:{workspace_id}");
    let operation = app.state::<OperationRegistry>().begin_exclusive("backup-server", &resource, false)
        .map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();

    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let _start_lease = match ServerStartLease::acquire() {
            Ok(value) => value,
            Err(message) => {
                let error = CommandError::recoverable("SERVER_START_BUSY", message, "Wait for server start");
                fail_operation(&operations, &operation_id, &error, true);
                return Err(error);
            }
        };
        let entry = workspace_registry::get(&workspace_id).map_err(CommandError::from)?;
        let _ = operations.set_phase(&operation_id, "preflight", "Checking server state", "Confirming the server is offline and safe to snapshot.", None);
        if let Err(message) = server_process_guard::ensure_root_not_running(std::path::Path::new(&entry.path)) {
            let error = CommandError::recoverable("SERVER_BUSY", message, "Stop server");
            fail_operation(&operations, &operation_id, &error, true);
            return Err(error);
        }

        // Backup copy callbacks can fire once per 1 MiB chunk. Persisting every callback
        // would rewrite the complete operation journal tens of thousands of times on a
        // large server. Semantic phase changes remain immediate/durable; byte progress is
        // sampled to roughly 1% (with a 16 MiB minimum step).
        let mut reporter = DurableProgressReporter::new(&operations, &operation_id);
        let result = server_backups::create_tracked(&workspace_id, |phase, status, details, measured| {
            reporter.report(phase, status, details, measured);
        });
        match result {
            Ok(backup) => { let _ = operations.succeed(&operation_id, "Server backup created"); Ok(backup) }
            Err(message) => {
                let error = CommandError::recoverable("BACKUP_FAILED", message, "Retry backup");
                fail_operation(&operations, &operation_id, &error, true);
                Err(error)
            }
        }
    });

    match task.await {
        Ok(result) => result,
        Err(error) => {
            let _ = app.state::<OperationRegistry>().fail(&join_operation_id, OperationError {
                code: "TASK_FAILED".into(), message: "Server backup task ended unexpectedly".into(), details: error.to_string(), recoverable: true,
            });
            Err(CommandError::new("TASK_FAILED", format!("Server backup task failed: {error}")))
        }
    }
}

#[tauri::command]
pub async fn server_backup_restore(
    app: AppHandle,
    workspace_id: String,
    backup_id: String,
) -> CommandResult<server_restore::ServerRestoreResult> {
    let resource = format!("workspace:{workspace_id}");
    let operation = app.state::<OperationRegistry>().begin_exclusive("restore-server", &resource, false)
        .map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();

    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let _start_lease = match ServerStartLease::acquire() {
            Ok(value) => value,
            Err(message) => {
                let error = CommandError::recoverable("SERVER_START_BUSY", message, "Wait for server start");
                fail_operation(&operations, &operation_id, &error, true);
                return Err(error);
            }
        };
        let entry = workspace_registry::get(&workspace_id).map_err(CommandError::from)?;
        let _ = operations.set_phase(&operation_id, "preflight", "Checking server state", "Restore requires an offline, process-free server workspace.", None);
        if let Err(message) = server_process_guard::ensure_root_not_running(std::path::Path::new(&entry.path)) {
            let error = CommandError::recoverable("SERVER_BUSY", message, "Stop server");
            fail_operation(&operations, &operation_id, &error, true);
            return Err(error);
        }

        let _ = operations.set_phase(
            &operation_id,
            "integrity-check",
            "Verifying restore point integrity",
            "Validating backup identity, file inventory, sizes, and SHA-256 checksums before touching the current server.",
            None,
        );
        match server_backups::verify(&workspace_id, &backup_id) {
            Ok(report) if report.status == server_backups::BackupIntegrityStatus::LegacyUnverified => {
                let _ = operations.add_warning(
                    &operation_id,
                    "This restore point uses the legacy backup format and has no per-file checksum manifest. Identity validation will still be enforced.",
                );
            }
            Ok(_) => {}
            Err(message) => {
                let error = CommandError::recoverable(
                    "BACKUP_INTEGRITY_FAILED",
                    format!("Restore point integrity verification failed: {message}"),
                    "Choose another restore point",
                );
                fail_operation(&operations, &operation_id, &error, true);
                return Err(error);
            }
        }

        let mut reporter = DurableProgressReporter::new(&operations, &operation_id);
        let result = server_restore::restore_tracked(&workspace_id, &backup_id, |phase, status, details, measured| {
            reporter.report(phase, status, details, measured);
        });
        match result {
            Ok(result) => {
                if result.cleanup_pending { let _ = operations.add_warning(&operation_id, "Restore succeeded; rollback staging cleanup will be retried on next startup."); }
                let _ = operations.succeed(&operation_id, "Server restored");
                Ok(result)
            }
            Err(failure) if failure.recovery_required => {
                let error = CommandError::recoverable("RESTORE_RECOVERY_REQUIRED", failure.message.clone(), "Restart LazyBuilder");
                let _ = operations.require_recovery(&operation_id, OperationError {
                    code: error.code.to_string(), message: error.message.clone(), details: error.details.clone(), recoverable: true,
                });
                Err(error)
            }
            Err(failure) => {
                let error = CommandError::recoverable("RESTORE_FAILED", failure.message, "Retry restore");
                fail_operation(&operations, &operation_id, &error, true);
                Err(error)
            }
        }
    });

    match task.await {
        Ok(result) => result,
        Err(error) => {
            let _ = app.state::<OperationRegistry>().require_recovery(&join_operation_id, OperationError {
                code: "TASK_FAILED".into(), message: "Server restore task ended unexpectedly".into(), details: error.to_string(), recoverable: true,
            });
            Err(CommandError::new("TASK_FAILED", format!("Server restore task failed: {error}")))
        }
    }
}

#[tauri::command]
pub fn server_backup_delete(
    operations: State<'_, OperationRegistry>,
    workspace_id: String,
    backup_id: String,
) -> CommandResult<()> {
    let resource = format!("workspace:{workspace_id}");
    if operations.has_active_for_resource(&resource).map_err(CommandError::from)? {
        return Err(CommandError::recoverable("OPERATION_BUSY", "Wait for the active server operation to finish before deleting a restore point.", "Open Activity"));
    }
    server_backups::delete(&workspace_id, &backup_id).map_err(CommandError::from)
}

struct DurableProgressReporter<'a> {
    operations: &'a OperationRegistry,
    operation_id: &'a str,
    last_phase: String,
    last_status: String,
    last_current: u64,
    last_total: Option<u64>,
}

impl<'a> DurableProgressReporter<'a> {
    fn new(operations: &'a OperationRegistry, operation_id: &'a str) -> Self {
        Self {
            operations,
            operation_id,
            last_phase: String::new(),
            last_status: String::new(),
            last_current: 0,
            last_total: None,
        }
    }

    fn report(&mut self, phase: &str, status: &str, details: &str, measured: Option<(u64, u64)>) {
        let semantic_changed = self.last_phase != phase || self.last_status != status;
        let progress_changed = measured.is_some_and(|(current, total)| {
            should_persist_progress(self.last_current, self.last_total, current, total)
        });
        if !semantic_changed && !progress_changed {
            return;
        }

        let progress = measured.map(|(current, total)| OperationProgress {
            current,
            total: Some(total),
            unit: "bytes".into(),
        });
        if self.operations.set_phase(self.operation_id, phase, status, details, progress).is_ok() {
            self.last_phase = phase.to_string();
            self.last_status = status.to_string();
            if let Some((current, total)) = measured {
                self.last_current = current;
                self.last_total = Some(total);
            } else {
                self.last_current = 0;
                self.last_total = None;
            }
        }
    }
}

fn should_persist_progress(last_current: u64, last_total: Option<u64>, current: u64, total: u64) -> bool {
    if last_total != Some(total) || current >= total {
        return true;
    }
    let step = (total / 100).max(MIN_PROGRESS_JOURNAL_STEP_BYTES).max(1);
    current.saturating_sub(last_current) >= step
}

fn fail_operation(operations: &OperationRegistry, operation_id: &str, error: &CommandError, recoverable: bool) {
    let _ = operations.fail(operation_id, OperationError {
        code: error.code.to_string(), message: error.message.clone(), details: error.details.clone(), recoverable,
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn progress_journal_updates_are_bounded_for_large_copies() {
        let total = 100 * 1024 * 1024 * 1024u64;
        let step = (total / 100).max(MIN_PROGRESS_JOURNAL_STEP_BYTES);
        assert!(step >= 1024 * 1024 * 1024);
        assert!(!should_persist_progress(0, Some(total), step - 1, total));
        assert!(should_persist_progress(0, Some(total), step, total));
        assert!(should_persist_progress(step, Some(total), total, total));
    }

    #[test]
    fn progress_journal_uses_minimum_step_for_smaller_copies() {
        let total = 100 * 1024 * 1024u64;
        assert!(!should_persist_progress(0, Some(total), MIN_PROGRESS_JOURNAL_STEP_BYTES - 1, total));
        assert!(should_persist_progress(0, Some(total), MIN_PROGRESS_JOURNAL_STEP_BYTES, total));
    }
}