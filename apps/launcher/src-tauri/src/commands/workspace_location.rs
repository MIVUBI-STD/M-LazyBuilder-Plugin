use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::OperationRegistry;
use crate::engine::workspace_registry;
use std::path::PathBuf;
use tauri::State;

#[tauri::command]
pub fn workspace_location_pick(id: String) -> CommandResult<Option<String>> {
    let entry = workspace_registry::get(&id).map_err(CommandError::from)?;
    let current = PathBuf::from(&entry.path);
    if current.is_dir() {
        return Err(CommandError::new(
            "WORKSPACE_LOCATION_AVAILABLE",
            "This server location is still available. Locate is only for reconnecting a missing or moved server.",
        ));
    }
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
    if PathBuf::from(&previous.path).is_dir() {
        return Err(CommandError::new(
            "WORKSPACE_LOCATION_AVAILABLE",
            "This server location is still available. LazyBuilder will not repoint an existing server to a second copy.",
        ));
    }

    workspace_registry::relocate(&id, &PathBuf::from(root_path))
        .map_err(|message| CommandError::recoverable("WORKSPACE_RECONNECT_FAILED", message, "Choose original server folder"))
}
