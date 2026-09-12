use crate::engine::world_manager::{
    create_world,
    get_world_settings,
    list_worlds,
    load_world,
    unload_world,
    update_world_settings,
    CreateWorldRequest,
    ManagedWorldSummary,
    UpdateWorldSettingsRequest,
    WorldSettingsSnapshot,
};

#[tauri::command]
pub fn world_list() -> Result<Vec<ManagedWorldSummary>, String> {
    list_worlds()
}

#[tauri::command]
pub fn world_create(request: CreateWorldRequest) -> Result<ManagedWorldSummary, String> {
    create_world(&request)
}

#[tauri::command]
pub fn world_load(world_id: String) -> Result<ManagedWorldSummary, String> {
    load_world(&world_id)
}

#[tauri::command]
pub fn world_unload(world_id: String) -> Result<ManagedWorldSummary, String> {
    unload_world(&world_id)
}

#[tauri::command]
pub fn world_settings(world_id: String) -> Result<WorldSettingsSnapshot, String> {
    get_world_settings(&world_id)
}

#[tauri::command]
pub fn world_update_settings(
    world_id: String,
    request: UpdateWorldSettingsRequest,
) -> Result<WorldSettingsSnapshot, String> {
    update_world_settings(&world_id, &request)
}
