use crate::engine::server_manager::ServerManagerState;
use crate::engine::{java_runtime, provisioning, workspace_registry};
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
    resolved_provisioning_status()
}

#[tauri::command]
pub fn workspace_provision(app: AppHandle) -> Result<provisioning::ProvisionResult, String> {
    let resource_dir = app.path().resource_dir().ok();
    provisioning::provision_active(resource_dir.as_deref())
}

#[tauri::command]
pub fn workspace_accept_eula() -> Result<ProvisioningStatus, String> {
    workspace_registry::accept_eula()?;
    resolved_provisioning_status()
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
pub fn workspace_open_picker(state: State<'_, ServerManagerState>) -> Result<Option<WorkspaceEntry>, String> {
    ensure_switch_allowed(&state)?;
    let Some(path) = rfd::FileDialog::new()
        .set_title("Open LazyBuilder server workspace")
        .pick_folder() else {
        return Ok(None);
    };
    workspace_registry::open(&path).map(Some)
}

#[tauri::command]
pub fn workspace_activate(
    state: State<'_, ServerManagerState>,
    id: String,
) -> Result<WorkspaceEntry, String> {
    ensure_switch_allowed(&state)?;
    workspace_registry::activate(&id)
}

#[tauri::command]
pub fn workspace_close(state: State<'_, ServerManagerState>) -> Result<(), String> {
    ensure_switch_allowed(&state)?;
    workspace_registry::deactivate()
}

fn resolved_provisioning_status() -> Result<ProvisioningStatus, String> {
    let mut status = workspace_registry::provisioning_status()?;
    status.java_ready = java_runtime::managed_java_path()?.is_file();
    status.ready = status.workspace_created
        && status.java_ready
        && status.paper_ready
        && status.core_modules_ready
        && status.config_ready
        && status.eula_accepted;
    if !status.java_ready {
        status.next_step = "Provision managed Java 21".into();
    }
    Ok(status)
}

fn ensure_switch_allowed(state: &ServerManagerState) -> Result<(), String> {
    if workspace_registry::current()?.is_none() {
        return Ok(());
    }
    let snapshot = state.snapshot()?;
    match snapshot.state.as_str() {
        "Offline" | "Crashed" => Ok(()),
        other => Err(format!(
            "Stop the active server before switching workspaces. Current server state: {other}."
        )),
    }
}
