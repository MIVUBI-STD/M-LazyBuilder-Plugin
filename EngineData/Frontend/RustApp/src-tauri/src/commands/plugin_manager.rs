use crate::engine::plugin_manager::{PluginInstallResult, PluginManagerState, PluginSummary};
use crate::engine::server_manager::ServerManagerState;
use tauri::State;

#[tauri::command]
pub fn plugin_list(state: State<'_, PluginManagerState>) -> Result<Vec<PluginSummary>, String> {
    state.list_plugins()
}

#[tauri::command]
pub fn plugin_pick_jar() -> Option<String> {
    rfd::FileDialog::new()
        .add_filter("Paper plugin", &["jar"])
        .pick_file()
        .map(|path| path.to_string_lossy().to_string())
}

#[tauri::command]
pub fn plugin_install(
    plugins: State<'_, PluginManagerState>,
    server: State<'_, ServerManagerState>,
    jar_path: String,
) -> Result<PluginInstallResult, String> {
    ensure_plugin_mutation_allowed(&server)?;
    plugins.install(&jar_path)
}

#[tauri::command]
pub fn plugin_update(
    plugins: State<'_, PluginManagerState>,
    server: State<'_, ServerManagerState>,
    plugin_id: String,
    jar_path: String,
) -> Result<PluginInstallResult, String> {
    ensure_plugin_mutation_allowed(&server)?;
    plugins.update(&plugin_id, &jar_path)
}

#[tauri::command]
pub fn plugin_set_enabled(
    plugins: State<'_, PluginManagerState>,
    server: State<'_, ServerManagerState>,
    plugin_id: String,
    enabled: bool,
) -> Result<(), String> {
    ensure_plugin_mutation_allowed(&server)?;
    plugins.set_enabled(&plugin_id, enabled)
}

#[tauri::command]
pub fn plugin_remove(
    plugins: State<'_, PluginManagerState>,
    server: State<'_, ServerManagerState>,
    plugin_id: String,
) -> Result<(), String> {
    ensure_plugin_mutation_allowed(&server)?;
    plugins.remove(&plugin_id)
}

#[tauri::command]
pub fn plugin_resolve_duplicates(
    plugins: State<'_, PluginManagerState>,
    server: State<'_, ServerManagerState>,
    plugin_id: String,
    keep_jar_file_name: String,
) -> Result<PluginInstallResult, String> {
    ensure_plugin_mutation_allowed(&server)?;
    plugins.resolve_duplicates(&plugin_id, &keep_jar_file_name)
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
