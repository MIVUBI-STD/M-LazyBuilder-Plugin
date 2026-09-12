use crate::engine::world_manager::{
    create_world,
    get_world_settings,
    get_world_task,
    list_world_tasks,
    list_worlds,
    load_world,
    start_archive_world,
    start_backup_world,
    start_clone_world,
    start_restore_world,
    unload_world,
    update_world_settings,
    CloneWorldRequest,
    CreateWorldRequest,
    ManagedWorldSummary,
    UpdateWorldSettingsRequest,
    WorldSettingsSnapshot,
    WorldTaskSnapshot,
};

#[tauri::command]
pub fn world_list() -> Result<Vec<ManagedWorldSummary>, String> { list_worlds() }
#[tauri::command]
pub fn world_create(request: CreateWorldRequest) -> Result<ManagedWorldSummary, String> { create_world(&request) }
#[tauri::command]
pub fn world_load(world_id: String) -> Result<ManagedWorldSummary, String> { load_world(&world_id) }
#[tauri::command]
pub fn world_unload(world_id: String) -> Result<ManagedWorldSummary, String> { unload_world(&world_id) }
#[tauri::command]
pub fn world_settings(world_id: String) -> Result<WorldSettingsSnapshot, String> { get_world_settings(&world_id) }
#[tauri::command]
pub fn world_update_settings(world_id: String, request: UpdateWorldSettingsRequest) -> Result<WorldSettingsSnapshot, String> { update_world_settings(&world_id, &request) }
#[tauri::command]
pub fn world_task_list() -> Result<Vec<WorldTaskSnapshot>, String> { list_world_tasks() }
#[tauri::command]
pub fn world_task(task_id: String) -> Result<WorldTaskSnapshot, String> { get_world_task(&task_id) }
#[tauri::command]
pub fn world_archive(world_id: String) -> Result<WorldTaskSnapshot, String> { start_archive_world(&world_id) }
#[tauri::command]
pub fn world_restore(world_id: String) -> Result<WorldTaskSnapshot, String> { start_restore_world(&world_id) }
#[tauri::command]
pub fn world_backup(world_id: String) -> Result<WorldTaskSnapshot, String> { start_backup_world(&world_id) }
#[tauri::command]
pub fn world_clone(request: CloneWorldRequest) -> Result<WorldTaskSnapshot, String> { start_clone_world(&request) }
