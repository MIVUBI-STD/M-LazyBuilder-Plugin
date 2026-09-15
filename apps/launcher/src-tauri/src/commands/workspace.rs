use crate::commands::error::{CommandError, CommandResult};
use crate::engine::server_manager::ServerManagerState;
use crate::engine::{adoption, provisioning, runtime_updates, server_process_guard, workspace_registry};
use crate::engine::workspace_registry::{ProvisioningStatus, WorkspaceDuplicateEstimate, WorkspaceEntry};
use std::path::{Path, PathBuf};
use std::process::Command;
use tauri::{AppHandle, Manager, State};

#[derive(Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WorkspaceState {
    pub active: Option<WorkspaceEntry>,
    pub recent: Vec<WorkspaceEntry>,
}

#[tauri::command]
pub fn workspace_state() -> CommandResult<WorkspaceState> {
    Ok(WorkspaceState { active: workspace_registry::current()?, recent: workspace_registry::list()? })
}

#[tauri::command]
pub async fn workspace_provisioning_status() -> CommandResult<ProvisioningStatus> {
    tauri::async_runtime::spawn_blocking(workspace_registry::provisioning_status)
        .await
        .map_err(|error| CommandError::new("TASK_FAILED", format!("Server readiness check failed: {error}")))?
        .map_err(CommandError::from)
}

#[tauri::command]
pub async fn workspace_provision(app: AppHandle) -> CommandResult<provisioning::ProvisionResult> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        ensure_runtime_update_allowed(&state)?;
        let resource_dir = app.path().resource_dir().ok();
        provisioning::provision_active(resource_dir.as_deref()).map_err(CommandError::from)
    })
    .await
    .map_err(|error| CommandError::new("TASK_FAILED", format!("Server provisioning task failed: {error}")))?
}

#[tauri::command]
pub async fn workspace_runtime_update_status() -> CommandResult<runtime_updates::RuntimeUpdateStatus> {
    tauri::async_runtime::spawn_blocking(runtime_updates::status)
        .await
        .map_err(|error| CommandError::new("TASK_FAILED", format!("Paper update check task failed: {error}")))?
        .map_err(CommandError::from)
}

#[tauri::command]
pub async fn workspace_update_paper(app: AppHandle) -> CommandResult<runtime_updates::RuntimeUpdateStatus> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        ensure_runtime_update_allowed(&state)?;
        runtime_updates::update_paper().map_err(CommandError::from)
    })
    .await
    .map_err(|error| CommandError::new("TASK_FAILED", format!("Paper update task failed: {error}")))?
}

#[tauri::command]
pub async fn workspace_accept_eula() -> CommandResult<ProvisioningStatus> {
    tauri::async_runtime::spawn_blocking(|| {
        workspace_registry::accept_eula().map_err(CommandError::from)?;
        workspace_registry::provisioning_status().map_err(CommandError::from)
    })
    .await
    .map_err(|error| CommandError::new("TASK_FAILED", format!("EULA acceptance task failed: {error}")))?
}

#[tauri::command]
pub fn workspace_pick_parent() -> CommandResult<Option<String>> {
    Ok(rfd::FileDialog::new()
        .set_title("Choose where to create the LazyBuilder server")
        .pick_folder()
        .map(|path| path.display().to_string()))
}

#[tauri::command]
pub fn workspace_create(state: State<'_, ServerManagerState>, parent_path: String, name: String) -> CommandResult<WorkspaceEntry> {
    ensure_switch_allowed(&state)?;
    workspace_registry::create(&PathBuf::from(parent_path), &name).map_err(CommandError::from)
}

#[tauri::command]
pub fn workspace_adoption_pick(state: State<'_, ServerManagerState>) -> CommandResult<Option<adoption::AdoptionPlan>> {
    ensure_switch_allowed(&state)?;
    let Some(path) = rfd::FileDialog::new().set_title("Choose existing Paper server to adopt").pick_folder() else { return Ok(None); };
    server_process_guard::ensure_root_not_running(&path).map_err(CommandError::from)?;
    adoption::analyze(&path).map(Some).map_err(CommandError::from)
}

#[tauri::command]
pub async fn workspace_adopt(app: AppHandle, root_path: String, name: Option<String>) -> CommandResult<WorkspaceEntry> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        ensure_switch_allowed(&state)?;
        let root = PathBuf::from(root_path);
        server_process_guard::ensure_root_not_running(&root).map_err(CommandError::from)?;
        adoption::execute(&root, name.as_deref()).map_err(CommandError::from)
    })
    .await
    .map_err(|error| CommandError::new("TASK_FAILED", format!("Server adoption task failed: {error}")))?
}

#[tauri::command]
pub fn workspace_activate(state: State<'_, ServerManagerState>, id: String) -> CommandResult<WorkspaceEntry> {
    ensure_activation_allowed(&state, &id)?;
    workspace_registry::activate(&id).map_err(CommandError::from)
}

