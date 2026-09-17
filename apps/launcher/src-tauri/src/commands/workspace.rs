use crate::commands::error::{CommandError, CommandResult, RecoveryAction};
use crate::engine::operations::{OperationError, OperationProgress, OperationRegistry};
use crate::engine::server_runtime_registry::ServerRuntimeRegistry;
use crate::engine::{adoption, provisioning, runtime_updates, server_process_guard, server_start_lock::ServerStartLease, workspace_registry};
use crate::engine::workspace_registry::{ProvisioningStatus, WorkspaceDuplicateEstimate, WorkspaceEntry};
use std::path::{Path, PathBuf};
use std::process::Command;
use tauri::{AppHandle, Manager, State};

const WORKSPACE_LIBRARY_RESOURCE: &str = "workspace-library";

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
    let active = workspace_registry::current().map_err(CommandError::from)?.ok_or_else(|| CommandError::new("WORKSPACE_REQUIRED", "Open a server before preparing it."))?;
    let resource = format!("workspace:{}", active.id);
    let operation = app.state::<OperationRegistry>().begin_exclusive("provision-server", &resource, false).map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();
    let active_id = active.id.clone();
    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let runtimes = task_app.state::<ServerRuntimeRegistry>();
        let _ = operations.set_phase(&operation_id, "preflight", "Checking server state", "Confirming that runtime files can be changed safely.", None);
        if let Err(error) = ensure_runtime_update_allowed(&runtimes, &active_id) { fail_operation(&operations, &operation_id, &error, true); return Err(error); }
        let resource_dir = task_app.path().resource_dir().ok();
        let result = provisioning::provision_active_tracked(resource_dir.as_deref(), |phase, status, details| { let _ = operations.set_phase(&operation_id, phase, status, details, None); });
        match result {
            Ok(result) => { let _ = operations.succeed(&operation_id, "Server prepared"); Ok(result) }
            Err(message) => { let error = CommandError::recoverable_action("PROVISION_FAILED", message, RecoveryAction::RetryOperation); fail_operation(&operations, &operation_id, &error, true); Err(error) }
        }
    });
    match task.await {
        Ok(result) => result,
        Err(error) => {
            let _ = app.state::<OperationRegistry>().require_recovery(&join_operation_id, OperationError { code: "TASK_FAILED".into(), message: "Server preparation task ended unexpectedly".into(), details: error.to_string(), recoverable: true });
            Err(CommandError::new("TASK_FAILED", format!("Server provisioning task failed: {error}")))
        }
    }
}

#[tauri::command]
pub async fn workspace_runtime_update_status() -> CommandResult<runtime_updates::RuntimeUpdateStatus> {
    tauri::async_runtime::spawn_blocking(runtime_updates::status).await.map_err(|error| CommandError::new("TASK_FAILED", format!("Paper update check task failed: {error}")))?.map_err(CommandError::from)
}

#[tauri::command]
pub async fn workspace_update_paper(app: AppHandle) -> CommandResult<runtime_updates::RuntimeUpdateStatus> {
    let active = workspace_registry::current().map_err(CommandError::from)?.ok_or_else(|| CommandError::new("WORKSPACE_REQUIRED", "Open a server before updating Paper."))?;
    let resource = format!("workspace:{}", active.id);
    let operation = app.state::<OperationRegistry>().begin_exclusive("update-paper", &resource, false).map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();
    let active_id = active.id.clone();
    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let runtimes = task_app.state::<ServerRuntimeRegistry>();
        let _ = operations.set_phase(&operation_id, "preflight", "Checking server state", "Confirming that Paper can be replaced safely while the server is offline.", None);
        if let Err(error) = ensure_runtime_update_allowed(&runtimes, &active_id) { fail_operation(&operations, &operation_id, &error, true); return Err(error); }
        let result = runtime_updates::update_paper_tracked(|phase, status, details| { let _ = operations.set_phase(&operation_id, phase, status, details, None); });
        match result {
            Ok(result) => { let _ = operations.succeed(&operation_id, "Paper updated"); Ok(result) }
            Err(message) => {
                let rollback_failed = message.contains("rollback also failed");
                let error = if rollback_failed {
                    CommandError::new("PAPER_UPDATE_FAILED", message.clone()).with_details("Paper rollback could not be completed automatically. Inspect the server before retrying.")
                } else {
                    CommandError::recoverable_action("PAPER_UPDATE_FAILED", message.clone(), RecoveryAction::RetryOperation)
                };
                let operation_error = OperationError { code: error.code.to_string(), message: error.message.clone(), details: error.details.clone(), recoverable: !rollback_failed };
                if rollback_failed { let _ = operations.require_recovery(&operation_id, operation_error); } else { let _ = operations.fail(&operation_id, operation_error); }
                Err(error)
            }
        }
    });
    match task.await {
        Ok(result) => result,
        Err(error) => {
            let _ = app.state::<OperationRegistry>().require_recovery(&join_operation_id, OperationError { code: "TASK_FAILED".into(), message: "Paper update task ended unexpectedly".into(), details: error.to_string(), recoverable: true });
            Err(CommandError::new("TASK_FAILED", format!("Server Paper update task failed: {error}")))
        }
    }
}

