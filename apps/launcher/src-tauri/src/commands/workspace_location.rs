use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::OperationRegistry;
use crate::engine::{server_process_guard, workspace_registry};
use std::path::PathBuf;
use tauri::State;

#[tauri::command]
pub fn workspace_location_pick(id: String) -> CommandResult<Option<String>> {
    let entry = workspace_registry::get(&id).map_err(CommandError::from)?;
    Ok(rfd::FileDialog::new()
        .set_title(format!("Locate {}", entry.name))
        .pick_folder()
        .map(|path| path.display().to_string()))
}

#[tauri::command]
pub fn workspace_location_reconnect(
    operations: State<'_, OperationRegistry>,
    id: String,
    root_path: String,
) -> CommandResult<workspace_registry::WorkspaceEntry> {
    let resource = format!("workspace:{id}");
    if operations.has_active_for_resource(&resource).map_err(CommandError::from)? {
        return Err(CommandError::recoverable(
            "OPERATION_BUSY",
            "Wait for the active server operation to finish before changing its registered location.",
            "Open Activity",
        ));
    }

    let previous = workspace_registry::get(&id).map_err(CommandError::from)?;
    let previous_root = PathBuf::from(&previous.path);
    if previous_root.is_dir() {
        server_process_guard::ensure_root_not_running(&previous_root).map_err(CommandError::from)?;
    }

    workspace_registry::relocate(&id, &PathBuf::from(root_path))
        .map_err(|message| CommandError::recoverable("WORKSPACE_RECONNECT_FAILED", message, "Choose original server folder"))
}
