use crate::commands::error::{CommandError, CommandResult};
use crate::engine::operations::{OperationError, OperationRegistry};
use crate::engine::plugin_ingress;
use crate::engine::plugin_manager::{PluginInstallResult, PluginManagerState, PluginSummary};
use crate::engine::server_manager::ServerManagerState;
use crate::engine::server_start_lock::ServerStartLease;
use crate::engine::workspace_registry;
use serde::Serialize;
use std::path::Path;
use tauri::{AppHandle, Manager};

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PluginListItem {
    pub id: String,
    pub display_name: String,
    pub version: String,
    pub category: String,
    pub state: String,
    pub problem_detail: Option<String>,
    pub candidate_files: Option<Vec<String>>,
    #[serde(rename = "managedByLazyBuilder")]
    pub managed_by_lazybuilder: bool,
    pub mutable: bool,
}

impl From<PluginSummary> for PluginListItem {
    fn from(plugin: PluginSummary) -> Self {
        let managed_by_lazybuilder = is_lazybuilder_managed(&plugin);
        Self {
            id: plugin.id,
            display_name: plugin.display_name,
            version: plugin.version,
            category: plugin.category,
            state: plugin.state,
            problem_detail: plugin.problem_detail,
            candidate_files: plugin.candidate_files,
            managed_by_lazybuilder,
            mutable: !managed_by_lazybuilder,
        }
    }
}

#[tauri::command]
pub async fn plugin_list(app: AppHandle) -> Result<Vec<PluginListItem>, String> {
    run_blocking("Plugin list", move || {
        let plugins = app.state::<PluginManagerState>();
        plugins.list_plugins().map(|items| items.into_iter().map(PluginListItem::from).collect())
    })
    .await
}

#[tauri::command]
pub fn plugin_pick_jar() -> Option<String> {
    rfd::FileDialog::new()
        .add_filter("Paper plugin", &["jar"])
        .pick_file()
        .map(|path| path.to_string_lossy().to_string())
}

#[tauri::command]
pub async fn plugin_install(app: AppHandle, jar_path: String) -> CommandResult<PluginInstallResult> {
    run_plugin_mutation(
        app,
        "install-plugin",
        "installing",
        "Installing plugin",
        "PLUGIN_INSTALL_FAILED",
        "Review Plugins and retry",
        move |plugins| {
            let ingress = plugin_ingress::stage_selected_jar(Path::new(&jar_path))?;
            let staged_path = ingress.path().to_string_lossy().into_owned();
            plugins.install(&staged_path)
        },
    )
    .await
}

#[tauri::command]
pub async fn plugin_update(
    app: AppHandle,
    plugin_id: String,
    jar_path: String,
) -> CommandResult<PluginInstallResult> {
    run_plugin_mutation(
        app,
        "update-plugin",
        "updating",
        "Updating plugin",
        "PLUGIN_UPDATE_FAILED",
        "Review Plugins and retry",
        move |plugins| {
            let ingress = plugin_ingress::stage_selected_jar(Path::new(&jar_path))?;
            let staged_path = ingress.path().to_string_lossy().into_owned();
            plugins.update(&plugin_id, &staged_path)
        },
    )
    .await
}

#[tauri::command]
pub async fn plugin_set_enabled(
    app: AppHandle,
    plugin_id: String,
    enabled: bool,
) -> CommandResult<()> {
    run_plugin_mutation(
        app,
        "change-plugin-state",
        "updating",
        "Changing plugin state",
        "PLUGIN_STATE_FAILED",
        "Review Plugins and retry",
        move |plugins| plugins.set_enabled(&plugin_id, enabled),
    )
    .await
}

#[tauri::command]
pub async fn plugin_remove(app: AppHandle, plugin_id: String) -> CommandResult<()> {
    run_plugin_mutation(
        app,
        "remove-plugin",
        "removing",
        "Removing plugin",
        "PLUGIN_REMOVE_FAILED",
        "Review Plugins and retry",
        move |plugins| plugins.remove(&plugin_id),
    )
    .await
}

#[tauri::command]
pub async fn plugin_remove_problem(
    app: AppHandle,
    plugin_id: String,
    jar_file_name: String,
) -> CommandResult<()> {
    run_plugin_mutation(
        app,
        "remove-problem-plugin",
        "removing",
        "Removing broken plugin file",
        "PLUGIN_REMOVE_FAILED",
        "Review Plugins and retry",
        move |plugins| plugins.remove_problem(&plugin_id, &jar_file_name),
    )
    .await
}

