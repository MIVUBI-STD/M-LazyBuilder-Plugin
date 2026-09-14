use crate::engine::world_manager::{
    create_world,
    get_world_settings,
    get_world_task,
    list_world_tasks,
    list_worlds,
    start_archive_world,
    start_backup_world,
    start_delete_world,
    start_duplicate_world,
    start_export_world,
    start_import_world,
    start_restore_world,
    update_world_settings,
    upload_world_import,
    CreateWorldRequest,
    DeleteWorldRequest,
    DuplicateWorldRequest,
    ExportWorldRequest,
    ImportWorldRequest,
    ManagedWorldSummary,
    UpdateWorldSettingsRequest,
    WorldSettingsSnapshot,
    WorldTaskSnapshot,
};

#[tauri::command]
pub fn world_list() -> Result<Vec<ManagedWorldSummary>, String> { list_worlds() }

#[tauri::command]
pub fn world_create(request: CreateWorldRequest) -> Result<ManagedWorldSummary, String> {
    create_world(&request)
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

#[tauri::command]
pub fn world_task_list() -> Result<Vec<WorldTaskSnapshot>, String> { list_world_tasks() }

#[tauri::command]
pub fn world_task(task_id: String) -> Result<WorldTaskSnapshot, String> {
    get_world_task(&task_id)
}

#[tauri::command]
pub fn world_archive(world_id: String) -> Result<WorldTaskSnapshot, String> {
    start_archive_world(&world_id)
}

#[tauri::command]
pub fn world_restore(world_id: String) -> Result<WorldTaskSnapshot, String> {
    start_restore_world(&world_id)
}

#[tauri::command]
pub fn world_backup(world_id: String) -> Result<WorldTaskSnapshot, String> {
    start_backup_world(&world_id)
}

#[tauri::command]
pub fn world_duplicate(request: DuplicateWorldRequest) -> Result<WorldTaskSnapshot, String> {
    start_duplicate_world(&request)
}

#[tauri::command]
pub fn world_export(request: ExportWorldRequest) -> Result<WorldTaskSnapshot, String> {
    start_export_world(&request)
}

#[tauri::command]
pub fn world_delete(request: DeleteWorldRequest) -> Result<WorldTaskSnapshot, String> {
    start_delete_world(&request)
}

#[tauri::command]
pub fn world_import_pick() -> Option<String> {
    rfd::FileDialog::new()
        .add_filter("Minecraft world", &["zip", "mcworld"])
        .pick_file()
        .map(|path| path.to_string_lossy().to_string())
}

#[tauri::command]
pub fn world_import_upload(file_path: String) -> Result<String, String> {
    upload_world_import(&file_path)
}

#[tauri::command]
pub fn world_import(request: ImportWorldRequest) -> Result<WorldTaskSnapshot, String> {
    start_import_world(&request)
}
