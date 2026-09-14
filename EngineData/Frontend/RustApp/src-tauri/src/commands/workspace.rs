use crate::engine::server_manager::ServerManagerState;
use crate::engine::{adoption, provisioning, runtime_updates, server_process_guard, workspace_registry};
use crate::engine::workspace_registry::{ProvisioningStatus, WorkspaceEntry};
use std::path::PathBuf;
use tauri::{AppHandle, Manager, State};

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
pub fn workspace_provisioning_status() -> Result<ProvisioningStatus, String> {
    workspace_registry::provisioning_status()
}

#[tauri::command]
pub async fn workspace_provision(
    app: AppHandle,
    state: State<'_, ServerManagerState>,
) -> Result<provisioning::ProvisionResult, String> {
    ensure_runtime_update_allowed(&state)?;
    let resource_dir = app.path().resource_dir().ok();
    tauri::async_runtime::spawn_blocking(move || provisioning::provision_active(resource_dir.as_deref()))
        .await
        .map_err(|error| format!("Server provisioning task failed: {error}"))?
}

#[tauri::command]
pub fn workspace_runtime_update_status() -> Result<runtime_updates::RuntimeUpdateStatus, String> {
    runtime_updates::status()
}

#[tauri::command]
pub fn workspace_update_paper(
    state: State<'_, ServerManagerState>,
) -> Result<runtime_updates::RuntimeUpdateStatus, String> {
    ensure_runtime_update_allowed(&state)?;
    runtime_updates::update_paper()
}

#[tauri::command]
pub fn workspace_accept_eula() -> Result<ProvisioningStatus, String> {
    workspace_registry::accept_eula()?;
    workspace_registry::provisioning_status()
}

#[tauri::command]
pub fn workspace_pick_parent() -> Result<Option<String>, String> {
    Ok(rfd::FileDialog::new()
        .set_title("Choose where to create the LazyBuilder server")
        .pick_folder()
        .map(|path| path.display().to_string()))
}

#[tauri::command]
pub fn workspace_create(
    state: State<'_, ServerManagerState>,
    parent_path: String,
    name: String,
) -> Result<WorkspaceEntry, String> {
    ensure_switch_allowed(&state)?;
    workspace_registry::create(&PathBuf::from(parent_path), &name)
}

#[tauri::command]
pub fn workspace_adoption_pick(
    state: State<'_, ServerManagerState>,
) -> Result<Option<adoption::AdoptionPlan>, String> {
    ensure_switch_allowed(&state)?;
    let Some(path) = rfd::FileDialog::new()
        .set_title("Choose existing Paper server to adopt")
        .pick_folder() else {
        return Ok(None);
    };
    adoption::analyze(&path).map(Some)
}

#[tauri::command]
pub fn workspace_adopt(
    state: State<'_, ServerManagerState>,
    root_path: String,
    name: Option<String>,
) -> Result<WorkspaceEntry, String> {
    ensure_switch_allowed(&state)?;
    adoption::execute(&PathBuf::from(root_path), name.as_deref())
}

#[tauri::command]
pub fn workspace_activate(
    state: State<'_, ServerManagerState>,
    id: String,
) -> Result<WorkspaceEntry, String> {
    ensure_activation_allowed(&state, &id)?;
    workspace_registry::activate(&id)
}

#[tauri::command]
pub fn workspace_close(state: State<'_, ServerManagerState>) -> Result<(), String> {
    ensure_switch_allowed(&state)?;
    workspace_registry::deactivate()
}

fn ensure_activation_allowed(state: &ServerManagerState, target_id: &str) -> Result<(), String> {
    server_process_guard::ensure_no_running_paper_except(Some(target_id))?;
    if workspace_registry::current()?.is_none() {
        return Ok(());
    }
    ensure_runtime_update_allowed(state)
}

fn ensure_switch_allowed(state: &ServerManagerState) -> Result<(), String> {
    server_process_guard::ensure_no_running_paper_except(None)?;
    if workspace_registry::current()?.is_none() {
        return Ok(());
    }
    ensure_runtime_update_allowed(state)
}

fn ensure_runtime_update_allowed(state: &ServerManagerState) -> Result<(), String> {
    let snapshot = state.snapshot()?;
    match snapshot.state.as_str() {
        "Offline" | "Crashed" => Ok(()),
        other => Err(format!(
            "Stop the active server before changing workspace runtime files. Current server state: {other}."
        )),
    }
}
