use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::{OperationError, OperationRegistry};
use crate::engine::resource_settings::{self, ResourceUpdateRequest, ServerResourceProfile};
use crate::engine::server_start_lock::ServerStartLease;
use crate::engine::workspace_registry;
use tauri::{AppHandle, Manager};

#[tauri::command]
pub fn server_resource_profile() -> Result<ServerResourceProfile, String> {
    resource_settings::profile()
}

#[tauri::command]
pub async fn server_resource_save(
    app: AppHandle,
    request: ResourceUpdateRequest,
) -> CommandResult<ServerResourceProfile> {
    let active = workspace_registry::current()
        .map_err(CommandError::from)?
        .ok_or_else(|| CommandError::new("WORKSPACE_REQUIRED", "Open a server before changing its memory settings."))?;
    let resource = format!("workspace:{}", active.id);
    let operation = app
        .state::<OperationRegistry>()
        .begin_exclusive("save-server-resources", &resource, false)
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

        let _ = operations.set_phase(
            &operation_id,
            "saving",
            "Saving server memory settings",
            "Updating the canonical server runtime configuration for the next managed start.",
            None,
        );
        match resource_settings::save(request) {
            Ok(profile) => {
                let _ = operations.succeed(&operation_id, "Server memory settings saved");
                Ok(profile)
            }
            Err(message) => {
                let error = CommandError::recoverable("RESOURCE_SETTINGS_SAVE_FAILED", message, "Review server settings and retry");
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
                    message: "Server resource settings task ended unexpectedly".into(),
                    details: error.to_string(),
                    recoverable: true,
                },
            );
            Err(CommandError::new("TASK_FAILED", format!("Server resource settings task failed: {error}")))
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
