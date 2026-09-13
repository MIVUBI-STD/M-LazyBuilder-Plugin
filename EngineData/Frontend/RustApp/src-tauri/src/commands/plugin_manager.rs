use crate::engine::plugin_manager::{PluginInstallResult, PluginManagerState, PluginSummary};
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
    state: State<'_, PluginManagerState>,
    jar_path: String,
) -> Result<PluginInstallResult, String> {
    state.install(&jar_path)
}

#[tauri::command]
pub fn plugin_update(
    state: State<'_, PluginManagerState>,
    plugin_id: String,
    jar_path: String,
) -> Result<PluginInstallResult, String> {
    state.update(&plugin_id, &jar_path)
}

#[tauri::command]
pub fn plugin_set_enabled(
    state: State<'_, PluginManagerState>,
    plugin_id: String,
    enabled: bool,
) -> Result<(), String> {
    state.set_enabled(&plugin_id, enabled)
}

#[tauri::command]
pub fn plugin_remove(
    state: State<'_, PluginManagerState>,
    plugin_id: String,
) -> Result<(), String> {
    state.remove(&plugin_id)
}

#[tauri::command]
pub fn plugin_resolve_duplicates(
    state: State<'_, PluginManagerState>,
    plugin_id: String,
    keep_jar_file_name: String,
) -> Result<PluginInstallResult, String> {
    state.resolve_duplicates(&plugin_id, &keep_jar_file_name)
}
