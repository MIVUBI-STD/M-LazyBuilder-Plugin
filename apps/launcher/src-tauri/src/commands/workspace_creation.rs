use crate::commands::error::{CommandError, CommandResult, RecoveryAction};
use crate::engine::operations::{OperationError, OperationRegistry};
use crate::engine::server_start_lock::ServerStartLease;
use crate::engine::{workspace_creation, workspace_registry};
use crate::engine::workspace_registry::WorkspaceEntry;
use std::path::PathBuf;
use tauri::State;

const WORKSPACE_LIBRARY_RESOURCE: &str = "workspace-library";

#[tauri::command]
pub fn workspace_create(
    operations: State<'_, OperationRegistry>,
    parent_path: String,
    name: String,
) -> CommandResult<WorkspaceEntry> {
    // Creating a workspace publishes and activates new workspace identity. Reuse
    // the canonical server-start lease so active-workspace selection cannot move
    // while another server is reading its start-time configuration.
    let _selection_lease = ServerStartLease::acquire().map_err(|message| {
        CommandError::recoverable_action(
            "SERVER_START_BUSY",
            message,
            RecoveryAction::WaitForServerStart,
        )
    })?;

    // Creating a workspace changes server-library truth, but it does not mutate
    // any existing server runtime. Keep library mutations serialized without
    // forcing unrelated running Paper servers to stop.
    if operations
        .list()
        .map_err(CommandError::from)?
        .iter()
        .any(|entry| !entry.state.is_terminal())
    {
        return Err(CommandError::recoverable_action(
            "OPERATION_BUSY",
            "Wait for the active Launcher operation to finish before creating another server.",
            RecoveryAction::OpenActivity,
        ));
    }

    let operation = operations
        .begin_exclusive("create-server", WORKSPACE_LIBRARY_RESOURCE, false)
        .map_err(|error| CommandError::recoverable_action("OPERATION_BUSY", error, RecoveryAction::OpenActivity))?;
    let operation_id = operation.id.clone();

    let _ = operations.set_phase(
        &operation_id,
        "staging",
        "Preparing server workspace",
        "Creating the new server in recovery-owned staging before publishing it to the selected location.",
        None,
    );

    match workspace_creation::create(&PathBuf::from(parent_path), &name) {
        Ok(entry) => {
            let _ = operations.set_phase(
                &operation_id,
                "publishing",
                "Publishing server workspace",
                "The staged workspace was published and registered with its stable LazyBuilder identity.",
                None,
            );
            let _ = operations.succeed(&operation_id, "Server created");
            Ok(entry)
        }
        Err(message) if message.starts_with("CREATE_RECOVERY_REQUIRED:") => {
            let message = message.trim_start_matches("CREATE_RECOVERY_REQUIRED:").trim().to_string();
            let error = CommandError::recoverable_action(
                "CREATE_RECOVERY_REQUIRED",
                message,
                RecoveryAction::RestartLauncher,
            );
            let _ = operations.require_recovery(
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
        Err(message) => {
            let error = CommandError::recoverable_action(
                "CREATE_FAILED",
                message,
                RecoveryAction::RetryOperation,
            );
            let _ = operations.fail(
                &operation_id,
                OperationError {
                    code: error.code.to_string(),
                    message: error.message.clone(),
                    details: error.details.clone(),
                    recoverable: true,
                },
            );
            let _ = workspace_registry::deactivate();
            Err(error)
        }
    }
}
