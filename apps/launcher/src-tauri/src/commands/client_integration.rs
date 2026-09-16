use crate::commands::error::{CommandError, CommandResult};
use crate::engine::client_integration::{self, ClientIntegrationStatus};
use crate::engine::operations::{OperationError, OperationRegistry};
use tauri::{AppHandle, Manager};

const CLIENT_INTEGRATION_RESOURCE: &str = "client:modrinth";

#[tauri::command]
pub async fn client_integration_status(app: AppHandle) -> Result<ClientIntegrationStatus, String> {
    let resource_dir = app.path().resource_dir().ok();
    run_blocking("Client setup status", move || {
        client_integration::status(resource_dir.as_deref())
    })
    .await
}

#[tauri::command]
pub async fn client_integration_select_profile(
    app: AppHandle,
    profile_path: String,
) -> CommandResult<ClientIntegrationStatus> {
    let resource_dir = app.path().resource_dir().ok();
    run_client_mutation(
        app,
        "select-client-profile",
        "selecting",
        "Selecting client profile",
        "CLIENT_PROFILE_SELECTION_FAILED",
        "Review Client Setup and retry",
        move || {
            client_integration::select_profile(&profile_path)?;
            client_integration::status(resource_dir.as_deref())
        },
    )
    .await
}

#[tauri::command]
pub async fn client_integration_pick_profile(app: AppHandle) -> CommandResult<ClientIntegrationStatus> {
    let resource_dir = app.path().resource_dir().ok();
    let selected = rfd::FileDialog::new()
        .set_title("Select Modrinth profile")
        .pick_folder();

    let Some(profile_path) = selected else {
        return client_integration::status(resource_dir.as_deref()).map_err(CommandError::from);
    };

    run_client_mutation(
        app,
        "select-client-profile",
        "selecting",
        "Selecting client profile",
        "CLIENT_PROFILE_SELECTION_FAILED",
        "Review Client Setup and retry",
        move || {
            client_integration::select_manual_profile(&profile_path)?;
            client_integration::status(resource_dir.as_deref())
        },
    )
    .await
}

#[tauri::command]
pub async fn client_integration_sync(app: AppHandle) -> CommandResult<ClientIntegrationStatus> {
    let resource_dir = app.path().resource_dir().ok();
    run_client_mutation(
        app,
        "sync-client-components",
        "syncing",
        "Syncing LazyBuilder client components",
        "CLIENT_SYNC_FAILED",
        "Review Client Setup and retry",
        move || {
            client_integration::sync(resource_dir.as_deref())?;
            client_integration::status(resource_dir.as_deref())
        },
    )
    .await
}

async fn run_client_mutation<T, F>(
    app: AppHandle,
    kind: &'static str,
    phase: &'static str,
    status: &'static str,
    failure_code: &'static str,
    failure_action: &'static str,
    work: F,
) -> CommandResult<T>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, String> + Send + 'static,
{
    let operation = app
        .state::<OperationRegistry>()
        .begin_exclusive(kind, CLIENT_INTEGRATION_RESOURCE, false)
        .map_err(|error| CommandError::recoverable("OPERATION_BUSY", error, "Open Activity"))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();

    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let _ = operations.set_phase(
            &operation_id,
            phase,
            status,
            "Client integration mutations are serialized against the selected Modrinth profile.",
            None,
        );
        match work() {
            Ok(result) => {
                let _ = operations.succeed(&operation_id, status);
                Ok(result)
            }
            Err(message) => {
                let error = CommandError::recoverable(failure_code, message, failure_action);
                let _ = operations.fail(
                    &operation_id,
                    OperationError {
                        code: error.code.to_string(),
                        message: error.message.clone(),
                        details: error.details.clone(),
                        recoverable: true,
                    },
                );
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
                    message: "Client integration task ended unexpectedly".into(),
                    details: error.to_string(),
                    recoverable: true,
                },
            );
            Err(CommandError::new("TASK_FAILED", format!("Client integration task failed: {error}")))
        }
    }
}

async fn run_blocking<T, F>(label: &'static str, work: F) -> Result<T, String>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, String> + Send + 'static,
{
    tauri::async_runtime::spawn_blocking(work)
        .await
        .map_err(|error| format!("{label} task failed: {error}"))?
}
