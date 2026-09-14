use crate::engine::plugin_manager::{PluginInstallResult, PluginManagerState, PluginSummary};
use crate::engine::server_manager::ServerManagerState;
use tauri::{AppHandle, Manager};

#[tauri::command]
pub async fn plugin_list(app: AppHandle) -> Result<Vec<PluginSummary>, String> {
    run_blocking("Plugin list", move || {
        let plugins = app.state::<PluginManagerState>();
        plugins.list_plugins()
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
pub async fn plugin_install(app: AppHandle, jar_path: String) -> Result<PluginInstallResult, String> {
    run_blocking("Plugin install", move || {
        let plugins = app.state::<PluginManagerState>();
        let server = app.state::<ServerManagerState>();
        ensure_plugin_mutation_allowed(&server)?;
        plugins.install(&jar_path)
    })
    .await
}

#[tauri::command]
pub async fn plugin_update(
    app: AppHandle,
    plugin_id: String,
    jar_path: String,
) -> Result<PluginInstallResult, String> {
    run_blocking("Plugin update", move || {
        let plugins = app.state::<PluginManagerState>();
        let server = app.state::<ServerManagerState>();
        ensure_plugin_mutation_allowed(&server)?;
        plugins.update(&plugin_id, &jar_path)
    })
    .await
}

#[tauri::command]
pub async fn plugin_set_enabled(
    app: AppHandle,
    plugin_id: String,
    enabled: bool,
) -> Result<(), String> {
    run_blocking("Plugin state change", move || {
        let plugins = app.state::<PluginManagerState>();
        let server = app.state::<ServerManagerState>();
        ensure_plugin_mutation_allowed(&server)?;
        plugins.set_enabled(&plugin_id, enabled)
    })
    .await
}

#[tauri::command]
pub async fn plugin_remove(app: AppHandle, plugin_id: String) -> Result<(), String> {
    run_blocking("Plugin removal", move || {
        let plugins = app.state::<PluginManagerState>();
        let server = app.state::<ServerManagerState>();
        ensure_plugin_mutation_allowed(&server)?;
        plugins.remove(&plugin_id)
    })
    .await
}

#[tauri::command]
pub async fn plugin_remove_problem(
    app: AppHandle,
    plugin_id: String,
    jar_file_name: String,
) -> Result<(), String> {
    run_blocking("Broken plugin cleanup", move || {
        let plugins = app.state::<PluginManagerState>();
        let server = app.state::<ServerManagerState>();
        ensure_plugin_mutation_allowed(&server)?;
        plugins.remove_problem(&plugin_id, &jar_file_name)
    })
    .await
}

#[tauri::command]
pub async fn plugin_resolve_duplicates(
    app: AppHandle,
    plugin_id: String,
    keep_jar_file_name: String,
) -> Result<PluginInstallResult, String> {
    run_blocking("Plugin duplicate resolution", move || {
        let plugins = app.state::<PluginManagerState>();
        let server = app.state::<ServerManagerState>();
        ensure_plugin_mutation_allowed(&server)?;
        plugins.resolve_duplicates(&plugin_id, &keep_jar_file_name)
    })
    .await
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

async fn run_blocking<T, F>(label: &'static str, work: F) -> Result<T, String>
where
    T: Send + 'static,
    F: FnOnce() -> Result<T, String> + Send + 'static,
{
    tauri::async_runtime::spawn_blocking(work)
        .await
        .map_err(|error| format!("{label} task failed: {error}"))?
}
