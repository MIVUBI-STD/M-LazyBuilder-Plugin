use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::{OperationError, OperationRegistry};
use crate::engine::server_health::{self, ServerHealthSnapshot};
use crate::engine::server_start_lock::ServerStartLease;
use crate::engine::{server_process_guard, server_repair, workspace_registry};
use tauri::{AppHandle, Manager};

#[tauri::command]
pub fn launcher_server_health(id: String) -> CommandResult<ServerHealthSnapshot> {
    server_health::inspect(&id).map_err(CommandError::from)
}

#[tauri::command]
pub fn launcher_server_repair_plan(id: String) -> CommandResult<server_repair::ServerRepairPlan> {
    server_repair::plan(&id).map_err(CommandError::from)
}

#[tauri::command]
pub async fn launcher_server_repair(
    app: AppHandle,
    id: String,
) -> CommandResult<server_repair::ServerRepairResult> {
    let resource = format!("workspace:{id}");
    let operation = app
        .state::<OperationRegistry>()
        .begin_exclusive("repair-server", &resource, false)
        .map_err(|error| CommandError::recoverable("OPERATION_BUSY", error, "Open Activity"))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();

    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let _start_lease = match ServerStartLease::acquire() {
            Ok(value) => value,
            Err(message) => {
                let error = CommandError::recoverable("SERVER_START_BUSY", message, "Wait for server start");
                fail_operation(&operations, &operation_id, &error);
                return Err(error);
            }
        };

        let entry = match workspace_registry::get(&id) {
            Ok(value) => value,
            Err(message) => {
                let error = CommandError::new("WORKSPACE_NOT_FOUND", message);
                fail_operation(&operations, &operation_id, &error);
                return Err(error);
            }
        };
        let _ = operations.set_phase(
            &operation_id,
            "preflight",
            "Checking repair safety",
            "Confirming the selected server is offline and still matches its registered workspace.",
            None,
        );
        if let Err(message) = server_process_guard::ensure_root_not_running(std::path::Path::new(&entry.path)) {
            let error = CommandError::recoverable("SERVER_BUSY", message, "Stop server");
            fail_operation(&operations, &operation_id, &error);
            return Err(error);
        }

        let resource_dir = task_app.path().resource_dir().ok();
        let result = server_repair::execute_tracked(&id, resource_dir.as_deref(), |phase, status, details| {
            let _ = operations.set_phase(&operation_id, phase, status, details, None);
        });

        match result {
            Ok(result) => {
                let status = if result.health.ready { "Server repaired and ready" } else { "Owned repairs completed; manual attention remains" };
                let _ = operations.succeed(&operation_id, status);
                Ok(result)
            }
            Err(message) => {
                let error = CommandError::recoverable("REPAIR_FAILED", message, "Review server health");
                fail_operation(&operations, &operation_id, &error);
                Err(error)
            }
        }
    });

    match task.await {
        Ok(result) => result,
        Err(error) => {
            let _ = app.state::<OperationRegistry>().require_recovery(
                &join_operation_id,
                OperationError {
                    code: "TASK_FAILED".into(),
                    message: "Server repair task ended unexpectedly".into(),
                    details: error.to_string(),
                    recoverable: true,
                },
            );
            Err(CommandError::new("TASK_FAILED", format!("Server repair task failed: {error}")))
        }
    }
}

fn fail_operation(operations: &OperationRegistry, operation_id: &str, error: &CommandError) {
    let _ = operations.fail(
        operation_id,
        OperationError {
            code: error.code.to_string(),
            message: error.message.clone(),
            details: error.details.clone(),
            recoverable: error.recoverable,
        },
    );
}