#[tauri::command]
pub async fn plugin_resolve_duplicates(
    app: AppHandle,
    plugin_id: String,
    keep_jar_file_name: String,
) -> CommandResult<PluginInstallResult> {
    run_plugin_mutation(
        app,
        "resolve-plugin-duplicates",
        "resolving",
        "Resolving duplicate plugin files",
        "PLUGIN_DUPLICATE_RESOLUTION_FAILED",
        "Review Plugins and retry",
        move |plugins| plugins.resolve_duplicates(&plugin_id, &keep_jar_file_name),
    )
    .await
}

fn is_lazybuilder_managed(plugin: &PluginSummary) -> bool {
    if matches!(plugin.id.as_str(), "world-manager" | "utilities-manager") {
        return true;
    }
    plugin.candidate_files.as_ref().is_some_and(|files| {
        files.iter().any(|file| {
            let value = file.to_ascii_lowercase();
            value.starts_with("world-manager-") || value.starts_with("utilities-manager-")
        })
    })
}

async fn run_plugin_mutation<T, F>(
    app: AppHandle,
    kind: &'static str,
    phase: &'static str,
    status: &'static str,
    failure_code: &'static str,
    failure_action: &'static str,
    work: F,
) -> CommandResult<T>
where
    T: Send + 'static,
    F: FnOnce(&PluginManagerState) -> Result<T, String> + Send + 'static,
{
    let active = workspace_registry::current()
        .map_err(CommandError::from)?
        .ok_or_else(|| CommandError::new("WORKSPACE_REQUIRED", "Open a server before changing plugins."))?;
    let resource = format!("workspace:{}", active.id);
    let operation = app
        .state::<OperationRegistry>()
        .begin_exclusive(kind, &resource, false)
        .map_err(|error| CommandError::recoverable("OPERATION_BUSY", error, "Open Activity"))?;
    let operation_id = operation.id.clone();
    let join_operation_id = operation.id.clone();
    let task_app = app.clone();

    let task = tauri::async_runtime::spawn_blocking(move || {
        let operations = task_app.state::<OperationRegistry>();
        let _start_lease = match ServerStartLease::acquire() {
            Ok(value) => value,
            Err(message) => {
                let error = CommandError::recoverable("SERVER_START_BUSY", message, "Wait for server start");
                fail_operation(&operations, &operation_id, &error);
                return Err(error);
            }
        };

        let server = task_app.state::<ServerManagerState>();
        let _ = operations.set_phase(
            &operation_id,
            "preflight",
            "Checking plugin mutation safety",
            "Confirming the server remains offline while plugin files are changed.",
            None,
        );
        if let Err(message) = ensure_plugin_mutation_allowed(&server) {
            let error = CommandError::recoverable("SERVER_BUSY", message, "Stop server");
            fail_operation(&operations, &operation_id, &error);
            return Err(error);
        }

        let _ = operations.set_phase(&operation_id, phase, status, "Plugin file changes are staged and validated by the canonical Plugin Manager owner.", None);
        let plugins = task_app.state::<PluginManagerState>();
        match work(plugins.inner()) {
            Ok(result) => {
                let _ = operations.succeed(&operation_id, status);
                Ok(result)
            }
            Err(message) => {
                let error = CommandError::recoverable(failure_code, message, failure_action);
                fail_operation(&operations, &operation_id, &error);
                Err(error)
            }
        }
    });

    match task.await {
        Ok(result) => result,
        Err(error) => {
            let _ = app.state::<OperationRegistry>().require_recovery(
                &join_operation_id,
                OperationError {
                    code: "TASK_FAILED".into(),
                    message: "Plugin mutation task ended unexpectedly".into(),
                    details: error.to_string(),
                    recoverable: true,
                },
            );
            Err(CommandError::new("TASK_FAILED", format!("Plugin mutation task failed: {error}")))
        }
    }
}

fn ensure_plugin_mutation_allowed(server: &ServerManagerState) -> Result<(), String> {
    let snapshot = server.snapshot()?;
    match snapshot.state.as_str() {
        "Offline" | "Crashed" => Ok(()),
        "Detached" => Err(
            "A LazyBuilder-managed Paper process is still running externally. Stop or recover it before changing plugin JARs."
                .into(),
        ),
        other => Err(format!(
            "Stop the server before changing plugins. Current server state: {other}."
        )),
    }
}

fn fail_operation(operations: &OperationRegistry, operation_id: &str, error: &CommandError) {
    let _ = operations.fail(
        operation_id,
        OperationError {
            code: error.code.to_string(),
            message: error.message.clone(),
            details: error.details.clone(),
            recoverable: error.recoverable,
        },
    );
}

async fn run_blocking<T, F>(label: &'static str, work: F) -> Result<T, String>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, String> + Send + 'static,
{
    tauri::async_runtime::spawn_blocking(work)
        .await
        .map_err(|error| format!("{label} task failed: {error}"))?
}