#[tauri::command]
pub async fn workspace_accept_eula(operations: State<'_, OperationRegistry>) -> CommandResult<ProvisioningStatus> {
    ensure_current_workspace_operation_idle(&operations)?;
    tauri::async_runtime::spawn_blocking(|| { workspace_registry::accept_eula().map_err(CommandError::from)?; workspace_registry::provisioning_status().map_err(CommandError::from) }).await.map_err(|error| CommandError::new("TASK_FAILED", format!("EULA acceptance task failed: {error}")))?
}

#[tauri::command]
pub fn workspace_pick_parent() -> CommandResult<Option<String>> {
    Ok(rfd::FileDialog::new().set_title("Choose where to create the LazyBuilder server").pick_folder().map(|path| path.display().to_string()))
}

#[tauri::command]
pub fn workspace_adoption_pick(operations: State<'_, OperationRegistry>) -> CommandResult<Option<adoption::AdoptionPlan>> {
    ensure_switch_allowed(&operations)?;
    let Some(path) = rfd::FileDialog::new().set_title("Choose existing Paper server to adopt").pick_folder() else { return Ok(None); };
    server_process_guard::ensure_root_not_running(&path).map_err(CommandError::from)?;
    adoption::analyze(&path).map(Some).map_err(CommandError::from)
}

#[tauri::command]
pub async fn workspace_adopt(app: AppHandle, root_path: String, name: Option<String>) -> CommandResult<WorkspaceEntry> {
    let _selection_lease = acquire_workspace_selection_lease()?;
    {
        let operations = app.state::<OperationRegistry>();
        ensure_switch_allowed(&operations)?;
    }
    let operation = app.state::<OperationRegistry>().begin_exclusive("adopt-server", WORKSPACE_LIBRARY_RESOURCE, false).map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();
    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let root = PathBuf::from(root_path);
        let _ = operations.set_phase(&operation_id, "preflight", "Validating existing Paper server", "Checking process ownership, filesystem safety, and migration destinations before moving server files.", None);
        if let Err(message) = server_process_guard::ensure_root_not_running(&root) {
            let error = CommandError::recoverable_action("SERVER_BUSY", message, RecoveryAction::StopServer);
            fail_operation(&operations, &operation_id, &error, true);
            return Err(error);
        }
        let _ = operations.set_phase(&operation_id, "migrating", "Adopting existing server", "Moving recognized Paper runtime files and worlds under a durable rollback intent.", None);
        match adoption::execute(&root, name.as_deref()) {
            Ok(entry) => { let _ = operations.succeed(&operation_id, "Existing server adopted"); Ok(entry) }
            Err(message) if message.starts_with("ADOPTION_RECOVERY_REQUIRED:") => {
                let message = message.trim_start_matches("ADOPTION_RECOVERY_REQUIRED:").trim().to_string();
                let error = CommandError::recoverable_action("ADOPTION_RECOVERY_REQUIRED", message, RecoveryAction::RestartLauncher);
                let _ = operations.require_recovery(&operation_id, OperationError { code: error.code.to_string(), message: error.message.clone(), details: error.details.clone(), recoverable: true });
                Err(error)
            }
            Err(message) => {
                let error = CommandError::recoverable_action("ADOPTION_FAILED", message, RecoveryAction::RetryOperation);
                fail_operation(&operations, &operation_id, &error, true);
                Err(error)
            }
        }
    });
    match task.await {
        Ok(result) => result,
        Err(error) => {
            let _ = app.state::<OperationRegistry>().require_recovery(&join_operation_id, OperationError { code: "TASK_FAILED".into(), message: "Server adoption task ended unexpectedly".into(), details: error.to_string(), recoverable: true });
            Err(CommandError::new("TASK_FAILED", format!("Server adoption task failed: {error}")))
        }
    }
}

