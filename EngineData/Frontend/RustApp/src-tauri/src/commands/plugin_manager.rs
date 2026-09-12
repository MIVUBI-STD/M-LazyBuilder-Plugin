use crate::engine::plugin_manager::{PluginInstallResult, PluginManagerState, PluginSummary};
use tauri::State;

#[tauri::command]
pub fn plugin_list(state: State<'_, PluginManagerState>) -> Result<Vec<PluginSummary>, String> {
    state.list_plugins()
}

#[tauri::command]
pub fn plugin_install(state: State<'_, PluginManagerState>, jar_path: String) -> Result<PluginInstallResult, String> {
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
    remove_data: bool,
) -> Result<(), String> {
    state.remove(&plugin_id, remove_data)
}

#[tauri::command]
pub fn plugin_resolve_duplicates(
    state: State<'_, PluginManagerState>,
    plugin_id: String,
    keep_jar_file_name: String,
) -> Result<PluginInstallResult, String> {
    state.resolve_duplicates(&plugin_id, &keep_jar_file_name)
}

#[tauri::command]
pub fn plugin_set_category(
    state: State<'_, PluginManagerState>,
    plugin_id: String,
    category: String,
) -> Result<(), String> {
    state.set_category(&plugin_id, &category)
}
