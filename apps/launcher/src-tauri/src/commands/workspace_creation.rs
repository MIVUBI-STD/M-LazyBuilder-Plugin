use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::{OperationError, OperationRegistry};
use crate::engine::{server_process_guard, workspace_creation, workspace_registry};
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
    // Creating a workspace changes server-library truth. Keep this deliberately
    // conservative: do not create while any other Launcher mutation is active or
    // while any registered Paper process is still running.
    if operations
        .list()
        .map_err(CommandError::from)?
        .iter()
        .any(|entry| !entry.state.is_terminal())
    {
        return Err(CommandError::recoverable(
            "OPERATION_BUSY",
            "Wait for the active Launcher operation to finish before creating another server.",
            "Open Activity",
        ));
    }
    server_process_guard::ensure_no_running_paper_except(None).map_err(CommandError::from)?;

    let operation = operations
        .begin_exclusive("create-server", WORKSPACE_LIBRARY_RESOURCE, false)
        .map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
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
            let error = CommandError::recoverable("CREATE_RECOVERY_REQUIRED", message, "Restart LazyBuilder");
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
            let error = CommandError::recoverable("CREATE_FAILED", message, "Retry server creation");
            let _ = operations.fail(
                &operation_id,
                OperationError {
                    code: error.code.to_string(),
                    message: error.message.clone(),
                    details: error.details.clone(),
                    recoverable: true,
                },
            );
            // A failed staged create can leave a registry entry only if recovery
            // metadata is also preserved. Normal failures are expected to have
            // rolled back their staging before reaching this boundary.
            let _ = workspace_registry::deactivate();
            Err(error)
        }
    }
}