#[tauri::command]
pub fn workspace_activate(operations: State<'_, OperationRegistry>, id: String) -> CommandResult<WorkspaceEntry> {
    let _selection_lease = acquire_workspace_selection_lease()?;
    ensure_activation_allowed(&operations, &id)?;
    workspace_registry::activate(&id).map_err(|message| {
        if message.contains("currently unavailable") {
            CommandError::recoverable_action("WORKSPACE_UNAVAILABLE", message, RecoveryAction::LocateWorkspace)
        } else {
            CommandError::from(message)
        }
    })
}

#[tauri::command]
pub fn workspace_close(operations: State<'_, OperationRegistry>) -> CommandResult<()> {
    let _selection_lease = acquire_workspace_selection_lease()?;
    ensure_switch_allowed(&operations)?;
    workspace_registry::deactivate().map_err(CommandError::from)
}

#[tauri::command]
pub fn workspace_open_folder(id: String) -> CommandResult<()> {
    let entry = workspace_registry::get(&id).map_err(CommandError::from)?;
    let path = PathBuf::from(&entry.path);
    if !path.is_dir() {
        return Err(CommandError::recoverable_action(
            "WORKSPACE_UNAVAILABLE",
            format!("Server location is currently unavailable: {}", path.display()),
            RecoveryAction::LocateWorkspace,
        ));
    }
    Command::new("explorer.exe").arg(&path).spawn().map_err(|error| CommandError::new("OPEN_FOLDER_FAILED", format!("Could not open server folder: {error}")))?;
    Ok(())
}

#[tauri::command]
pub fn workspace_duplicate_estimate(runtimes: State<'_, ServerRuntimeRegistry>, operations: State<'_, OperationRegistry>, id: String, parent_path: String) -> CommandResult<WorkspaceDuplicateEstimate> {
    ensure_library_operation_idle(&operations)?;
    ensure_workspace_mutation_allowed(&runtimes, &id)?;
    workspace_registry::duplicate_estimate(&id, Path::new(&parent_path)).map_err(CommandError::from)
}

#[tauri::command]
pub async fn workspace_duplicate(app: AppHandle, id: String, parent_path: String, name: String) -> CommandResult<WorkspaceEntry> {
    {
        let operations = app.state::<OperationRegistry>();
        ensure_library_operation_idle(&operations)?;
    }
    let resource = format!("workspace:{id}");
    let operation = app.state::<OperationRegistry>().begin_exclusive("duplicate-server", &resource, false).map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();
    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let runtimes = task_app.state::<ServerRuntimeRegistry>();
        operations.set_phase(&operation_id, "validating", "Validating source server", "", None).map_err(CommandError::from)?;
        if let Err(error) = ensure_workspace_mutation_allowed(&runtimes, &id) { fail_operation(&operations, &operation_id, &error, true); return Err(error); }
        operations.set_phase(&operation_id, "preflight", "Checking storage", "", None).map_err(CommandError::from)?;
        let estimate = match workspace_registry::duplicate_estimate(&id, Path::new(&parent_path)) {
            Ok(estimate) => estimate,
            Err(message) => { let error = CommandError::new("DUPLICATE_PREFLIGHT_FAILED", message); fail_operation(&operations, &operation_id, &error, true); return Err(error); }
        };
        operations.set_phase(&operation_id, "copying", "Copying server files", "The duplicate is staged and published only after the copy and workspace identity are validated.", Some(OperationProgress { current: 0, total: Some(estimate.source_bytes), unit: "bytes".into() })).map_err(CommandError::from)?;
        match workspace_registry::duplicate(&id, Path::new(&parent_path), &name) {
            Ok(entry) => {
                let _ = operations.set_phase(&operation_id, "publishing", "Publishing duplicated server", "", Some(OperationProgress { current: estimate.source_bytes, total: Some(estimate.source_bytes), unit: "bytes".into() }));
                let _ = operations.succeed(&operation_id, "Server duplicated");
                Ok(entry)
            }
            Err(message) if message.starts_with("DUPLICATE_RECOVERY_REQUIRED:") => {
                let message = message.trim_start_matches("DUPLICATE_RECOVERY_REQUIRED:").trim().to_string();
                let error = CommandError::recoverable_action("DUPLICATE_RECOVERY_REQUIRED", message, RecoveryAction::RestartLauncher);
                let _ = operations.require_recovery(&operation_id, OperationError { code: error.code.to_string(), message: error.message.clone(), details: error.details.clone(), recoverable: true });
                Err(error)
            }
            Err(message) => { let error = CommandError::recoverable_action("DUPLICATE_FAILED", message, RecoveryAction::RetryOperation); fail_operation(&operations, &operation_id, &error, true); Err(error) }
        }
    });
    match task.await {
        Ok(result) => result,
        Err(error) => {
            let _ = app.state::<OperationRegistry>().require_recovery(&join_operation_id, OperationError { code: "TASK_FAILED".into(), message: "Server duplication task ended unexpectedly".into(), details: error.to_string(), recoverable: true });
            Err(CommandError::new("TASK_FAILED", format!("Server duplication task failed: {error}")))
        }
    }
}

