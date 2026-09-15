use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::{OperationError, OperationProgress, OperationRegistry};
use crate::engine::{server_backups, server_process_guard, workspace_registry};
use tauri::{AppHandle, Manager, State};

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
    let operation = app
        .state::<OperationRegistry>()
        .begin_exclusive("backup-server", &resource, false)
        .map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();

    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let entry = workspace_registry::get(&workspace_id).map_err(CommandError::from)?;

        let _ = operations.set_phase(
            &operation_id,
            "preflight",
            "Checking server state",
            "Confirming the server is offline and safe to snapshot.",
            None,
        );

        if let Err(message) = server_process_guard::ensure_root_not_running(std::path::Path::new(&entry.path)) {
            let error = CommandError::recoverable("SERVER_BUSY", message, "Stop server");
            fail_operation(&operations, &operation_id, &error, true);
            return Err(error);
        }

        let result = server_backups::create_tracked(&workspace_id, |phase, status, details, measured| {
            let progress = measured.map(|(current, total)| OperationProgress {
                current,
                total: Some(total),
                unit: "bytes".into(),
            });
            let _ = operations.set_phase(&operation_id, phase, status, details, progress);
        });

        match result {
            Ok(backup) => {
                let _ = operations.succeed(&operation_id, "Server backup created");
                Ok(backup)
            }
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
            let _ = app.state::<OperationRegistry>().fail(
                &join_operation_id,
                OperationError {
                    code: "TASK_FAILED".into(),
                    message: "Server backup task ended unexpectedly".into(),
                    details: error.to_string(),
                    recoverable: true,
                },
            );
            Err(CommandError::new("TASK_FAILED", format!("Server backup task failed: {error}")))
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
        return Err(CommandError::recoverable(
            "OPERATION_BUSY",
            "Wait for the active server operation to finish before deleting a restore point.",
            "Open Activity",
        ));
    }
    server_backups::delete(&workspace_id, &backup_id).map_err(CommandError::from)
}

fn fail_operation(operations: &OperationRegistry, operation_id: &str, error: &CommandError, recoverable: bool) {
    let _ = operations.fail(
        operation_id,
        OperationError {
            code: error.code.to_string(),
            message: error.message.clone(),
            details: error.details.clone(),
            recoverable,
        },
    );
}
