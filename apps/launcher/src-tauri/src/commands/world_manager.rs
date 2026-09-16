use crate::commands::error::{CommandError, CommandResult};
use crate::engine::server_start_lock::ServerStartLease;
use crate::engine::workspace_registry;
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
    let target = active_world_target()?;
    run_targeted_read("World list", target, list_worlds).await
}

#[tauri::command]
pub async fn world_create(request: CreateWorldRequest) -> CommandResult<ManagedWorldSummary> {
    let target = active_world_target()?;
    run_targeted_mutation("World create", target, move || create_world(&request)).await
}

#[tauri::command]
pub async fn world_settings(world_id: String) -> CommandResult<WorldSettingsSnapshot> {
    let target = active_world_target()?;
    run_targeted_read("World settings", target, move || get_world_settings(&world_id)).await
}

#[tauri::command]
pub async fn world_update_settings(
    world_id: String,
    request: UpdateWorldSettingsRequest,
) -> CommandResult<WorldSettingsSnapshot> {
    let target = active_world_target()?;
    run_targeted_mutation("World settings update", target, move || update_world_settings(&world_id, &request)).await
}

#[tauri::command]
pub async fn world_task_list() -> CommandResult<Vec<WorldTaskSnapshot>> {
    let target = active_world_target()?;
    run_targeted_read("World task list", target, list_world_tasks).await
}

#[tauri::command]
pub async fn world_task(task_id: String) -> CommandResult<WorldTaskSnapshot> {
    let target = active_world_target()?;
    run_targeted_read("World task status", target, move || get_world_task(&task_id)).await
}

#[tauri::command]
pub async fn world_archive(world_id: String) -> CommandResult<WorldTaskSnapshot> {
    let target = active_world_target()?;
    run_targeted_mutation("World archive", target, move || start_archive_world(&world_id)).await
}

#[tauri::command]
pub async fn world_restore(world_id: String) -> CommandResult<WorldTaskSnapshot> {
    let target = active_world_target()?;
    run_targeted_mutation("World restore", target, move || start_restore_world(&world_id)).await
}

#[tauri::command]
pub async fn world_backup(world_id: String) -> CommandResult<WorldTaskSnapshot> {
    let target = active_world_target()?;
    run_targeted_mutation("World backup", target, move || start_backup_world(&world_id)).await
}

#[tauri::command]
pub async fn world_duplicate(request: DuplicateWorldRequest) -> CommandResult<WorldTaskSnapshot> {
    let target = active_world_target()?;
    run_targeted_mutation("World duplicate", target, move || start_duplicate_world(&request)).await
}

#[tauri::command]
pub async fn world_export(request: ExportWorldRequest) -> CommandResult<WorldTaskSnapshot> {
    let target = active_world_target()?;
    run_targeted_mutation("World export", target, move || start_export_world(&request)).await
}

#[tauri::command]
pub async fn world_delete(request: DeleteWorldRequest) -> CommandResult<WorldTaskSnapshot> {
    let target = active_world_target()?;
    run_targeted_mutation("World delete", target, move || start_delete_world(&request)).await
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
    let target = active_world_target()?;
    run_targeted_mutation("World import upload", target, move || upload_world_import(&file_path)).await
}

#[tauri::command]
pub async fn world_import(request: ImportWorldRequest) -> CommandResult<WorldTaskSnapshot> {
    let target = active_world_target()?;
    run_targeted_mutation("World import", target, move || start_import_world(&request)).await
}

fn active_world_target() -> CommandResult<String> {
    workspace_registry::current()
        .map_err(CommandError::from)?
        .map(|workspace| workspace.id)
        .ok_or_else(|| CommandError::new("WORKSPACE_REQUIRED", "Open a server before using World Manager."))
}

fn ensure_world_target(target: &str) -> Result<(), String> {
    let current = workspace_registry::current()?
        .ok_or_else(|| "The World Manager target server is no longer open.".to_string())?;
    if current.id != target {
        return Err("The selected server changed while this World Manager request was running. Retry the action on the intended server.".into());
    }
    Ok(())
}

fn classify_world_error(message: String) -> CommandError {
    if message.contains("selected server changed") || message.contains("target server is no longer open") {
        return CommandError::recoverable("WORLD_TARGET_CHANGED", message, "Retry on selected server");
    }
    if message.contains("desktop bridge protocol mismatch") {
        return CommandError::new("WORLD_PROTOCOL_MISMATCH", message);
    }
    if message.contains("desktop bridge is not ready") || message.contains("World-Manager") && message.contains("unavailable") {
        return CommandError::new("WORLD_BRIDGE_UNAVAILABLE", message);
    }
    CommandError::runtime(message)
}

/// Read-only calls are allowed to overlap workspace navigation, but their result
/// is discarded if selection changed before completion. This avoids making the
/// frequent task/status polls a navigation lock while preventing stale server
/// data from being presented under another workspace.
async fn run_targeted_read<T, F>(label: &'static str, target: String, work: F) -> CommandResult<T>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, String> + Send + 'static,
{
    let result = tauri::async_runtime::spawn_blocking(work)
        .await
        .map_err(|error| CommandError::new("TASK_FAILED", format!("{label} task failed: {error}")))?
        .map_err(classify_world_error)?;
    ensure_world_target(&target).map_err(classify_world_error)?;
    Ok(result)
}

/// Mutating calls hold the existing server/workspace transition lease for the
/// complete request. Workspace selection therefore cannot move from server A to
/// server B between resolving world-control config and committing a mutation.
async fn run_targeted_mutation<T, F>(label: &'static str, target: String, work: F) -> CommandResult<T>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, String> + Send + 'static,
{
    tauri::async_runtime::spawn_blocking(move || {
        let _lease = ServerStartLease::acquire()?;
        ensure_world_target(&target)?;
        work()
    })
    .await
    .map_err(|error| CommandError::new("TASK_FAILED", format!("{label} task failed: {error}")))?
    .map_err(classify_world_error)
}