#[tauri::command]
pub fn workspace_remove_from_library(runtimes: State<'_, ServerRuntimeRegistry>, operations: State<'_, OperationRegistry>, id: String) -> CommandResult<()> {
    ensure_library_operation_idle(&operations)?;
    ensure_workspace_operation_idle(&operations, &id)?;
    ensure_remove_allowed(&runtimes, &id)?;
    runtimes.remove(&id).map_err(CommandError::from)?;
    workspace_registry::remove_from_library(&id).map_err(CommandError::from)
}

#[tauri::command]
pub async fn workspace_delete(app: AppHandle, id: String, typed_display_name: String) -> CommandResult<()> {
    let resource = format!("workspace:{id}");
    let operation = app.state::<OperationRegistry>().begin_exclusive("delete-server", &resource, false).map_err(|error| CommandError::new("OPERATION_BUSY", error))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();
    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let runtimes = task_app.state::<ServerRuntimeRegistry>();
        let _ = operations.set_phase(&operation_id, "preflight", "Validating server deletion", "Confirming the server is offline and its workspace can be deleted safely.", None);
        if let Err(error) = ensure_workspace_mutation_allowed(&runtimes, &id) {
            fail_operation(&operations, &operation_id, &error, true);
            return Err(error);
        }

        let _ = operations.set_phase(&operation_id, "deleting", "Deleting server", "The server is staged under a durable deletion intent before it is removed from the library and filesystem.", None);
        match workspace_registry::delete(&id, &typed_display_name) {
            Ok(()) => {
                let _ = runtimes.remove(&id);
                let _ = operations.succeed(&operation_id, "Server deleted");
                Ok(())
            }
            Err(message) if message.contains("Rollback also failed") || message.contains("final deletion cleanup is pending") => {
                let error = CommandError::recoverable_action("DELETE_RECOVERY_REQUIRED", message, RecoveryAction::RestartLauncher);
                let _ = operations.require_recovery(&operation_id, OperationError { code: error.code.to_string(), message: error.message.clone(), details: error.details.clone(), recoverable: true });
                Err(error)
            }
            Err(message) => {
                let error = CommandError::recoverable_action("DELETE_FAILED", message, RecoveryAction::RetryOperation);
                fail_operation(&operations, &operation_id, &error, true);
                Err(error)
            }
        }
    });

    match task.await {
        Ok(result) => result,
        Err(error) => {
            let _ = app.state::<OperationRegistry>().require_recovery(&join_operation_id, OperationError { code: "TASK_FAILED".into(), message: "Server deletion task ended unexpectedly".into(), details: error.to_string(), recoverable: true });
            Err(CommandError::new("TASK_FAILED", format!("Server deletion task failed: {error}")))
        }
    }
}