#[tauri::command]
pub fn workspace_close(state: State<'_, ServerManagerState>) -> CommandResult<()> {
    ensure_switch_allowed(&state)?;
    workspace_registry::deactivate().map_err(CommandError::from)
}

#[tauri::command]
pub fn workspace_open_folder(id: String) -> CommandResult<()> {
    let entry = workspace_registry::get(&id).map_err(CommandError::from)?;
    let path = PathBuf::from(&entry.path);
    if !path.is_dir() {
        return Err(CommandError::new(
            "WORKSPACE_UNAVAILABLE",
            format!("Server location is currently unavailable: {}", path.display()),
        ));
    }
    Command::new("explorer.exe")
        .arg(&path)
        .spawn()
        .map_err(|error| CommandError::new("OPEN_FOLDER_FAILED", format!("Could not open server folder: {error}")))?;
    Ok(())
}

#[tauri::command]
pub fn workspace_duplicate_estimate(
    state: State<'_, ServerManagerState>,
    id: String,
    parent_path: String,
) -> CommandResult<WorkspaceDuplicateEstimate> {
    ensure_workspace_mutation_allowed(&state, &id)?;
    workspace_registry::duplicate_estimate(&id, Path::new(&parent_path)).map_err(CommandError::from)
}

#[tauri::command]
pub async fn workspace_duplicate(
    app: AppHandle,
    id: String,
    parent_path: String,
    name: String,
) -> CommandResult<WorkspaceEntry> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        ensure_workspace_mutation_allowed(&state, &id)?;
        workspace_registry::duplicate(&id, Path::new(&parent_path), &name).map_err(CommandError::from)
    })
    .await
    .map_err(|error| CommandError::new("TASK_FAILED", format!("Server duplication task failed: {error}")))?
}

#[tauri::command]
pub fn workspace_remove_from_library(state: State<'_, ServerManagerState>, id: String) -> CommandResult<()> {
    ensure_remove_allowed(&state, &id)?;
    workspace_registry::remove_from_library(&id).map_err(CommandError::from)
}

#[tauri::command]
pub async fn workspace_delete(
    app: AppHandle,
    id: String,
    typed_display_name: String,
) -> CommandResult<()> {
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<ServerManagerState>();
        ensure_workspace_mutation_allowed(&state, &id)?;
        workspace_registry::delete(&id, &typed_display_name).map_err(CommandError::from)
    })
    .await
    .map_err(|error| CommandError::new("TASK_FAILED", format!("Server deletion task failed: {error}")))?
}

fn ensure_activation_allowed(state: &ServerManagerState, target_id: &str) -> CommandResult<()> {
    server_process_guard::ensure_no_running_paper_except(Some(target_id)).map_err(CommandError::from)?;
    if workspace_registry::current().map_err(CommandError::from)?.is_none() { return Ok(()); }
    ensure_runtime_update_allowed(state)
}

fn ensure_switch_allowed(state: &ServerManagerState) -> CommandResult<()> {
    server_process_guard::ensure_no_running_paper_except(None).map_err(CommandError::from)?;
    if workspace_registry::current().map_err(CommandError::from)?.is_none() { return Ok(()); }
    ensure_runtime_update_allowed(state)
}

fn ensure_workspace_mutation_allowed(state: &ServerManagerState, target_id: &str) -> CommandResult<()> {
    let entry = workspace_registry::get(target_id).map_err(CommandError::from)?;
    if workspace_registry::current().map_err(CommandError::from)?.is_some_and(|active| active.id == target_id) {
        return ensure_runtime_update_allowed(state);
    }
    let root = PathBuf::from(entry.path);
    if !root.is_dir() {
        return Err(CommandError::new(
            "WORKSPACE_UNAVAILABLE",
            format!("Server location is currently unavailable: {}", root.display()),
        ));
    }
    server_process_guard::ensure_root_not_running(&root).map_err(CommandError::from)
}

fn ensure_remove_allowed(state: &ServerManagerState, target_id: &str) -> CommandResult<()> {
    let entry = workspace_registry::get(target_id).map_err(CommandError::from)?;
    if workspace_registry::current().map_err(CommandError::from)?.is_some_and(|active| active.id == target_id) {
        return ensure_runtime_update_allowed(state);
    }
    let root = PathBuf::from(entry.path);
    if root.is_dir() {
        server_process_guard::ensure_root_not_running(&root).map_err(CommandError::from)?;
    }
    Ok(())
}

fn ensure_runtime_update_allowed(state: &ServerManagerState) -> CommandResult<()> {
    let snapshot = state.snapshot().map_err(CommandError::from)?;
    match snapshot.state.as_str() {
        "Offline" | "Crashed" => Ok(()),
        other => Err(CommandError::new(
            "SERVER_BUSY",
            format!("Stop the active server before changing workspace runtime files. Current server state: {other}."),
        )),
    }
}
