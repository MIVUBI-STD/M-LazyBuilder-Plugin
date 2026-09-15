use crate::commands::error::{CommandError, CommandResult};
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
pub async fn world_list() -> CommandResult<Vec<ManagedWorldSummary>> {
    run_blocking("World list", list_worlds).await
}

#[tauri::command]
pub async fn world_create(request: CreateWorldRequest) -> CommandResult<ManagedWorldSummary> {
    run_blocking("World create", move || create_world(&request)).await
}

#[tauri::command]
pub async fn world_settings(world_id: String) -> CommandResult<WorldSettingsSnapshot> {
    run_blocking("World settings", move || get_world_settings(&world_id)).await
}

#[tauri::command]
pub async fn world_update_settings(
    world_id: String,
    request: UpdateWorldSettingsRequest,
) -> CommandResult<WorldSettingsSnapshot> {
    run_blocking("World settings update", move || update_world_settings(&world_id, &request)).await
}

#[tauri::command]
pub async fn world_task_list() -> CommandResult<Vec<WorldTaskSnapshot>> {
    run_blocking("World task list", list_world_tasks).await
}

#[tauri::command]
pub async fn world_task(task_id: String) -> CommandResult<WorldTaskSnapshot> {
    run_blocking("World task status", move || get_world_task(&task_id)).await
}

#[tauri::command]
pub async fn world_archive(world_id: String) -> CommandResult<WorldTaskSnapshot> {
    run_blocking("World archive", move || start_archive_world(&world_id)).await
}

#[tauri::command]
pub async fn world_restore(world_id: String) -> CommandResult<WorldTaskSnapshot> {
    run_blocking("World restore", move || start_restore_world(&world_id)).await
}

#[tauri::command]
pub async fn world_backup(world_id: String) -> CommandResult<WorldTaskSnapshot> {
    run_blocking("World backup", move || start_backup_world(&world_id)).await
}

#[tauri::command]
pub async fn world_duplicate(request: DuplicateWorldRequest) -> CommandResult<WorldTaskSnapshot> {
    run_blocking("World duplicate", move || start_duplicate_world(&request)).await
}

#[tauri::command]
pub async fn world_export(request: ExportWorldRequest) -> CommandResult<WorldTaskSnapshot> {
    run_blocking("World export", move || start_export_world(&request)).await
}

#[tauri::command]
pub async fn world_delete(request: DeleteWorldRequest) -> CommandResult<WorldTaskSnapshot> {
    run_blocking("World delete", move || start_delete_world(&request)).await
}

#[tauri::command]
pub fn world_import_pick() -> Option<String> {
    rfd::FileDialog::new()
        .add_filter("Minecraft world", &["zip", "mcworld"])
        .pick_file()
        .map(|path| path.to_string_lossy().to_string())
}

#[tauri::command]
pub async fn world_import_upload(file_path: String) -> CommandResult<String> {
    run_blocking("World import upload", move || upload_world_import(&file_path)).await
}

#[tauri::command]
pub async fn world_import(request: ImportWorldRequest) -> CommandResult<WorldTaskSnapshot> {
    run_blocking("World import", move || start_import_world(&request)).await
}

fn classify_world_error(message: String) -> CommandError {
    if message.contains("desktop bridge protocol mismatch") {
        return CommandError::new("WORLD_PROTOCOL_MISMATCH", message);
    }
    if message.contains("desktop bridge is not ready") || message.contains("World-Manager") && message.contains("unavailable") {
        return CommandError::new("WORLD_BRIDGE_UNAVAILABLE", message);
    }
    CommandError::runtime(message)
}

async fn run_blocking<T, F>(label: &'static str, work: F) -> CommandResult<T>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, String> + Send + 'static,
{
    tauri::async_runtime::spawn_blocking(work)
        .await
        .map_err(|error| CommandError::new("TASK_FAILED", format!("{label} task failed: {error}")))?
        .map_err(classify_world_error)
}