fn fail_operation(operations: &OperationRegistry, operation_id: &str, error: &CommandError, recoverable: bool) {
    let _ = operations.fail(operation_id, OperationError { code: error.code.to_string(), message: error.message.clone(), details: error.details.clone(), recoverable });
}

fn acquire_workspace_selection_lease() -> CommandResult<ServerStartLease> {
    ServerStartLease::acquire().map_err(|message| CommandError::recoverable_action(
        "SERVER_START_BUSY",
        message,
        RecoveryAction::WaitForServerStart,
    ))
}

fn ensure_library_operation_idle(operations: &OperationRegistry) -> CommandResult<()> {
    if operations.has_active_for_resource(WORKSPACE_LIBRARY_RESOURCE).map_err(CommandError::from)? {
        return Err(CommandError::recoverable_action(
            "OPERATION_BUSY",
            "A server-library migration is still running. Wait for it to finish before changing the server library.",
            RecoveryAction::OpenActivity,
        ));
    }
    Ok(())
}

fn ensure_workspace_operation_idle(operations: &OperationRegistry, workspace_id: &str) -> CommandResult<()> {
    let resource = format!("workspace:{workspace_id}");
    if operations.has_active_for_resource(&resource).map_err(CommandError::from)? {
        return Err(CommandError::recoverable_action(
            "OPERATION_BUSY",
            "A Launcher operation is still changing this server. Wait for it to finish before switching or modifying the workspace.",
            RecoveryAction::OpenActivity,
        ));
    }
    Ok(())
}

fn ensure_current_workspace_operation_idle(operations: &OperationRegistry) -> CommandResult<()> {
    if let Some(active) = workspace_registry::current().map_err(CommandError::from)? { ensure_workspace_operation_idle(operations, &active.id)?; }
    Ok(())
}

fn ensure_activation_allowed(operations: &OperationRegistry, target_id: &str) -> CommandResult<()> {
    ensure_library_operation_idle(operations)?;
    ensure_current_workspace_operation_idle(operations)?;
    ensure_workspace_operation_idle(operations, target_id)
}

fn ensure_switch_allowed(operations: &OperationRegistry) -> CommandResult<()> {
    ensure_library_operation_idle(operations)?;
    ensure_current_workspace_operation_idle(operations)
}

fn ensure_workspace_mutation_allowed(runtimes: &ServerRuntimeRegistry, target_id: &str) -> CommandResult<()> {
    let entry = workspace_registry::get(target_id).map_err(CommandError::from)?;
    let root = PathBuf::from(&entry.path);
    if !root.is_dir() {
        return Err(CommandError::recoverable_action(
            "WORKSPACE_UNAVAILABLE",
            format!("Server location is currently unavailable: {}", root.display()),
            RecoveryAction::LocateWorkspace,
        ));
    }
    ensure_runtime_update_allowed(runtimes, target_id)?;
    server_process_guard::ensure_root_not_running(&root).map_err(CommandError::from)
}

fn ensure_remove_allowed(runtimes: &ServerRuntimeRegistry, target_id: &str) -> CommandResult<()> {
    let entry = workspace_registry::get(target_id).map_err(CommandError::from)?;
    let root = PathBuf::from(&entry.path);
    ensure_runtime_update_allowed(runtimes, target_id)?;
    if root.is_dir() { server_process_guard::ensure_root_not_running(&root).map_err(CommandError::from)?; }
    Ok(())
}

fn ensure_runtime_update_allowed(runtimes: &ServerRuntimeRegistry, workspace_id: &str) -> CommandResult<()> {
    if let Some(state) = runtimes.runtime_if_present(workspace_id).map_err(CommandError::from)? {
        let snapshot = state.snapshot().map_err(CommandError::from)?;
        return match snapshot.state.as_str() {
            "Offline" | "Crashed" => Ok(()),
            other => Err(CommandError::recoverable_action(
                "SERVER_BUSY",
                format!("Stop this server before changing its runtime files. Current server state: {other}."),
                RecoveryAction::StopServer,
            )),
        };
    }
    if server_process_guard::workspace_has_running_paper(workspace_id).map_err(CommandError::from)? {
        return Err(CommandError::recoverable_action(
            "SERVER_BUSY",
            "This server still has a verified Paper process running. Stop or recover it before changing runtime files.",
            RecoveryAction::StopServer,
        ));
    }
    Ok(())
}
