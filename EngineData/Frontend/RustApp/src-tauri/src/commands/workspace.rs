use crate::engine::workspace_registry::{self, WorkspaceEntry};
use std::path::PathBuf;

#[derive(Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WorkspaceState {
    pub active: Option<WorkspaceEntry>,
    pub recent: Vec<WorkspaceEntry>,
}

#[tauri::command]
pub fn workspace_state() -> Result<WorkspaceState, String> {
    Ok(WorkspaceState {
        active: workspace_registry::current()?,
        recent: workspace_registry::list()?,
    })
}

#[tauri::command]
pub fn workspace_pick_parent() -> Result<Option<String>, String> {
    Ok(rfd::FileDialog::new()
        .set_title("Choose where to create the LazyBuilder server")
        .pick_folder()
        .map(|path| path.display().to_string()))
}

#[tauri::command]
pub fn workspace_create(parent_path: String, name: String) -> Result<WorkspaceEntry, String> {
    workspace_registry::create(&PathBuf::from(parent_path), &name)
}

#[tauri::command]
pub fn workspace_open_picker() -> Result<Option<WorkspaceEntry>, String> {
    let Some(path) = rfd::FileDialog::new()
        .set_title("Open LazyBuilder server workspace")
        .pick_folder() else {
        return Ok(None);
    };
    workspace_registry::open(&path).map(Some)
}

#[tauri::command]
pub fn workspace_activate(id: String) -> Result<WorkspaceEntry, String> {
    workspace_registry::activate(&id)
}

#[tauri::command]
pub fn workspace_close() -> Result<(), String> {
    workspace_registry::deactivate()
}
