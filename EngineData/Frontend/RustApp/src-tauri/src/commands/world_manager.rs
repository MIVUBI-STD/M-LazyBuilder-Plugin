use crate::engine::world_manager::{list_worlds, ManagedWorldSummary};

#[tauri::command]
pub fn world_list() -> Result<Vec<ManagedWorldSummary>, String> {
    list_worlds()
}
